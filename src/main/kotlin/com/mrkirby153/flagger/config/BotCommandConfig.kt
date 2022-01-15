package com.mrkirby153.flagger.config

import com.mrkirby153.botcore.command.ClearanceResolver
import com.mrkirby153.botcore.command.slashcommand.SlashCommandExecutor
import com.mrkirby153.botcore.spring.event.BotReadyEvent
import com.mrkirby153.flagger.commands.AdminCommands
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.AllowedMentions
import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

val COMMAND_CLASSES = listOf(AdminCommands::class.java)

@Configuration
class BotCommandConfig(
    private val context: ApplicationContext,
    @Value("\${bot.command-guilds:}") private val slashCommandGuilds: String,
    private val shardManager: ShardManager
) {

    init {
        AllowedMentions.setDefaultMentions(emptySet())
    }

    private val log = LogManager.getLogger()

    @Bean
    fun slashCommandExecutor(): SlashCommandExecutor {
        val ex = SlashCommandExecutor(clearanceResolver = object : ClearanceResolver {
            override fun resolve(member: Member): Int {
                return if (member.hasPermission(Permission.MANAGE_ROLES)) {
                    100
                } else {
                    0
                }
            }
        })
        registerTypeResolvers(ex)
        return ex
    }

    fun registerSlashCommands() {
        log.info("Registering slash commands from ${COMMAND_CLASSES.size} classes")
        COMMAND_CLASSES.forEach { clazz ->
            log.info("Registering $clazz")
            slashCommandExecutor().discoverAndRegisterSlashCommands(context.getBean(clazz), clazz)
        }
        val commands = slashCommandExecutor().flattenSlashCommands();
        log.info("Discovered ${commands.size} slash commands")
        if (slashCommandGuilds.isEmpty()) {
            shardManager.shards[0].updateCommands().addCommands(commands).queue {
                log.info("Updated {} global slash commands", it.size)
            }
        } else {
            slashCommandGuilds.split(",").mapNotNull { shardManager.getGuildById(it) }
                .forEach { g ->
                    log.info("Updating commands in $g")
                    g.updateCommands().addCommands(commands).queue {
                        log.info("Updated {} commands in $g", it.size)
                    }
                }
        }
    }


    private fun registerTypeResolvers(ex: SlashCommandExecutor) {
        log.debug("Registering type resolvers")
    }

    @EventListener
    fun onReady(event: BotReadyEvent) {
        registerSlashCommands()
    }

    @EventListener
    fun onSlashCommand(event: SlashCommandEvent) {
        if (!slashCommandExecutor().executeSlashCommandIfAble(event)) {
            event.reply("There was no handler available to handle this command").setEphemeral(true)
                .queue()
        }
    }
}