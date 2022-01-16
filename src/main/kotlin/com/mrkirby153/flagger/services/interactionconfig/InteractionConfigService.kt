package com.mrkirby153.flagger.services.interactionconfig

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent

interface InteractionConfigService {

    /**
     * Gets the [Message] for the given [page] in the provided [guild]
     */
    fun getPage(page: InteractionConfigPage, guild: Guild): Message

    /**
     * Handle the given [event]
     */
    fun handleButtonClick(event: ButtonClickEvent)

    /**
     * Handle the given [event]
     */
    fun handleSelectMenu(event: SelectionMenuEvent)
}

/**
 * A list of pages in the configuration
 */
enum class InteractionConfigPage(val displayName: String, val description: String? = null) {
    OVERVIEW("Overview"),
    ROLE_CONFIG("Role Configuration"),
    MESSAGE_CONFIG("Message Configuration"),
    CHANNEL_CONFIG("Channel Configuration"),
    TIMEOUT_CONFIG("Timeout Configuration")
}