package com.mrkirby153.flagger.config

import com.mrkirby153.interactionmenus.MenuManager
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MenuConfig(
    private val shardManager: ShardManager
) {

    @Bean
    fun menuManager(): MenuManager {
        val menuManager = MenuManager()
        shardManager.addEventListener(menuManager)
        return menuManager
    }
}