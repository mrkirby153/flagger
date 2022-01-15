package com.mrkirby153.flagger.services.config

import net.dv8tion.jda.api.entities.Guild

interface ConfigurationService {

    /**
     * Gets the [GuildConfiguration] for the provided [guild]
     */
    fun getConfiguration(guild: Guild): GuildConfiguration

    /**
     * Updates the [GuildConfiguration] for the provided [Guild]
     */
    fun setConfiguration(guild: Guild, configuration: GuildConfiguration)

    /**
     * Validate a guild's configuration
     */
    fun validateConfiguration(guild: Guild): Map<ConfigValidation, Boolean>
}

enum class ConfigValidation(val friendlyName: String) {
    ENABLED("Enabled"),
    MOD_ROLE_EXIST("Mod Role Exists"),
    PROXY_ROLE_EXIST("Proxy Role Exists"),
    LOG_CHANNEL_EXIST("Log Channel Exists"),
    MOD_PING_CHANNEL_EXIST("Mod Ping Channel Exists")
}