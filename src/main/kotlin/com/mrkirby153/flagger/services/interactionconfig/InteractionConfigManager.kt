package com.mrkirby153.flagger.services.interactionconfig

import com.mrkirby153.flagger.getField
import com.mrkirby153.flagger.services.config.ConfigurationService
import com.mrkirby153.flagger.setField
import com.mrkirby153.interactionmenus.Menu
import com.mrkirby153.interactionmenus.MenuManager
import com.mrkirby153.interactionmenus.builders.PageBuilder
import com.mrkirby153.interactionmenus.builders.SelectMenuBuilder
import com.mrkirby153.interactionmenus.builders.SelectOptionBuilder
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
import kotlin.math.ceil
import kotlin.math.min


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
                            menu.setState("role_page_paginator", 0)
                        }
                        default = menu.getState<String>("role_page") == "proxyModRole"
                    }
                    option {
                        value = "Mod Role"
                        description = "The moderator role that will be pinged by the bot"
                        onSelect {
                            menu.setState("role_page", "modRole")
                            menu.setState("role_page_paginator", 0)
                        }
                        default = menu.getState<String>("role_page") == "modRole"
                    }
                }
            }
            val selectedPage = menu.getState<String>("role_page")
            if (selectedPage != null) {
                actionRow {
                    select {
                        val selectedRole =
                            settings.getField<String>(selectedPage)?.run { guild.getRoleById(this) }
                        placeholder =
                            if (selectedRole == null) "Select a Role" else "@${selectedRole.name} [${selectedRole.id}]"
                        paginated(
                            this, guild.roles.filter { it.name != "@everyone" }, onPageChange = {
                                menu.setState("role_page_paginator", it)
                            }, page = menu.getState("role_page_paginator") ?: 0
                        ) { role ->
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
            subPage(menu.getState("category"), {
                menu.setState("category", it)
            }) {
                page("Log Channel", "Configure the log channel") {
                    actionRow {
                        select {
                            val currChannel =
                                settings.logChannel?.run { guild.getTextChannelById(this) }
                            placeholder =
                                if (currChannel == null) "Select a Channel" else "#${currChannel.name}"
                            paginated(
                                this,
                                listOf(null, *channels.toTypedArray()),
                                menu.getState<Int>("log_channel_page") ?: 0,
                                onPageChange = {
                                    menu.setState("log_channel_page", it)
                                }
                            ) { item ->
                                if (item == null) {
                                    value = "Disabled"
                                    default = settings.logChannel == null
                                    onSelect {
                                        settings.logChannel = null
                                        configService.setConfiguration(guild, settings)
                                        menu.rerender()
                                    }
                                } else {
                                    value = "#${item.name}"
                                    default = settings.logChannel == item.id
                                    onSelect {
                                        settings.logChannel = item.id
                                        configService.setConfiguration(guild, settings)
                                        menu.rerender()
                                    }
                                }
                            }
                        }
                    }
                }
                page(
                    "Mod Ping Channel",
                    "Configure settings for where moderators should be pinged"
                ) {
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
                                settings.pingModsInCurrentChannel =
                                    !settings.pingModsInCurrentChannel
                                configService.setConfiguration(guild, settings)
                                menu.rerender()
                            }
                        }
                    }
                    if (!settings.pingModsInCurrentChannel) {
                        actionRow {
                            select {
                                val currChannel =
                                    settings.modPingChannel?.run { guild.getTextChannelById(this) }
                                placeholder =
                                    if (currChannel == null) "Select a Channel" else "#${currChannel.name}"
                                paginated(
                                    this,
                                    channels,
                                    menu.getState<Int>("mod_ping_page") ?: 0,
                                    onPageChange = {
                                        menu.setState("mod_ping_page", it)
                                    }) { item ->
                                    value = "#${item.name}"
                                    default = settings.modPingChannel == item.id
                                    onSelect {
                                        settings.modPingChannel = item.id
                                        configService.setConfiguration(guild, settings)
                                        menu.rerender()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        settingsMenu.page(InteractionConfigPage.TIMEOUT_CONFIG)
        {
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

    private fun <T> paginated(
        selectMenuBuilder: SelectMenuBuilder,
        options: List<T>,
        page: Int,
        perPage: Int = 23,
        onPageChange: (Int) -> Unit,
        builder: SelectOptionBuilder.(T) -> Unit,
    ) {
        val minIndex = page * perPage
        val maxIndex = min(minIndex + perPage, options.size)
        val items = options.subList(minIndex, maxIndex)
        val maxPages = ceil(options.size / perPage.toDouble())
        log.debug("Generating paginator from ${options.size} options. Page: $page, min: $minIndex, max: $maxIndex, total pages: $maxPages")
        selectMenuBuilder.min = 1
        selectMenuBuilder.max = 1

        if (page > 0) {
            // Previous page
            selectMenuBuilder.option {
                value = "Previous Page"
                onSelect {
                    onPageChange(page - 1)
                }
            }
        }
        items.forEach {
            selectMenuBuilder.option {
                this.builder(it)
            }
        }
        if (page < maxPages - 1) {
            selectMenuBuilder.option {
                value = "Next Page"
                onSelect {
                    onPageChange(page + 1)
                }
            }
        }
    }
}