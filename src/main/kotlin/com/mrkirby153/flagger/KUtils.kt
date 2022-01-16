package com.mrkirby153.flagger

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import java.lang.reflect.Field


private val mapper = jacksonObjectMapper()

private data class CachedField(val clazz: Class<*>, val name: String)

private val fieldCache = mutableMapOf<CachedField, Field>()

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
 * Sets the provided [fieldName] to [value], traversing the class tree if nececary
 */
fun Any.setField(fieldName: String, value: Any?) {
    val cachedField = CachedField(this.javaClass, fieldName)
    val field = fieldCache[cachedField]
    if (field != null) {
        field.set(this, value)
    } else {
        var curr: Class<*>? = this.javaClass
        var found = false
        // Walk up the class tree
        do {
            val f = curr?.getDeclaredField(fieldName)
            if (f != null) {
                found = true
                fieldCache[cachedField] = f
                if (f.trySetAccessible())
                    f.set(this, value)
                else
                    throw SecurityException("Could not make $fieldName accessible on ${this.javaClass}, found in $curr")
                break
            }
            curr = curr?.superclass
        } while (curr != null)
        if (!found)
            throw NoSuchFieldError("Field $fieldName was not found on ${this.javaClass} or any of its superclasses")
    }
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