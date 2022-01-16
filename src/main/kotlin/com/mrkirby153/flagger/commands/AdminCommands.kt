package com.mrkirby153.flagger.commands

import com.mrkirby153.botcore.command.slashcommand.SlashCommand
import com.mrkirby153.botcore.command.slashcommand.SlashCommandAvailability
import com.mrkirby153.flagger.services.interactionconfig.InteractionConfigPage
import com.mrkirby153.flagger.services.interactionconfig.InteractionConfigService
import me.mrkirby153.kcutils.Time
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import org.springframework.stereotype.Component

@Component
class AdminCommands(
    private val interactionConfigService: InteractionConfigService
) {


    @SlashCommand(name = "ping", description = "Check's the bots ping", clearance = 100)
    fun ping(event: SlashCommandEvent) {
        val start = System.currentTimeMillis()
        event.deferReply().queue {
            it.editOriginal("Pong! %s".format(Time.format(1, System.currentTimeMillis() - start)))
                .queue()
        }
    }

    @SlashCommand(
        name = "config",
        description = "Displays the configuration",
        clearance = 100,
        availability = [SlashCommandAvailability.GUILD]
    )
    fun setup(event: SlashCommandEvent) {
        event.reply(interactionConfigService.getPage(InteractionConfigPage.OVERVIEW, event.guild!!))
            .setEphemeral(true).queue()
    }
}