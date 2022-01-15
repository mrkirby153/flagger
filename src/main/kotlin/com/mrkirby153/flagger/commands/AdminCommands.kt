package com.mrkirby153.flagger.commands

import com.mrkirby153.botcore.command.slashcommand.SlashCommand
import me.mrkirby153.kcutils.Time
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import org.springframework.stereotype.Component

@Component
class AdminCommands {


    @SlashCommand(name = "ping", description = "Check's the bots ping", clearance = 100)
    fun ping(event: SlashCommandEvent) {
        val start = System.currentTimeMillis()
        event.deferReply().queue {
            it.editOriginal("Pong! %s".format(Time.format(1, System.currentTimeMillis() - start)))
                .queue()
        }
    }
}