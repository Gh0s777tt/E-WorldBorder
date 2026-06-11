package com.wimbli.WorldBorder.UUID

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.util.UUID

// Originally from: https://github.com/eitetu/minecraft-server (UUIDTypeAdapter)
class UUIDTypeAdapter : TypeAdapter<UUID>() {

    override fun write(out: JsonWriter, value: UUID) {
        out.value(fromUUID(value))
    }

    override fun read(reader: JsonReader): UUID = fromString(reader.nextString())

    companion object {
        fun fromUUID(value: UUID): String = value.toString().replace("-", "")

        fun fromString(input: String): UUID = UUID.fromString(
            input.replaceFirst(
                Regex("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})"),
                "$1-$2-$3-$4-$5"
            )
        )
    }
}
