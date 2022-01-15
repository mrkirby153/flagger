package com.mrkirby153.flagger

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper


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