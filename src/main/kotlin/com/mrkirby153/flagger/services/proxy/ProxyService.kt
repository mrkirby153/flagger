package com.mrkirby153.flagger.services.proxy

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message

interface ProxyService {

    /**
     * Gets the unix time in milliseconds of the last time the mod role was pinged for the given
     * [guild]. Returns -1 if the mod role has not been pinged this session
     */
    fun lastModRolePing(guild: Guild): Long

    /**
     * Resets the last mod role ping for the provided [guild]
     */
    fun resetLastModRolePing(guild: Guild)

    /**
     * Returns true if this guild is eligible for a moderator ping
     */
    fun isEligibleForPing(guild: Guild): Boolean

    /**
     * Processes a message
     */
    fun handleMessage(message: Message)
}