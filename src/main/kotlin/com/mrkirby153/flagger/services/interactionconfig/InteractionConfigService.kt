package com.mrkirby153.flagger.services.interactionconfig

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User

interface InteractionConfigService {

    fun getMenu(user: User, guild: Guild): Message
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