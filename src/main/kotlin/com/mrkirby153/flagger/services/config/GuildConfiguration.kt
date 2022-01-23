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
     * The channel mods should be pinged in if ping in current channel is disabled
     */
    var modPingChannel: String? = null,

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
    var minTimeBetweenPings: Long = 0,

    var tooFrequentPingMessage: String = "Moderators have been pinged too recently and may still be looking at chat. Tag an online moderator for assistance instead",

    /**
     * If the proxy will immediately ping if all mods are not online
     */
    var skipPingIfOffline: Boolean = false,

    /**
     * What statuses are considered offline (as a bit string)
     * 1 - online
     * 2 - idle
     * 4 - dnd
     * 8 - offline
     */
    var offlineStatuses: Long = 14
) : JsonSerializable