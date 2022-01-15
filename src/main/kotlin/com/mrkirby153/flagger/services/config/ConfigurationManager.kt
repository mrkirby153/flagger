package com.mrkirby153.flagger.services.config

import com.fasterxml.jackson.core.JsonProcessingException
import com.mrkirby153.flagger.deserialize
import com.mrkirby153.flagger.serialize
import net.dv8tion.jda.api.entities.Guild
import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File

@Service
class ConfigurationManager(
    @Value("\${bot.config-dir:./config/settings}") private val configurationDirectory: File
) : ConfigurationService {

    private val log = LogManager.getLogger()

    private val configurationCache = mutableMapOf<String, GuildConfiguration>()

    init {
        if (configurationDirectory.exists()) {
            if (!configurationDirectory.isDirectory) {
                throw IllegalArgumentException(
                    "Provided configuration folder ${configurationDirectory.canonicalPath} is not a directory"
                )
            }
        } else {
            log.info("Creating configuration directory ${configurationDirectory.canonicalPath}")
            if (!configurationDirectory.mkdirs()) {
                throw IllegalStateException(
                    "Could not create the configuration directory at ${configurationDirectory.canonicalPath}"
                )
            }
        }
    }

    override fun getConfiguration(guild: Guild): GuildConfiguration {
        if (configurationCache[guild.id] != null) {
            return configurationCache[guild.id]!!
        }
        val configFile = File(configurationDirectory, "${guild.id}.json")
        val config = if (!configFile.exists()) {
            GuildConfiguration() // Return default configuration
        } else {
            try {
                configFile.readText().deserialize(GuildConfiguration::class.java)
            } catch (e: Exception) {
                when (e) {
                    is JsonProcessingException -> {
                        log.warn("Could not deserialize settings for $guild", e)
                        GuildConfiguration()
                    }
                    else -> throw e
                }
            }
        }
        configurationCache[guild.id] = config
        return config
    }

    override fun setConfiguration(guild: Guild, configuration: GuildConfiguration) {
        val configFile = File(configurationDirectory, "${guild.id}.json")
        val newConfig = configuration.serialize()
        configFile.writeText(newConfig)
        configurationCache[guild.id] = configuration
    }

    override fun validateConfiguration(guild: Guild): Map<ConfigValidation, Boolean> {
        val config = getConfiguration(guild)
        val results = mutableMapOf<ConfigValidation, Boolean>()
        results[ConfigValidation.ENABLED] = config.enabled
        results[ConfigValidation.MOD_ROLE_EXIST] =
            config.modRole?.run { guild.getRoleById(this) != null } ?: false
        results[ConfigValidation.PROXY_ROLE_EXIST] =
            config.proxyModRole?.run { guild.getRoleById(this) != null } ?: false
        results[ConfigValidation.LOG_CHANNEL_EXIST] =
            config.proxyModRole?.run { guild.getGuildChannelById(this) != null } ?: false
        return results
    }
}