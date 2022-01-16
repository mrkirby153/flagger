package com.mrkirby153.flagger.services.interactionconfig

import com.mrkirby153.flagger.services.config.ConfigurationService
import com.mrkirby153.flagger.setField
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu
import org.apache.logging.log4j.LogManager
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service


const val GREEN_CHECK = "✅"
const val RED_X = "❌"

@Service
class InteractionConfigManager(
    private val configService: ConfigurationService
) : InteractionConfigService {

    private val log = LogManager.getLogger()

    override fun getPage(page: InteractionConfigPage, guild: Guild): Message {
        log.debug("Getting page $page in $guild")
        val msg = when (page) {
            InteractionConfigPage.OVERVIEW -> renderOverview(guild)
            InteractionConfigPage.ROLE_CONFIG -> renderRoleConfig(guild)
            InteractionConfigPage.MESSAGE_CONFIG -> renderMessageConfig(guild)
            InteractionConfigPage.CHANNEL_CONFIG -> renderChannelConfig(guild)
            InteractionConfigPage.TIMEOUT_CONFIG -> renderTimeoutConfig(guild)
        }
        // Append the select menu first
        return MessageBuilder(msg).apply {
            val newActionRows = mutableListOf<ActionRow>()
            newActionRows.add(makeMenu(page))
            newActionRows.addAll(msg.actionRows)
            setActionRows(newActionRows)
        }.build()
    }

    @EventListener
    override fun handleButtonClick(event: ButtonClickEvent) {
        if (event.componentId.startsWith("option")) {
            log.debug("Button clicked!")
            event.deferEdit().queue { hook ->
                val parts = event.componentId.split(":").drop(1)
                when (parts[0]) {
                    "set" -> handleSetInteraction(parts.drop(1), event.guild!!, hook)
                }
                reRender(hook)
            }
        }
    }

    @EventListener
    override fun handleSelectMenu(event: SelectionMenuEvent) {
        log.debug("Select menu clicked")
        val guild = event.guild ?: return
        if (event.componentId.startsWith("menu")) {
            event.deferEdit().queue { hook ->
                val pageId = event.values.first()
                val page = InteractionConfigPage.valueOf(pageId)
                hook.editOriginal(getPage(page, guild)).queue()
            }
        }
        if (event.componentId.startsWith("option")) {
            event.deferEdit().queue() {
                val args = event.componentId.split(":").drop(1)
                when (args[0]) {
                    "set" -> {
                        val newArgs =
                            "${event.componentId};${event.values.joinToString()}".split(":").drop(1)
                        handleSetInteraction(newArgs.drop(1), event.guild!!, it)
                    }
                }
                reRender(it)
            }
        }
    }

    private fun handleSetInteraction(args: List<String>, guild: Guild, hook: InteractionHook) {
        log.debug("Handling set interaction with $args")
        if (args.size != 2) {
            hook.sendMessage(":warning: Could not process setting change: invalid payload")
                .setEphemeral(true).queue()
        } else {
            val field = args[0]
            val newVal = args[1]
            setSettingByKey(field, newVal, guild)
        }
    }

    private fun reRender(hook: InteractionHook) {
        hook.retrieveOriginal().queue { og ->
            val menuComponent = og.actionRows.flatMap { it.components }
                .first { it.id == "menu" } as SelectionMenu
            val defaultOption = menuComponent.options.first { it.isDefault }
            hook.editOriginal(
                getPage(
                    InteractionConfigPage.valueOf(defaultOption.value),
                    og.guild
                )
            ).queue()
        }
    }

    private fun setSettingByKey(setting: String, value: String, guild: Guild) {
        log.debug("Setting setting $setting to $value in $guild")
        val parts = value.split(";")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid value format")
        }
        val newValue: Any? = when (parts[0]) {
            "bool" -> parts[1] == "true"
            "str" -> parts[1]
            else -> null
        }
        log.debug("Parsed setting: $newValue")
        val currSettings = configService.getConfiguration(guild)
        currSettings.setField(setting, newValue)
        configService.setConfiguration(guild, currSettings)
    }

    private fun renderOverview(guild: Guild): Message {
        val config = configService.validateConfiguration(guild)
        val enabled = configService.getConfiguration(guild).enabled
        return MessageBuilder().apply {
            appendLine("**Configuration Overview**")
            config.forEach { (k, s) ->
                appendLine(" ${k.friendlyName}: ${if (s) GREEN_CHECK else RED_X}")
            }
            val enableButton = if (enabled) {
                Button.success("option:set:enabled:bool;false", "Yes")
            } else {
                Button.danger("option:set:enabled:bool;true", "No")
            }
            setActionRows(
                ActionRow.of(
                    Button.secondary("option:header:enabled", "Enabled").asDisabled(),
                    enableButton
                )
            )
        }.build()
    }

    private fun renderRoleConfig(guild: Guild): Message {
        val settings = configService.getConfiguration(guild)
        return MessageBuilder().apply {
            appendLine("**Role Config**")
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
            setActionRows(
                ActionRow.of(Button.secondary("option:header:modRole", "Mod Role: ").asDisabled()),
                ActionRow.of(
                    SelectionMenu.create("option:set:modRole:str").apply {
                        placeholder = "Select a moderator role"
                        addOptions(getOptionsForRoles(guild, settings.modRole))
                    }.build()
                ),
                ActionRow.of(Button.secondary("option:header:proxyRole", "Proxy Role: ").asDisabled()),
                ActionRow.of(
                    SelectionMenu.create("option:set:proxyModRole:str").apply {
                        placeholder = "Select a proxy role"
                        addOptions(getOptionsForRoles(guild, settings.proxyModRole))
                    }.build()
                )
            )
        }.build()
    }

    private fun renderMessageConfig(guild: Guild): Message {
        return MessageBuilder().apply {
            appendLine("**Message Config**")
        }.build()
    }

    private fun renderChannelConfig(guild: Guild): Message {
        return MessageBuilder().apply {
            appendLine("**Channel Config**")
        }.build()
    }

    private fun renderTimeoutConfig(guild: Guild): Message {
        return MessageBuilder().apply {
            appendLine("**Timeout Config**")
        }.build()
    }

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
}