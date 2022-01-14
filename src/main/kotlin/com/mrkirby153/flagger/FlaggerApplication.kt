package com.mrkirby153.flagger

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FlaggerApplication

fun main(args: Array<String>) {
	runApplication<FlaggerApplication>(*args)
}
