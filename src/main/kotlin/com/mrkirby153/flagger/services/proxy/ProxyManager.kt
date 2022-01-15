package com.mrkirby153.flagger.services.proxy

import com.mrkirby153.flagger.nameAndDiscrim
import com.mrkirby153.flagger.services.config.ConfigurationService
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.sharding.ShardManager
import org.apache.logging.log4j.LogManager
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.awt.Color
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Service
class ProxyManager(
    private val configurationService: ConfigurationService,
    private val shardManager: ShardManager
) : ProxyService {

    private val log = LogManager.getLogger()

    private val lastPings = mutableMapOf<String, Long>()
    private val pendingProxyMessages = mutableMapOf<String, ScheduledFuture<*>>()
    private val proxyMessages = mutableMapOf<String, String>()

    override fun lastModRolePing(guild: Guild) = lastPings[guild.id] ?: -1

    override fun resetLastModRolePing(guild: Guild) {
        lastPings.remove(guild.id)
    }

    override fun isEligibleForPing(guild: Guild): Boolean {
        val minTime = configurationService.getConfiguration(guild).minTimeBetweenPings
        return lastModRolePing(guild) + minTime < System.currentTimeMillis()
    }

    /**
     * Sends a (real) mod role ping to the configured channel
     */
    fun sendModRolePing(message: Message) {
        if (configurationService.validateConfiguration(message.guild).map { it.value }
                .any { !it }) {
            log.debug("Skipping sending mod role ping due to invalid configuration")
            return
        }
        val settings = configurationService.getConfiguration(message.guild)
        val targetChannel =
            if (settings.pingModsInCurrentChannel && settings.modPingChannel != null) message.channel as TextChannel else message.guild.getTextChannelById(
                settings.modPingChannel!!
            ) ?: return
        if (!message.guild.selfMember.hasPermission(targetChannel, Permission.MESSAGE_SEND)) {
            log.warn("Attempted to send a mod ping in $targetChannel without permission")
            return
        }

        val modRole = message.guild.getRoleById(settings.modRole!!)
        val messageText = buildString {
            append(modRole?.asMention ?: "<<Mod Role Not Found>>")
            append(" ${message.author.nameAndDiscrim}: ${message.contentRaw}")
        }
        val pingMessage = MessageBuilder(messageText).apply {
            setAllowedMentions(listOf(Message.MentionType.ROLE))
            mention(modRole)
        }.build()

        lastPings[message.guild.id] = System.currentTimeMillis()

        targetChannel.sendMessage(pingMessage).queue {
            log.debug("Sent mod ping $it")
        }
    }

    /**
     * Sends a proxy message in response to a message
     */
    fun sendProxyMessage(message: Message) {
        val settings = configurationService.getConfiguration(message.guild)
        val embed = EmbedBuilder().apply {
            setColor(Color.BLUE)
            setDescription(settings.confirmationMessage)
        }.build()


        val idFormat = "proxy:${message.author.id}:${message.channel.id}:${message.id}:%s"
        val actionRow = ActionRow.of(
            Button.success(idFormat.format("confirm"), "Yes"),
            Button.danger(idFormat.format("deny"), "No")
        )
        val proxyConfirmMessage = MessageBuilder().apply {
            setEmbeds(embed)
            setActionRows(actionRow)
        }.build()
        message.reply(proxyConfirmMessage).queue {
            log.debug("Sent proxy message $it because of $message")
            proxyMessages[message.id] = "${it.channel.id}-${it.id}"
            pendingProxyMessages[it.id] =
                it.editMessage(MessageBuilder().apply {
                    setEmbeds(EmbedBuilder().apply {
                        setColor(Color.BLUE)
                        setDescription(settings.confirmationMessage)
                    }.build())
                    setActionRows(
                        ActionRow.of(
                            Button.success(idFormat.format("confirm"), "Yes").asDisabled(),
                            Button.danger(idFormat.format("deny"), "No").asDisabled()
                        )
                    )
                }.build())
                    .queueAfter(settings.confirmTimeout, TimeUnit.MILLISECONDS) {
                        log.debug("Timed out waiting for confirmation to $message")
                    }
        }
    }

    override fun handleMessage(message: Message) {
        log.debug("Processing message ${message.contentRaw} sent by ${message.author.nameAndDiscrim}")
        val settings = configurationService.getConfiguration(message.guild)
        if (!settings.enabled) {
            log.debug("Stopping processing, proxy is disabled")
            return
        }
        if (settings.proxyModRole !in message.mentionedRoles.map { it.id }) {
            log.debug("Stopping processing, no proxy mod role ping")
            return
        }
        if (isEligibleForPing(message.guild)) {
            log.info("Sending proxy confirm message in response to ${message.id} by ${message.author.nameAndDiscrim}")
            sendProxyMessage(message)
        } else {
            log.info("Sending too frequent proxy ping message in response to ${message.id} by ${message.author.nameAndDiscrim}")
            message.reply(settings.tooFrequentPingMessage).queue()
        }
    }

    @EventListener
    fun onButtonClick(event: ButtonClickEvent) {
        val parts = event.componentId.split(':')
        log.debug("Handling button click with component id $parts")
        if (parts.size != 5) {
            log.debug("Component id is not in required format")
            return
        }
        val type = parts[0]
        val userId = parts[1]
        val channelId = parts[2]
        val messageId = parts[3]
        val action = parts[4]
        if (type != "proxy") {
            log.debug("Type is not correct")
            return
        }
        if (userId != event.user.id) {
            log.debug("User is not correct")
            return
        }
        pendingProxyMessages.remove(event.messageId)?.cancel(false) // Cancel the timeout task
        shardManager.retrieveUserById(userId).queue {
            event.guild?.getTextChannelById(channelId)?.retrieveMessageById(messageId)
                ?.queue { msg ->
                    when (action) {
                        "confirm" -> {
                            sendModRolePing(msg)
                        }
                        "deny" -> {
                            // Do nothing
                        }
                    }
                    // Disable the buttons in the message
                    proxyMessages.remove(msg.id)?.run {
                        val p = this.split(':')
                        val chanId = p[0]
                        val msgId = p[1]
                        event.guild?.getTextChannelById(chanId)?.retrieveMessageById(msgId)
                            ?.queue { m ->
                                val mb = MessageBuilder(m)
                                val newRows = mutableListOf<ActionRow>()
                                m.actionRows.forEach { row ->
                                    newRows.add(ActionRow.of(row.buttons.map { it.asDisabled() }))
                                }
                                mb.setActionRows(newRows)
                                m.editMessage(mb.build()).queue()
                            }
                    }
                }
        }
    }

    @EventListener
    fun onMessage(event: MessageReceivedEvent) {
        handleMessage(event.message)
    }
}