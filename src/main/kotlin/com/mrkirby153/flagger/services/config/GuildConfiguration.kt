package com.mrkirby153.flagger.services.config

import com.mrkirby153.flagger.JsonSerializable

data class GuildConfiguration(
    /**
     * If proxying is enabled
     */
    var enabled: Boolean = false,
    /**
     * The id of the mod role to ping
     */
    var modRole: String? = null,

    /**
     * The id of the mod role that users ping
     */
    var proxyModRole: String? = null,

    /**
     * If moderators should be pinged in the current channel when confirming
     */
    var pingModsInCurrentChannel: Boolean = true,

    /**
     * The id of the log channel
     */
    var logChannel: String? = null,

    /**
     * The time in ms before timing out the confirmation message
     */
    var confirmTimeout: Long = 10 * 1000,

    /**
     * The message displayed to the user to confirm the mod role ping
     */
    var confirmationMessage: String = "Are you sure you want to ping the moderators?",

    /**
     * The minimum time between mod role pings
     */
    var minTimeBetweenPings: Long = 0
): JsonSerializable