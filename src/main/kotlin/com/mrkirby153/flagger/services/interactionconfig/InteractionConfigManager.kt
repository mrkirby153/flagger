package com.mrkirby153.flagger.services.interactionconfig

import com.mrkirby153.flagger.getField
import com.mrkirby153.flagger.services.config.ConfigurationService
import com.mrkirby153.flagger.setField
import com.mrkirby153.interactionmenus.Menu
import com.mrkirby153.interactionmenus.MenuManager
import com.mrkirby153.interactionmenus.builders.PageBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu
import org.apache.logging.log4j.LogManager
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ScheduledFuture


const val GREEN_CHECK = "✅"
const val RED_X = "❌"

@Service
class InteractionConfigManager(
    private val configService: ConfigurationService,
    private val menuManager: MenuManager,
    private val scheduler: TaskScheduler
) : InteractionConfigService {

    private val log = LogManager.getLogger()

    private val pendingMessages =
        mutableMapOf<Pair<String, String>, (MessageReceivedEvent) -> Unit>()
    private val pendingMessageFutures = mutableMapOf<Pair<String, String>, ScheduledFuture<*>>()

    private fun makeMenu(page: InteractionConfigPage): ActionRow {
        return ActionRow.of(SelectionMenu.create("menu").apply {
            addOptions(
                InteractionConfigPage.values().map {
                    SelectOption.of(it.displayName, it.name).withDescription(it.description)
                        .withDefault(it == page)
                }
            )
        }.build())
    }

    private fun getOptionsForRoles(guild: Guild, default: String? = null) =
        guild.roles.filter { it.name != "@everyone" }.map { role ->
            val o = SelectOption.of("@${role.name} [${role.id}]", role.id)
            if (default != null) {
                o.withDefault(role.id == default)
            } else {
                o
            }
        }

    private fun buildSelectPage(
        pageBuilder: PageBuilder,
        menu: Menu<InteractionConfigPage>,
        currentPage: InteractionConfigPage
    ) {
        pageBuilder.actionRow {
            select {
                min = 1
                max = 1
                InteractionConfigPage.values().forEach { page ->
                    option {
                        onSelect {
                            menu.setPage(page)
                        }
                        value = page.displayName
                        default = page == currentPage
                    }
                }
            }
        }
    }

    override fun getMenu(user: User, guild: Guild): Message {
        val settingsMenu = Menu(InteractionConfigPage.OVERVIEW)
        settingsMenu.page(InteractionConfigPage.OVERVIEW) { menu ->
            val settings = configService.getConfiguration(guild)
            buildSelectPage(this, menu, InteractionConfigPage.OVERVIEW)
            text {
                appendLine("**Overview**")
                configService.validateConfiguration(guild).forEach { (k, v) ->
                    appendLine("${k.friendlyName}: ${if (v) GREEN_CHECK else RED_X}")
                }
            }
            actionRow {
                button {
                    enabled = false
                    value = "Enabled"
                }
                button {
                    style = if (settings.enabled) ButtonStyle.SUCCESS else ButtonStyle.DANGER
                    value = if (settings.enabled) "Enabled" else "Disabled"
                    onClick {
                        settings.enabled = !settings.enabled
                        configService.setConfiguration(guild, settings)
                        menu.rerender()
                    }
                }
            }
        }
        settingsMenu.page(InteractionConfigPage.ROLE_CONFIG) { menu ->
            val settings = configService.getConfiguration(guild)
            buildSelectPage(this, menu, InteractionConfigPage.ROLE_CONFIG)
            text {
                appendLine("**Role Configuration**")
                appendLine(buildString {
                    append("Moderator Role: ")
                    val r = settings.modRole?.run { guild.getRoleById(this) }
                    append(r?.asMention ?: "Not Configured")
                })
                appendLine(buildString {
                    append("Proxy Role: ")
                    val r = settings.proxyModRole?.run { guild.getRoleById(this) }
                    append(r?.asMention ?: "Not Configured")
                })
            }
            actionRow {
                select {
                    placeholder = "Select a role to configure"
                    option {
                        value = "Proxy Role"
                        description = "The role that users will ping"
                        onSelect {
                            menu.setState("role_page", "proxyModRole")
                        }
                        default = menu.getState<String>("role_page") == "proxyModRole"
                    }
                    option {
                        value = "Mod Role"
                        description = "The moderator role that will be pinged by the bot"
                        onSelect {
                            menu.setState("role_page", "modRole")
                        }
                        default = menu.getState<String>("role_page") == "modRole"
                    }
                }
            }
            val selectedPage = menu.getState<String>("role_page")
            if (selectedPage != null) {
                actionRow {
                    select {
                        guild.roles.filter { it.name != "@everyone" }.forEach { role ->
                            option {
                                onSelect {
                                    settings.setField(selectedPage, role.id)
                                    configService.setConfiguration(guild, settings)
                                    menu.rerender()
                                }
                                default = role.id == settings.getField(selectedPage)
                                value = "@${role.name} [${role.id}]"
                            }
                        }
                    }
                }
            }
        }
        settingsMenu.page(InteractionConfigPage.MESSAGE_CONFIG) { menu ->
            val settings = configService.getConfiguration(guild)
            buildSelectPage(this, menu, InteractionConfigPage.MESSAGE_CONFIG)
            val selectedMessage = menu.getState<String>("message")
            text = buildString {
                appendLine("**Messages**")
                if (selectedMessage != null) {
                    appendLine("```${settings.getField<String>(selectedMessage)}```")
                }
            }
            actionRow {
                select {
                    placeholder = "Select a message to configure"
                    option {
                        value = "Confirmation Message"
                        description = "The message sent to users asking them to confirm pinging"
                        onSelect {
                            menu.setState("message", "confirmationMessage")
                        }
                        default = selectedMessage == "confirmationMessage"
                    }
                    option {
                        value = "Too Frequent Message"
                        description =
                            "The message sent to users when they're pinging the mod role too frequently"
                        onSelect {
                            menu.setState("message", "tooFrequentPingMessage")
                        }
                        default = selectedMessage == "tooFrequentPingMessage"
                    }
                }
            }
            if (selectedMessage != null) {
                actionRow {
                    button {
                        value = "Edit"
                        onClick { hook ->
                            hook.sendMessage("Type the new message into the chat and send it")
                                .setEphemeral(true).queue {
                                    waitForMessage(hook) { evt ->
                                        log.debug("Running edit for $selectedMessage")
                                        settings.setField(selectedMessage, evt.message.contentRaw)
                                        evt.message.delete().queue()
                                        configService.setConfiguration(guild, settings)
                                        hook.editOriginal(menu.render()).queue()
                                    }
                                }
                        }
                    }
                }
            }
        }
        settingsMenu.page(InteractionConfigPage.CHANNEL_CONFIG) { menu ->
            val settings = configService.getConfiguration(guild)
            buildSelectPage(this, menu, InteractionConfigPage.CHANNEL_CONFIG)
            val channels = guild.textChannels.filter {
                it.canTalk() && guild.getMember(user)!!
                    .hasPermission(it, Permission.VIEW_CHANNEL)
            }
            text {
                appendLine("**Channel Configuration**")
                appendLine()
                appendLine("Not seeing the channel you're looking for? Ensure I have permissions to send messages there!")
            }
            actionRow {
                button {
                    value = "Ping Mods in Current Channel"
                    enabled = false
                }
                button {
                    value = if (settings.pingModsInCurrentChannel) "Enabled" else "Disabled"
                    style =
                        if (settings.pingModsInCurrentChannel) ButtonStyle.SUCCESS else ButtonStyle.DANGER
                    onClick {
                        if (!settings.pingModsInCurrentChannel) {
                            settings.modPingChannel = null
                        }
                        settings.pingModsInCurrentChannel = !settings.pingModsInCurrentChannel
                        configService.setConfiguration(guild, settings)
                        menu.rerender()
                    }
                }
            }
            if (!settings.pingModsInCurrentChannel) {
                if (channels.size < 25) {
                    actionRow {
                        select {
                            channels.forEach { chan ->
                                option {
                                    value = "#${chan.name}"
                                    default = settings.modPingChannel == chan.id
                                    onSelect {
                                        settings.modPingChannel = chan.id
                                        configService.setConfiguration(guild, settings)
                                        menu.rerender()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    actionRow {
                        button {
                            val chan =
                                settings.modPingChannel?.run { guild.getTextChannelById(this) }
                            value = "[${chan?.name ?: "Not Set"}] Click to Change"
                            style = ButtonStyle.SUCCESS
                            onClick { h ->
                                h.sendMessage("Send a message containing the channel you wish to set")
                                    .setEphemeral(true).queue {
                                        waitForMessage(h) { evt ->
                                            evt.message.delete().queue()
                                            if (evt.message.mentionedChannels.size < 1) {
                                                h.sendMessage("You did not send a channel mention, please try again")
                                                    .setEphemeral(true).queue()
                                                return@waitForMessage
                                            }
                                            settings.modPingChannel =
                                                evt.message.mentionedChannels.first().id
                                            configService.setConfiguration(guild, settings)
                                            h.editOriginal(menu.render()).queue()
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }
        settingsMenu.page(InteractionConfigPage.TIMEOUT_CONFIG) {
            buildSelectPage(this, it, InteractionConfigPage.TIMEOUT_CONFIG)
            text = "Timeout"
        }
        menuManager.register(settingsMenu)
        return settingsMenu.render()
    }

    private fun waitForMessage(hook: InteractionHook, onMessage: (MessageReceivedEvent) -> Unit) {
        val lookupKey = Pair(
            hook.interaction.user.id,
            hook.interaction.channel!!.id
        )
        pendingMessages[lookupKey] = onMessage
        val future = scheduler.schedule({
            log.debug("Purging $lookupKey")
        }, Instant.now().plus(30, ChronoUnit.SECONDS))
        pendingMessageFutures[lookupKey] = future
    }

    @EventListener
    fun onMessage(event: MessageReceivedEvent) {
        if (event.isFromGuild) {
            val lookupKey = Pair(event.author.id, event.channel.id)
            pendingMessages.remove(lookupKey)?.invoke(event)
            pendingMessageFutures.remove(lookupKey)?.cancel(true)
        }
    }
}