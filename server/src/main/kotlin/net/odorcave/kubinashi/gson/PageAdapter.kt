package net.odorcave.kubinashi.gson

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import eu.kanade.tachiyomi.source.model.Page

class PageAdapter : TypeAdapter<Page>() {
    override fun write(writer: JsonWriter?, value: Page?) {
        writer!!

        if (value == null) {
            writer.nullValue()
            return
        }

        writer.beginObject()
        writer.name("index")
        writer.value(value.index)
        writer.name("url")
        writer.value(value.url)
        writer.name("imageUrl")
        writer.value(value.imageUrl)
        writer.endObject()
    }

    override fun read(reader: JsonReader?): Page {
        reader!!
        var index: Int = 0
        var url: String = ""
        var imageUrl: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            val fieldName = reader.nextName()

            when (fieldName) {
                "index" -> index = reader.nextInt()
                "url" -> url = reader.nextString()
                "imageUrl" -> imageUrl = reader.nextString()
            }
        }
        reader.endObject()

        return Page(
            index = index,
            url = url,
            imageUrl = imageUrl,
        )
    }

}
