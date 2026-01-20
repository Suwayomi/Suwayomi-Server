package net.odorcave.kubinashi

import com.google.errorprone.annotations.Immutable
import com.google.gson.Gson
import com.google.gson.LongSerializationPolicy
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapterImpl
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.chapter.ChapterSanitizer
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import net.odorcave.kubinashi.gson.PageAdapter
import net.odorcave.kubinashi.model.Chapter
import org.slf4j.event.Level
import suwayomi.tachidesk.manga.impl.extension.Extension.downloadAPKFile
import suwayomi.tachidesk.manga.impl.util.PackageTools.dex2jar
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.applicationSetup
import uy.kohesive.injekt.injectLazy
import java.io.Serializable

@Immutable
data class SourceManga(val source: Long, val manga: SMangaImpl) : Serializable;

@Immutable
data class SourceChapter(val source: Long, val chapter: SChapterImpl) : Serializable;

@Immutable
data class SourcePage(val source: Long, val page: Page) : Serializable;

@Immutable
data class NeededExtension(val apk: String) : Serializable
typealias NeededExtensionList = Array<NeededExtension>

fun Application.routing(logger: KLogger) {

    routing {

        post("/extensions/sync") {
            val extensionsToInstall = call.receive<NeededExtensionList>()

            extensionsToInstall.forEach {
                installExtensionApk(it.apk)
            }

            call.respond(HttpStatusCode.OK)
        }

        get("/search/{sourceId}") {
            logger.warn { "search called" }
            try {
                val query = call.request.queryParameters["q"]
                val language = call.request.queryParameters["l"] ?: "en"
                if (query == null) {
                    call.respond<Map<Long, List<SManga>>>(mapOf())
                    return@get
                }

                val sourceId = call.parameters["sourceId"]?.toLong()
                if (sourceId == null) {
                    call.response.status(HttpStatusCode.BadRequest)
                    return@get
                }

                logger.warn{"getting source"}
                val source = GetCatalogueSource.getCatalogueSourceOrNull(sourceId)
                if (source == null) {
                    logger.warn{"CatalogueSource not found for $sourceId"}
                    call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                    return@get
                }

                logger.warn{"calling source: ${source.name}"}
                try {
                    val page = source.getSearchManga(1, query, source.getFilterList())
                    val titles = page.mangas
                        .distinctBy { it.url }

                    logger.warn{ "got ${page.mangas.size} mangas" }

                    call.respond(titles)
                } catch (e: Exception) {
                    logger.error(e) { "Error querying source" }
                    call.response.status(HttpStatusCode.ServiceUnavailable)
                    return@get
                }
            } catch (e: Exception) {
                // Log.e(LOG_TAG, "Failed to query with error: ${e.message}\\n${e.stackTraceToString()} ")
            }
        }

        get("/manga/details") {

            val mangaToFetch: SourceManga;
            try {
                mangaToFetch = call.receive<SourceManga>()
            } catch (e: ContentTransformationException) {
                // Log.i(LOG_TAG, "${e}");
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val sourceId = mangaToFetch.source

            val source = GetCatalogueSource.getCatalogueSourceOrNull(sourceId)
            if (source == null) {
                call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                return@get
            }

            val mangaDetails = source.getMangaDetails(mangaToFetch.manga)

            call.respond(mangaDetails)
        }

        get("/manga/chapters") {
            val mangaToFetch = call.receive<SourceManga>()
            val sourceId = mangaToFetch.source
            val manga = mangaToFetch.manga;

            val source = GetCatalogueSource.getCatalogueSourceOrNull(sourceId)
            if (source == null) {
                call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                return@get
            }

            val sourceChapters = source.getChapterList(manga).mapIndexed { i, sChapter ->
                Chapter.create()
                    .copyFromSChapter(sChapter)
                    .copy(name = with(ChapterSanitizer) {sChapter.name.sanitize(manga.title)})
                    .copy(source_order = i.toLong())
            }.map {
                var chapter = it
                if (source is HttpSource) {
                    val sChapter = chapter.toSChapter()
                    source.prepareNewChapter(sChapter, manga)
                    chapter = chapter.copyFromSChapter(sChapter)
                }

                chapter = chapter.copy(chapter_number = ChapterRecognition.parseChapterNumber(manga.title, chapter.name, chapter.chapter_number))

                return@map chapter
            }

            call.respond(sourceChapters)
        }

        get("/manga/chapter/pages") {
            val chapterToFetch = call.receive<SourceChapter>()
            val sourceId = chapterToFetch.source

            val source = GetCatalogueSource.getCatalogueSourceOrNull(sourceId)
            if (source == null) {
                call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                return@get
            }

            val pageList = source.getPageList(chapterToFetch.chapter)

            call.respond(pageList)
            return@get
        }

        get("/manga/chapter/page/image") {
            val page: SourcePage
            try {
                page = call.receive<SourcePage>()
            } catch (e: Exception) {
                // Log.e("LOG_TAG", "$e: ${e.stackTraceToString()}")
                throw e
            }
            val sourceId = page.source

            val source = GetCatalogueSource.getCatalogueSourceOrNull(sourceId)
            if (source == null) {
                call.response.status(HttpStatusCode(404, "Source with id $sourceId not found"))
                return@get
            }

            if (source is HttpSource) {
                val response = source.getImage(page.page)
                val bodyContentType = response.body.contentType()!!
                call.respondBytes(contentType = ContentType(bodyContentType.type, bodyContentType.subtype)) {
                    response.body.bytes()
                }
                return@get
            }
        }
    }
}

suspend fun installExtensionApk(apkName: String) {
    val applicationDirs: ApplicationDirs by injectLazy();

    val apkURL = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/$apkName"
    val apkSavePath = "${applicationDirs.extensionsRoot}/$apkName"

    // download apk file
    downloadAPKFile(apkURL, apkSavePath)

    val jarName = apkName.substringBefore(".apk") + ".jar"
    val jarPath = "${applicationDirs.extensionsRoot}/$jarName"
    val fileNameWithoutType = apkName.substringBefore(".apk")

    dex2jar(apkSavePath, jarPath, fileNameWithoutType)

    GetCatalogueSource.loadCatalogueSourceFromApk(apkName)
}

fun main() {
    applicationSetup()
    var logger = KotlinLogging.logger {}

    val network: NetworkHelper by injectLazy();

    val LARAVEL_HOST = System.getenv("LARAVEL_HOST") ?: "http://localhost:8000"

    logger.warn { "Installing extensions" }
    runBlocking {
        val response = network.client.newCall(
            GET("${LARAVEL_HOST}/extensions/needed")
        ).execute().body.string()

        val neededExtensions = Gson().fromJson(response, NeededExtensionList::class.java)

        neededExtensions.map { it.apk }.forEach { apkName ->
            logger.warn { "Installing $apkName" }

            try {
                installExtensionApk(apkName);
            } catch (e: Exception) {
                logger.error(e) { "Error installing $apkName" }
            }
        }
    }

    val server = embeddedServer(Netty, port = 8081) {
        install(ContentNegotiation) {
            gson {
                // Needs to be parsed to string because JSON can't handle all Long values and truncates some
                // e.g: in memory 2499283573021220255 => 2499283573021220400 in JSON
                setLongSerializationPolicy(LongSerializationPolicy.STRING)

                registerTypeAdapter(Page::class.java, PageAdapter())
            }
        }
        install(CallLogging) {
            level = Level.INFO
        }

        routing(logger)
    }

    server.start(wait = false)


}
