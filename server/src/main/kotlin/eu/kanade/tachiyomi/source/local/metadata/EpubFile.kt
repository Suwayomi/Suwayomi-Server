package eu.kanade.tachiyomi.source.local.metadata

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.storage.EpubFile
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

/**
 * Fills manga metadata using this epub file's metadata.
 */
fun EpubFile.fillMangaMetadata(manga: SManga) {
    val ref = getPackageHref()
    val doc = getPackageDocument(ref)

    val creator = doc.getElementsByTag("dc:creator").first()
    val description = doc.getElementsByTag("dc:description").first()

    manga.author = creator?.text()
    manga.description = description?.text()
}

/**
 * Fills chapter metadata using this epub file's metadata.
 */
fun EpubFile.fillChapterMetadata(chapter: SChapter) {
    val ref = getPackageHref()
    val doc = getPackageDocument(ref)

    val title = doc.getElementsByTag("dc:title").first()
    val publisher = doc.getElementsByTag("dc:publisher").first()
    val creator = doc.getElementsByTag("dc:creator").first()
    var date = doc.getElementsByTag("dc:date").first()
    if (date == null) {
        date = doc.select("meta[property=dcterms:modified]").first()
    }

    if (title != null) {
        chapter.name = title.text()
    }

    if (publisher != null) {
        chapter.scanlator = publisher.text()
    } else if (creator != null) {
        chapter.scanlator = creator.text()
    }

    if (date != null) {
        parseEpubDate(date.text())?.let { chapter.date_upload = it }
    }
}

private val legacyEpubDateFormat =
    // 'Z' offsets like "+0000" written without a colon; Instant/OffsetDateTime don't accept these
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

/**
 * Parses a `dc:date`/modified timestamp. Epubs carry these in several shapes:
 * full instants (`2020-01-01T00:00:00Z`), offset times (`+01:00`), colon-less offsets
 * (`+0000`) and date-only values (`2020-01-01`). All are normalized to UTC epoch millis.
 */
private fun parseEpubDate(text: String): Long? =
    runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching {
            LocalDate
                .parse(text)
                .atTime(LocalTime.MIDNIGHT)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }.getOrNull()
        ?: runCatching { synchronized(legacyEpubDateFormat) { legacyEpubDateFormat.parse(text)?.time } }.getOrNull()
