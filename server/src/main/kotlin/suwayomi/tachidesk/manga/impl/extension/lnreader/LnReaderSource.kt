package suwayomi.tachidesk.manga.impl.extension.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import suwayomi.tachidesk.manga.impl.text.ChapterTextSource
import suwayomi.tachidesk.manga.impl.text.RawChapterText
import suwayomi.tachidesk.server.serverConfig
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale
import kotlin.io.path.exists
import kotlin.io.path.readText

internal data class LnWebStorageBinding(
    val pluginId: String,
    val origin: String,
)

class LnReaderSource(
    val installedPlugin: LnReaderPluginStore.InstalledPlugin,
    private val runtimeProvider: (() -> LnPluginRuntime)? = null,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
) : HttpSource(),
    ConfigurableSource,
    ChapterTextSource,
    AutoCloseable {
    override val id: Long = LnReaderRepository.sourceId(installedPlugin.manifest.plugin.id)
    override val name: String = installedPlugin.manifest.plugin.name
    override val baseUrl: String = installedPlugin.manifest.plugin.site
    override val lang: String = LnReaderRepository.normalizeLanguage(installedPlugin.manifest.plugin.lang)
    override val supportsLatest: Boolean = true

    suspend fun fetchImage(request: Request): Response =
        LnNetworkGateway.fetchImage(
            client,
            request,
            LnNetworkPolicy { LnNetworkPolicy.parseOrigins(serverConfig.lnReaderAllowedLocalOrigins.value) },
        )

    private var runtimeInstance: LnPluginRuntime? = null

    @Synchronized
    internal fun getOrInitRuntime(): LnPluginRuntime =
        runtimeInstance?.takeUnless { it.isClosed } ?: (
            runtimeProvider?.invoke()
                ?: LnPluginRuntime(
                    installedPlugin.manifest.plugin.id,
                    installedPlugin.indexJs,
                    webStorageOrigin = LnReaderRepository.webStorageOrigin(baseUrl),
                )
        ).also { runtimeInstance = it }

    internal fun webStorageBindingFor(url: String): LnWebStorageBinding? {
        val origin = LnReaderRepository.webStorageOrigin(baseUrl) ?: return null
        if (LnReaderRepository.webStorageOrigin(url) != origin) return null
        if (!runCatching { getOrInitRuntime().webStorageUtilized }.getOrDefault(false)) return null
        return LnWebStorageBinding(installedPlugin.manifest.plugin.id, origin)
    }

    override fun headersBuilder(): Headers.Builder {
        val builder = super.headersBuilder()
        runCatching {
            val initJson = getOrInitRuntime().call("imageRequestInit", "{}")
            if (initJson.isNotBlank() && initJson != "null") {
                json.parseToJsonElement(initJson).jsonObject["headers"]?.jsonObject?.forEach { (k, v) ->
                    builder.set(k, v.jsonPrimitive.content)
                }
            }
        }
        return builder
    }

    private inline fun <reified T> callGuest(
        operation: String,
        builder: JsonObjectBuilder.() -> Unit = {},
    ): T {
        val request = json.encodeToString(buildJsonObject(builder))
        val resultJson = getOrInitRuntime().call(operation, request)
        return json.decodeFromString(resultJson)
    }

    private suspend fun queryNovels(
        operation: String,
        page: Int,
        showLatest: Boolean = false,
        filters: FilterList = FilterList(),
    ): MangasPage {
        val novels: List<NovelItemDto> =
            callGuest(operation) {
                put("page", page)
                putJsonObject("options") {
                    put("showLatestNovels", showLatest)
                    put("filters", mapFiltersToGuest(filters))
                }
            }
        return MangasPage(novels.map(::toSManga), hasNextPage = novels.isNotEmpty())
    }

    override suspend fun getPopularManga(page: Int): MangasPage = queryNovels("popularNovels", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = queryNovels("latestNovels", page, showLatest = true)

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage =
        if (query.isNotBlank()) {
            val novels: List<NovelItemDto> =
                callGuest("searchNovels") {
                    put("term", query)
                    put("page", page)
                }
            MangasPage(novels.map(::toSManga), hasNextPage = novels.isNotEmpty())
        } else {
            queryNovels("popularNovels", page, filters = filters)
        }

    private fun fetchSourceNovel(novelPath: String): SourceNovelDto = callGuest("parseNovel") { put("path", novelPath) }

    suspend fun getMangaDetails(manga: SManga): SManga = manga.applyNovelDetails(fetchSourceNovel(manga.url))

    suspend fun getChapterList(manga: SManga): List<SChapter> = fetchChapters(manga.url)

    private suspend fun fetchChapters(
        novelPath: String,
        retryOnPaginationChange: Boolean = true,
    ): List<SChapter> {
        val novel = fetchSourceNovel(novelPath)
        val totalPages = novel.totalPages ?: 0
        if (totalPages > MAX_TOTAL_PAGES) {
            throw IllegalStateException("Total pages $totalPages exceeds limit $MAX_TOTAL_PAGES")
        }
        if (totalPages < 0) {
            throw IllegalStateException("Total pages $totalPages cannot be negative")
        }

        val hasParsePage = getOrInitRuntime().hasParsePage
        if (totalPages > 1 && !hasParsePage) {
            throw IllegalStateException("LNReader source reports $totalPages pages but does not implement parsePage")
        }
        val initialChapters = novel.chapters.orEmpty()
        val isPatternB = hasParsePage && totalPages >= 1 && initialChapters.isEmpty()
        val page1Chapters = if (isPatternB) fetchPageChapters(novelPath, 1) else initialChapters

        val page1Limit = if (totalPages > 1 || isPatternB) MAX_CHAPTERS_PER_PAGE else MAX_TOTAL_CHAPTERS
        if (page1Chapters.size > page1Limit) {
            val desc = if (totalPages > 1 || isPatternB) "Page 1 chapter count" else "Total chapters"
            throw IllegalStateException("$desc (${page1Chapters.size}) exceeds limit $page1Limit")
        }

        val allRawChapters = ArrayList<ChapterItemDto>(page1Chapters.size)
        allRawChapters.addAll(page1Chapters)

        if (hasParsePage && totalPages > 1) {
            for (page in 2..totalPages) {
                val pageChapters = fetchPageChapters(novelPath, page)
                allRawChapters.addAll(pageChapters)
                if (allRawChapters.size > MAX_TOTAL_CHAPTERS) {
                    throw IllegalStateException("Total chapters exceeds limit $MAX_TOTAL_CHAPTERS")
                }
            }
        }

        if (allRawChapters.size > MAX_TOTAL_CHAPTERS) {
            throw IllegalStateException("Total chapters (${allRawChapters.size}) exceeds limit $MAX_TOTAL_CHAPTERS")
        }

        if (hasParsePage && totalPages > 1) {
            val currentNovel = fetchSourceNovel(novelPath)
            val currentPage1 = if (isPatternB) fetchPageChapters(novelPath, 1) else currentNovel.chapters.orEmpty()
            if ((currentNovel.totalPages ?: 0) != totalPages || currentPage1 != page1Chapters) {
                if (retryOnPaginationChange) return fetchChapters(novelPath, retryOnPaginationChange = false)
                throw IllegalStateException("LNReader pagination changed while fetching chapters")
            }
        }

        val deduplicated = allRawChapters.distinctBy { it.path }
        return deduplicated.mapIndexed { index, item -> item.toSChapter((index + 1).toFloat()) }.reversed()
    }

    private suspend fun fetchPageChapters(
        novelPath: String,
        page: Int,
    ): List<ChapterItemDto> {
        val resultJson =
            getOrInitRuntime().call(
                "parsePage",
                json.encodeToString(
                    buildJsonObject {
                        put("path", novelPath)
                        put("page", page.toString())
                    },
                ),
            )
        val chapters = parsePageResult(resultJson)
        if (chapters.size > MAX_CHAPTERS_PER_PAGE) {
            throw IllegalStateException("Page $page chapter count (${chapters.size}) exceeds limit $MAX_CHAPTERS_PER_PAGE")
        }
        return chapters
    }

    private fun parsePageResult(resultJson: String): List<ChapterItemDto> {
        val element = json.parseToJsonElement(resultJson)
        val array =
            when (element) {
                is JsonArray -> element
                is JsonObject -> element["chapters"] as? JsonArray
                else -> null
            } ?: throw IllegalStateException("LNReader parsePage did not return a chapter array")
        return json.decodeFromJsonElement(array)
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) getMangaDetails(manga) else manga
        val updatedChapters = if (fetchChapters) getChapterList(manga) else chapters
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        Observable.fromCallable {
            runBlocking { getMangaDetails(manga) }
        }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        Observable.fromCallable {
            runBlocking { getChapterList(manga) }
        }

    override suspend fun getChapterText(chapter: SChapter): RawChapterText {
        val resultJson =
            getOrInitRuntime().call(
                "parseChapter",
                json.encodeToString(
                    buildJsonObject {
                        put("path", chapter.url)
                    },
                ),
            )
        val text = json.parseToJsonElement(resultJson).jsonPrimitive.content
        val customJs = installedPlugin.customJs?.takeIf { it.exists() }?.readText(Charsets.UTF_8)
        val customCss = installedPlugin.customCss?.takeIf { it.exists() }?.readText(Charsets.UTF_8)
        return RawChapterText(text = text, customJs = customJs, customCss = customCss)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        throw UnsupportedOperationException("LNReader sources provide chapter text via ChapterTextSource, not image pages")

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    public override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("LNReader sources provide chapter text, not image pages")

    private fun resolveUrl(
        path: String,
        isNovel: Boolean,
    ): String =
        runCatching {
            val resultJson =
                getOrInitRuntime().call(
                    "resolveUrl",
                    json.encodeToString(
                        buildJsonObject {
                            put("path", path)
                            put("isNovel", isNovel)
                        },
                    ),
                )
            if (resultJson == "null") resolveDefaultUrl(path) else json.parseToJsonElement(resultJson).jsonPrimitive.content
        }.getOrElse {
            resolveDefaultUrl(path)
        }

    override fun getMangaUrl(manga: SManga): String = resolveUrl(manga.url, isNovel = true)

    override fun getChapterUrl(chapter: SChapter): String = resolveUrl(chapter.url, isNovel = false)

    private fun resolveDefaultUrl(path: String): String =
        if (path.startsWith("//")) {
            "https:$path"
        } else if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            "${baseUrl.removeSuffix("/")}/${path.removePrefix("/")}"
        }

    override fun getFilterList(): FilterList {
        val filtersJson = runCatching { getOrInitRuntime().call("filters", "{}") }.getOrNull() ?: return FilterList()
        if (filtersJson == "null" || filtersJson.isBlank()) return FilterList()
        val element = json.parseToJsonElement(filtersJson).jsonObject
        val filters =
            element.mapNotNull { (key, filterElem) ->
                val filterObj = filterElem.jsonObject
                val filterName = filterObj["label"]?.jsonPrimitive?.content ?: key
                when (filterObj["type"]?.jsonPrimitive?.content) {
                    "Text" -> {
                        LnTextFilter(key, filterName, filterObj["value"]?.jsonPrimitive?.content.orEmpty())
                    }

                    "Picker" -> {
                        val options = filterObj.extractOptions()
                        val selectedValue = filterObj["value"]?.jsonPrimitive?.content.orEmpty()
                        val selectedIndex = options.indexOfFirst { it.second == selectedValue }.coerceAtLeast(0)
                        LnSelectFilter(
                            key = key,
                            name = filterName,
                            optionValues = options.map { it.second },
                            displayLabels = options.map { it.first }.toTypedArray(),
                            state = selectedIndex,
                        )
                    }

                    "Switch" -> {
                        LnSwitchFilter(key, filterName, filterObj["value"]?.jsonPrimitive?.booleanOrNull ?: false)
                    }

                    "Checkbox" -> {
                        val selected =
                            filterObj["value"]
                                ?.jsonArray
                                .orEmpty()
                                .map { it.jsonPrimitive.content }
                                .toSet()
                        val items =
                            filterObj.extractOptions().map { (label, value) ->
                                LnCheckboxItem(value, label, value in selected)
                            }
                        LnCheckboxGroup(key, filterName, items)
                    }

                    "XCheckbox" -> {
                        val valueObj = filterObj["value"]?.jsonObject
                        val includes =
                            valueObj
                                ?.get("include")
                                ?.jsonArray
                                .orEmpty()
                                .map { it.jsonPrimitive.content }
                                .toSet()
                        val excludes =
                            valueObj
                                ?.get("exclude")
                                ?.jsonArray
                                .orEmpty()
                                .map { it.jsonPrimitive.content }
                                .toSet()
                        val items =
                            filterObj.extractOptions().map { (label, value) ->
                                val state =
                                    when {
                                        value in includes -> Filter.TriState.STATE_INCLUDE
                                        value in excludes -> Filter.TriState.STATE_EXCLUDE
                                        else -> Filter.TriState.STATE_IGNORE
                                    }
                                LnTriStateItem(value, label, state)
                            }
                        LnTriStateGroup(key, filterName, items)
                    }

                    else -> {
                        null
                    }
                }
            }
        return FilterList(filters)
    }

    val isConfigurable: Boolean
        get() =
            installedPlugin.manifest.plugin.hasSettings ?: runCatching {
                val settingsJson = getOrInitRuntime().call("pluginSettings", "{}")
                settingsJson != "null" && settingsJson.isNotBlank() && settingsJson != "{}"
            }.getOrDefault(false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val settingsJson = runCatching { getOrInitRuntime().call("pluginSettings", "{}") }.getOrNull() ?: return
        if (settingsJson == "null" || settingsJson.isBlank()) return
        val element = json.parseToJsonElement(settingsJson).jsonObject
        for ((settingKey, settingElem) in element) {
            val settingObj = settingElem.jsonObject
            val label = settingObj["label"]?.jsonPrimitive?.content ?: settingKey
            val summaryText =
                settingObj["description"]?.jsonPrimitive?.contentOrNull
                    ?: settingObj["summary"]?.jsonPrimitive?.contentOrNull
            val pref =
                when (settingObj["type"]?.jsonPrimitive?.content ?: "Text") {
                    "Text" -> {
                        EditTextPreference(screen.context).apply {
                            dialogTitle = label
                            setDefaultValue(settingObj["value"]?.jsonPrimitive?.content.orEmpty())
                        }
                    }

                    "Switch" -> {
                        SwitchPreferenceCompat(screen.context).apply {
                            setDefaultValue(settingObj["value"]?.jsonPrimitive?.booleanOrNull ?: false)
                        }
                    }

                    "Select" -> {
                        val options = settingObj.extractOptions()
                        ListPreference(screen.context).apply {
                            entries = options.map { it.first }.toTypedArray()
                            entryValues = options.map { it.second }.toTypedArray()
                            setDefaultValue(settingObj["value"]?.jsonPrimitive?.content ?: options.firstOrNull()?.second.orEmpty())
                        }
                    }

                    "CheckboxGroup" -> {
                        val options = settingObj.extractOptions()
                        MultiSelectListPreference(screen.context).apply {
                            entries = options.map { it.first }.toTypedArray()
                            entryValues = options.map { it.second }.toTypedArray()
                            setDefaultValue(
                                settingObj["value"]
                                    ?.jsonArray
                                    .orEmpty()
                                    .map { it.jsonPrimitive.content }
                                    .toSet(),
                            )
                        }
                    }

                    else -> {
                        null
                    }
                }
            pref?.apply {
                key = settingKey
                title = label
                if (summaryText != null) {
                    summary = summaryText
                }
                screen.addPreference(this)
            }
        }
    }

    @Synchronized
    override fun close() {
        runtimeInstance?.close()
        runtimeInstance = null
    }

    private fun toSManga(dto: NovelItemDto): SManga =
        SManga.create().apply {
            url = dto.path
            title = dto.name
            thumbnail_url = dto.cover
        }

    private fun mapFiltersToGuest(filters: FilterList): JsonObject {
        val defaultFilters = runCatching { getFilterList() }.getOrNull()?.list.orEmpty()
        val filterMap =
            (defaultFilters + filters)
                .filterIsInstance<LnGuestFilter>()
                .associateBy { it.key }
        return buildJsonObject {
            filterMap.forEach { (key, filter) ->
                put(key, filter.toGuestJson())
            }
        }
    }

    private fun normalizeGenres(genres: String?): String? =
        genres
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.joinToString(", ")
            ?.takeIf(String::isNotBlank)

    private fun mapStatus(status: String?): Int =
        when (status?.trim()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            "Licensed" -> SManga.LICENSED
            "Publishing Finished" -> SManga.PUBLISHING_FINISHED
            "Cancelled" -> SManga.CANCELLED
            "On Hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

    private fun normalizeScanlator(scanlator: JsonElement?): String? =
        when (scanlator) {
            is JsonArray -> scanlator.mapNotNull { it.jsonPrimitive.contentOrNull }.joinToString(", ").takeIf(String::isNotBlank)
            is JsonPrimitive -> scanlator.contentOrNull?.takeIf(String::isNotBlank)
            else -> null
        }

    private fun parseReleaseTime(releaseTime: String?): Long {
        val value = releaseTime?.trim()?.takeIf(String::isNotEmpty) ?: return 0L
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
        RELEASE_DATE_TIME_FORMATTERS
            .firstNotNullOfOrNull { formatter ->
                runCatching { LocalDateTime.parse(value, formatter).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            }?.let { return it }
        RELEASE_DATE_FORMATTERS
            .firstNotNullOfOrNull { formatter ->
                runCatching {
                    LocalDate
                        .parse(value, formatter)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull()
            }?.let { return it }
        return runCatching {
            LocalDate
                .parse(value.take(10))
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun ChapterItemDto.toSChapter(fallbackNumber: Float): SChapter =
        SChapter.create().apply {
            url = path
            name = this@toSChapter.name
            date_upload = parseReleaseTime(releaseTime)
            chapter_number = chapterNumber?.toFloat() ?: fallbackNumber
            scanlator = normalizeScanlator(this@toSChapter.scanlator)
        }

    private fun SManga.applyNovelDetails(novel: SourceNovelDto): SManga =
        apply {
            title = novel.name?.takeIf(String::isNotBlank) ?: title
            thumbnail_url = novel.cover?.takeIf(String::isNotBlank) ?: thumbnail_url
            description = novel.summary
            author = novel.author
            artist = novel.artist
            genre = normalizeGenres(novel.genres)
            status = mapStatus(novel.status)
        }

    private fun JsonObject.extractOptions(): List<Pair<String, String>> =
        this["options"]?.jsonArray.orEmpty().map {
            val opt = it.jsonObject
            opt["label"]?.jsonPrimitive?.content.orEmpty() to opt["value"]?.jsonPrimitive?.content.orEmpty()
        }

    sealed interface LnGuestFilter {
        val key: String

        fun toGuestJson(): JsonObject
    }

    class LnTextFilter(
        override val key: String,
        name: String,
        state: String,
    ) : Filter.Text(name, state),
        LnGuestFilter {
        override fun toGuestJson(): JsonObject =
            buildJsonObject {
                put("type", "Text")
                put("value", state)
            }
    }

    class LnSelectFilter(
        override val key: String,
        name: String,
        val optionValues: List<String>,
        displayLabels: Array<String>,
        state: Int,
    ) : Filter.Select<String>(name, displayLabels, state),
        LnGuestFilter {
        override fun toGuestJson(): JsonObject =
            buildJsonObject {
                put("type", "Picker")
                put("value", optionValues.getOrElse(state) { "" })
            }
    }

    class LnSwitchFilter(
        override val key: String,
        name: String,
        state: Boolean,
    ) : Filter.CheckBox(name, state),
        LnGuestFilter {
        override fun toGuestJson(): JsonObject =
            buildJsonObject {
                put("type", "Switch")
                put("value", state)
            }
    }

    class LnCheckboxItem(
        val value: String,
        name: String,
        state: Boolean,
    ) : Filter.CheckBox(name, state)

    class LnCheckboxGroup(
        override val key: String,
        name: String,
        items: List<LnCheckboxItem>,
    ) : Filter.Group<LnCheckboxItem>(name, items),
        LnGuestFilter {
        override fun toGuestJson(): JsonObject =
            buildJsonObject {
                put("type", "Checkbox")
                put("value", buildJsonArray { state.filter { it.state }.forEach { add(JsonPrimitive(it.value)) } })
            }
    }

    class LnTriStateItem(
        val value: String,
        name: String,
        state: Int,
    ) : Filter.TriState(name, state)

    class LnTriStateGroup(
        override val key: String,
        name: String,
        items: List<LnTriStateItem>,
    ) : Filter.Group<LnTriStateItem>(name, items),
        LnGuestFilter {
        override fun toGuestJson(): JsonObject =
            buildJsonObject {
                put("type", "XCheckbox")
                putJsonObject("value") {
                    put("include", buildJsonArray { state.filter { it.isIncluded() }.forEach { add(JsonPrimitive(it.value)) } })
                    put("exclude", buildJsonArray { state.filter { it.isExcluded() }.forEach { add(JsonPrimitive(it.value)) } })
                }
            }
    }

    companion object {
        const val MAX_TOTAL_PAGES = 500
        const val MAX_CHAPTERS_PER_PAGE = 2000
        const val MAX_TOTAL_CHAPTERS = 50000

        private val RELEASE_DATE_TIME_FORMATTERS =
            listOf(
                englishFormatter("MMMM d, uuuu h:mm a"),
                englishFormatter("EEEE, MMMM d, uuuu h:mm a"),
                englishFormatter("MMM d, uuuu h:mm a"),
                englishFormatter("EEE, MMM d, uuuu h:mm a"),
            )

        private val RELEASE_DATE_FORMATTERS =
            listOf(
                englishFormatter("MM/dd/uuuu"),
                englishFormatter("M/d/uuuu"),
                englishFormatter("MMMM d, uuuu"),
                englishFormatter("MMM d, uuuu"),
            )

        private fun englishFormatter(pattern: String): DateTimeFormatter =
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter(Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.STRICT)
    }
}

@Serializable
private data class NovelItemDto(
    val name: String,
    val path: String,
    val cover: String? = null,
)

@Serializable
private data class ChapterItemDto(
    val name: String,
    val path: String,
    val releaseTime: String? = null,
    val chapterNumber: Double? = null,
    val page: String? = null,
    val scanlator: JsonElement? = null,
)

@Serializable
private data class SourceNovelDto(
    val name: String? = null,
    val path: String? = null,
    val cover: String? = null,
    val genres: String? = null,
    val summary: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    val rating: Double? = null,
    val chapters: List<ChapterItemDto>? = null,
    val totalPages: Int? = null,
)
