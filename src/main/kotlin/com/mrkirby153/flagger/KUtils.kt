package com.mrkirby153.flagger

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User


private val mapper = jacksonObjectMapper()

/**
 * Attempts to deserialize this string into the provided [clazz]
 */
fun <T> String.deserialize(clazz: Class<T>): T = mapper.readValue(this, clazz)

/**
 * Attempts to deserialize this string into an array of the provided [clazz]
 */
fun <T> String.deserializeArray(clazz: Class<T>): Array<T> {
    val type = mapper.typeFactory.constructArrayType(clazz)
    return mapper.readValue(this, type)
}

/**
 * Marker interface to designate a class as json serializable
 */
interface JsonSerializable

/**
 * Serializes the object to a json string
 */
fun JsonSerializable.serialize(): String = mapper.writeValueAsString(this)


/**
 * The user's username and discriminator combined into a string like it would be displayed in
 * the client
 */
val User.nameAndDiscrim: String
    get() = "${this.name}#${this.discriminator}"

/**
 * The member's username and discriminator combined into a string like it would be displayed in
 * the client
 */
val Member.nameAndDiscrim: String
    get() = this.user.nameAndDiscrim