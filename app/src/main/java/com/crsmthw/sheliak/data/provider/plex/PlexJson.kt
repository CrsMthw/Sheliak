package com.crsmthw.sheliak.data.provider.plex

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/*
 * Plex JSON is loosely typed: the official examples carry "size": "1", "id": "221632" and "allowSync": "1" where
 * the schema says integer or boolean (docs/PLEX.md §3, correction 14). These serializers accept any JSON primitive
 * (a number, a quoted number, a boolean, "1"/"0") for the type they stand for, and read a value they cannot use
 * (an object where a scalar was expected, "abc" for a number, "" for anything but a string) as null instead of
 * failing the whole response. Every Plex DTO field that is not an object or a list uses one of them, so one odd
 * field never costs a sync. They are nullable serializers: JSON null and a missing key both read as null.
 */

/** The element as a non-null primitive, or null (JSON null, an object, an array). JSON input only. */
private fun Decoder.primitiveOrNull(): JsonPrimitive? {
    val json = this as? JsonDecoder ?: throw SerializationException("Plex DTOs are read from JSON only")
    val element = json.decodeJsonElement()
    return (element as? JsonPrimitive)?.takeUnless { it is JsonNull }
}

/** Writes [value] (or JSON null) — the DTOs are only ever written by tests, always as JSON. */
private fun Encoder.encodeJson(value: JsonElement) {
    val json = this as? JsonEncoder ?: throw SerializationException("Plex DTOs are written as JSON only")
    json.encodeJsonElement(value)
}

private fun JsonPrimitive.number(): Double? =
    content.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }

/** Any primitive's text: `221632` and `"221632"` both read as "221632". */
object PlexStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.crsmthw.sheliak.plex.PlexString", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? = decoder.primitiveOrNull()?.content

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeJson(if (value == null) JsonNull else JsonPrimitive(value))
    }
}

/** `1`, `"1"`, `1.0` and `"1.0"` read as 1; anything else (booleans, "", text) as null. */
object PlexIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.crsmthw.sheliak.plex.PlexInt", PrimitiveKind.INT).nullable

    override fun deserialize(decoder: Decoder): Int? {
        val p = decoder.primitiveOrNull() ?: return null
        if (p.isString.not() && (p.content == "true" || p.content == "false")) return null
        return p.content.trim().toIntOrNull()
            ?: p.number()?.takeIf { it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }?.toInt()
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        encoder.encodeJson(if (value == null) JsonNull else JsonPrimitive(value))
    }
}

/** As [PlexIntSerializer], for epoch seconds, sizes in bytes and ids that outgrow an Int. */
object PlexLongSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.crsmthw.sheliak.plex.PlexLong", PrimitiveKind.LONG).nullable

    override fun deserialize(decoder: Decoder): Long? {
        val p = decoder.primitiveOrNull() ?: return null
        if (p.isString.not() && (p.content == "true" || p.content == "false")) return null
        return p.content.trim().toLongOrNull()
            ?: p.number()?.takeIf { it >= Long.MIN_VALUE.toDouble() && it <= Long.MAX_VALUE.toDouble() }?.toLong()
    }

    override fun serialize(encoder: Encoder, value: Long?) {
        encoder.encodeJson(if (value == null) JsonNull else JsonPrimitive(value))
    }
}

/** Decimals such as the loudness `gain` (dB) and `userRating`; quoted or not. */
object PlexFloatSerializer : KSerializer<Float?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.crsmthw.sheliak.plex.PlexFloat", PrimitiveKind.FLOAT).nullable

    override fun deserialize(decoder: Decoder): Float? {
        val p = decoder.primitiveOrNull() ?: return null
        if (p.isString.not() && (p.content == "true" || p.content == "false")) return null
        return p.number()?.toFloat()
    }

    override fun serialize(encoder: Encoder, value: Float?) {
        encoder.encodeJson(if (value == null) JsonNull else JsonPrimitive(value))
    }
}

/** `true`, `"true"`, `1` and `"1"` read as true; `false`, `"false"`, `0`, `"0"` as false; "" and text as null. */
object PlexBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.crsmthw.sheliak.plex.PlexBoolean", PrimitiveKind.BOOLEAN).nullable

    override fun deserialize(decoder: Decoder): Boolean? {
        val p = decoder.primitiveOrNull() ?: return null
        return when (p.content.trim().lowercase(Locale.ROOT)) {
            "true"  -> true
            "false" -> false
            else    -> p.number()?.let { it != 0.0 }
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        encoder.encodeJson(if (value == null) JsonNull else JsonPrimitive(value))
    }
}
