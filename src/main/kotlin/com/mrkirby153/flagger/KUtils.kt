package com.mrkirby153.flagger

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import java.lang.reflect.Field


private val mapper = jacksonObjectMapper()

data class CachedField(val clazz: Class<*>, val name: String)

val fieldCache = mutableMapOf<CachedField, Field>()

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
        val foundField = findField(this.javaClass, fieldName)
            ?: throw NoSuchFieldError("Field $fieldName was not found on ${this.javaClass} or any of its superclasses")
        if (!foundField.trySetAccessible()) {
            throw SecurityException("Could not make $fieldName in ${this.javaClass} accessible")
        }
        fieldCache[cachedField] = foundField
        foundField.set(this, value)
    }
}

fun findField(clazz: Class<*>, field: String): Field? {
    var curr: Class<*>? = clazz
    var found = false
    // Walk up the class tree
    do {
        try {
            val f = curr?.getDeclaredField(field)
            if (f != null) {
                return f
            }
        } catch (e: NoSuchFieldException) {
            // Do nothing
        }
        curr = curr?.superclass
    } while (curr != null)
    return null
}

inline fun <reified T> Any.getField(fieldName: String): T? {
    val cachedField = CachedField(this.javaClass, fieldName)
    val field = fieldCache[cachedField]
    if (field != null) {
        val obj = field.get(this) ?: return null
        if (obj is T) {
            return obj
        } else {
            throw ClassCastException("Cannot cast $obj to required type")
        }
    } else {
        val foundField = findField(this.javaClass, fieldName)
            ?: throw NoSuchFieldError("Field $fieldName was not found on ${this.javaClass} or any of its superclasses")
        foundField.trySetAccessible()
        fieldCache[cachedField] = foundField
        val obj = foundField.get(this) ?: return null
        if (obj is T) {
            return obj
        } else {
            throw ClassCastException("Cannot cast $obj to required type")
        }
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