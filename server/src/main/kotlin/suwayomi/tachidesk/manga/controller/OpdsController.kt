package suwayomi.tachidesk.manga.controller

import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.http.Context
import suwayomi.tachidesk.manga.impl.Source
import suwayomi.tachidesk.server.util.XmlFeed
import java.util.UUID

object OpdsController {
    private val catalogUuid = UUID.randomUUID().toString()

    fun defineEndpoints() {
        path("opds") {
            // get(::rootCatalog)
            // get("sources", ::sourcesNavigationFeed)
            // get("source/{sourceId}", ::sourceAcquisitionFeed)
            // get("manga/{mangaId}/chapters", ::chaptersAcquisitionFeed)
        }
    }

    // private fun rootCatalog(ctx: Context) {
    //     ctx.xmlFeed {
    //         title { +"Suwayomi OPDS Catalog" }
    //         id { +"urn:uuid:$catalogUuid" }
    //         updated { +currentTime() }
    //         author {
    //             name { +"Suwayomi" }
    //         }
    //         link {
    //             href = ctx.url()
    //             rel = "self"
    //             type = "application/atom+xml;profile=opds-catalog;kind=navigation"
    //         }
    //         link {
    //             href = ctx.url()
    //             rel = "start"
    //             type = "application/atom+xml;profile=opds-catalog;kind=navigation"
    //         }

    //         Source.getSourceList().forEach { source ->
    //             entry {
    //                 title { +source.name }
    //                 id { +"urn:uuid:source-${source.id}" }
    //                 updated { +currentTime() }
    //                 content { +"Mangas from ${source.name}" }
    //                 link {
    //                     href = "${ctx.url()}/source/${source.id}"
    //                     rel = "subsection"
    //                     type = "application/atom+xml;profile=opds-catalog;kind=acquisition"
    //                 }
    //             }
    //         }
    //     }
    // }

    // private fun sourcesNavigationFeed(ctx: Context) {
    //     ctx.xmlFeed {
    //         title { +"Available Sources" }
    //         id { +"urn:uuid:${UUID.randomUUID()}" }
    //         updated { +currentTime() }
    //         link {
    //             href = ctx.url()
    //             rel = "self"
    //             type = "application/atom+xml;profile=opds-catalog;kind=navigation"
    //         }
    //         link {
    //             href = "${ctx.url()}/opds"
    //             rel = "start"
    //             type = "application/atom+xml;profile=opds-catalog;kind=navigation"
    //         }

    //         Source.getSourceList().forEach { source ->
    //             entry {
    //                 title { +source.name }
    //                 id { +"urn:uuid:source-${source.id}" }
    //                 updated { +currentTime() }
    //                 content { +"Mangas from ${source.name}" }
    //                 link {
    //                     href = "${ctx.url()}/source/${source.id}"
    //                     rel = "subsection"
    //                     type = "application/atom+xml;profile=opds-catalog;kind=acquisition"
    //                 }
    //             }
    //         }
    //     }
    // }

    // private fun sourceAcquisitionFeed(ctx: Context) {
    //     val sourceId = ctx.pathParam("sourceId").toLong()
    //     val source = Source.getSource(sourceId)!!
    //     val mangas = getMangasForSource(sourceId)

    //     ctx.xmlFeed {
    //         title { +source.name }
    //         id { +"urn:uuid:source-mangas-${source.id}" }
    //         updated { +currentTime() }
    //         link {
    //             href = ctx.url()
    //             rel = "self"
    //             type = "application/atom+xml;profile=opds-catalog;kind=acquisition"
    //         }
    //         link {
    //             href = "${ctx.url()}/opds"
    //             rel = "start"
    //             type = "application/atom+xml;profile=opds-catalog;kind=navigation"
    //         }

    //         mangas.forEach { manga ->
    //             entry {
    //                 title { +manga.title }
    //                 id { +"urn:uuid:manga-${manga.id}" }
    //                 updated { +manga.lastUpdate.toString() }
    //                 content { +(manga.description ?: "No description available") }
    //                 link {
    //                     href = "${ctx.url()}/manga/${manga.id}/chapters"
    //                     rel = "subsection"
    //                     type = "application/atom+xml;profile=opds-catalog;kind=acquisition"
    //                 }
    //                 link {
    //                     href = manga.thumbnailUrl ?: ""
    //                     rel = "http://opds-spec.org/image/thumbnail"
    //                     type = "image/jpeg"
    //                 }
    //             }
    //         }
    //     }
    // }

    // private fun chaptersAcquisitionFeed(ctx: Context) {
    //     val mangaId = ctx.pathParam("mangaId").toInt()
    //     val chapters = getChaptersForManga(mangaId)

    //     ctx.xmlFeed {
    //         title { +"Downloaded Chapters" }
    //         id { +"urn:uuid:manga-chapters-$mangaId" }
    //         updated { +currentTime() }
    //         link {
    //             href = ctx.url()
    //             rel = "self"
    //             type = "application/atom+xml;profile=opds-catalog;kind=acquisition"
    //         }
    //         link {
    //             href = "${ctx.url()}/opds"
    //             rel = "start"
    //             type = "application/atom+xml;profile=opds-catalog;kind=navigation"
    //         }

    //         chapters.forEach { chapter ->
    //             entry {
    //                 title { +chapter.name }
    //                 id { +"urn:uuid:chapter-${chapter.id}" }
    //                 updated { +chapter.lastUpdate.toString() }
    //                 content { +"Chapter ${chapter.chapterNumber}" }
    //                 link {
    //                     href = getChapterDownloadUrl(chapter)
    //                     rel = "http://opds-spec.org/acquisition"
    //                     type = "application/vnd.comicbook+zip"
    //                 }
    //             }
    //         }
    //     }
    // }

    // private fun getMangasForSource(sourceId: Long): List<MangaDataClass> {
    //     // Implementar lógica para obtener mangas de una fuente
    //     return emptyList()
    // }

    // private fun getChaptersForManga(mangaId: Int): List<ChapterDataClass> {
    //     // Implementar lógica para obtener capítulos de un manga
    //     return emptyList()
    // }

    // private fun getChapterDownloadUrl(chapter: ChapterDataClass): String {
    //     // Implementar lógica para obtener URL de descarga CBZ/CBR
    //     return ""
    // }

    // private fun currentTime(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
}
