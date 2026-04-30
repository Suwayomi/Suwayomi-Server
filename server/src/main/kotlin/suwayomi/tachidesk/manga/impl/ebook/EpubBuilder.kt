package suwayomi.tachidesk.manga.impl.ebook

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Image-only EPUB 3 builder. Produces an in-memory ByteArray of an EPUB
 * file containing one HTML page per image of a single chapter, suitable
 * for sending to a Kindle / Kobo over email or downloading to a device.
 *
 * The EPUB is built directly with java.util.zip — no third-party epub
 * libraries — to keep the dependency surface flat. RTL is exposed as a
 * `<spine page-progression-direction>` attribute so the reader paginates
 * the right way for manga.
 */
object EpubBuilder {
    private val logger = KotlinLogging.logger {}

    data class Page(
        val bytes: ByteArray,
        val mime: String,
        /** filename within the EPUB (e.g. `001.jpg`). */
        val name: String,
    )

    /**
     * Reads all the images of [chapterId] from the existing chapter
     * download (folder or CBZ — both providers expose a uniform getImage).
     */
    fun pagesFromChapter(
        mangaId: Int,
        chapterId: Int,
    ): List<Page> {
        val count = ChapterDownloadHelper.getImageCount(mangaId, chapterId)
        require(count > 0) { "Chapter has no downloaded pages (download to server first)" }
        val out = mutableListOf<Page>()
        for (i in 0 until count) {
            val (stream, mime) = ChapterDownloadHelper.getImage(mangaId, chapterId, i)
            stream.use {
                val bytes = it.readAllBytes()
                val ext = extFor(mime)
                val name = "%03d.%s".format(i + 1, ext)
                out += Page(bytes, mime, name)
            }
        }
        return out
    }

    /**
     * Build an EPUB byte stream from raw pages. [bookTitle] becomes the
     * dc:title; [author] becomes dc:creator. [rtl] flips
     * page-progression-direction so manga reads right-to-left.
     */
    fun build(
        bookTitle: String,
        author: String?,
        pages: List<Page>,
        rtl: Boolean = true,
        language: String = "en",
    ): ByteArray {
        require(pages.isNotEmpty()) { "EPUB requires at least one page" }
        val identifier = "urn:uuid:" + UUID.randomUUID()
        val baos = ByteArrayOutputStream(pages.sumOf { it.bytes.size } + 16 * 1024)
        ZipOutputStream(baos).use { zip ->
            // EPUB requires the mimetype entry to be first AND stored uncompressed.
            writeMimetype(zip)
            writeStored(zip, "META-INF/container.xml", containerXml().toByteArray(Charsets.UTF_8))
            writeStored(
                zip,
                "OEBPS/content.opf",
                contentOpf(identifier, bookTitle, author, pages, rtl, language).toByteArray(Charsets.UTF_8),
            )
            writeStored(zip, "OEBPS/nav.xhtml", navXhtml(bookTitle, pages).toByteArray(Charsets.UTF_8))
            writeStored(zip, "OEBPS/style.css", STYLE_CSS.toByteArray(Charsets.UTF_8))
            for ((index, page) in pages.withIndex()) {
                val pageHtml = pageXhtml(bookTitle, page.name)
                writeStored(zip, "OEBPS/p%03d.xhtml".format(index + 1), pageHtml.toByteArray(Charsets.UTF_8))
                writeStored(zip, "OEBPS/img/${page.name}", page.bytes)
            }
        }
        logger.debug {
            "Built EPUB title='$bookTitle' pages=${pages.size} size=${baos.size()} bytes (rtl=$rtl)"
        }
        return baos.toByteArray()
    }

    // ---- helpers ----

    private fun writeMimetype(zip: ZipOutputStream) {
        val entry = ZipEntry("mimetype").apply { method = ZipEntry.STORED }
        val data = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        entry.size = data.size.toLong()
        entry.compressedSize = data.size.toLong()
        val crc = CRC32().apply { update(data) }
        entry.crc = crc.value
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }

    private fun writeStored(
        zip: ZipOutputStream,
        path: String,
        data: ByteArray,
    ) {
        val entry = ZipEntry(path).apply { method = ZipEntry.DEFLATED }
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }

    private fun extFor(mime: String): String =
        when (mime.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/avif" -> "avif"
            else -> "bin"
        }

    private fun escape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun containerXml(): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
    <rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
    </rootfiles>
</container>
"""

    private fun contentOpf(
        identifier: String,
        title: String,
        author: String?,
        pages: List<Page>,
        rtl: Boolean,
        language: String,
    ): String {
        val direction = if (rtl) " page-progression-direction=\"rtl\"" else ""
        val authorTag =
            author?.takeIf { it.isNotBlank() }?.let {
                "        <dc:creator>${escape(it)}</dc:creator>\n"
            } ?: ""
        val manifestPages =
            pages.indices.joinToString("\n") { idx ->
                val page = pages[idx]
                val pageId = "p%03d".format(idx + 1)
                val imgId = "img%03d".format(idx + 1)
                val mime = page.mime
                """        <item id="$pageId" href="p%03d.xhtml" media-type="application/xhtml+xml"/>
        <item id="$imgId" href="img/${page.name}" media-type="$mime"/>""".format(idx + 1)
            }
        val spineItems =
            pages.indices.joinToString("\n") { idx ->
                "        <itemref idref=\"p%03d\"/>".format(idx + 1)
            }
        return """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="$language" prefix="rendition: http://www.idpf.org/vocab/rendition/#">
    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
        <dc:identifier id="bookid">$identifier</dc:identifier>
        <dc:title>${escape(title)}</dc:title>
        <dc:language>$language</dc:language>
$authorTag        <meta property="rendition:layout">pre-paginated</meta>
        <meta property="rendition:orientation">portrait</meta>
        <meta property="rendition:spread">none</meta>
        <meta property="dcterms:modified">${java.time.Instant.now().toString().substringBefore('.')}Z</meta>
    </metadata>
    <manifest>
        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
        <item id="css" href="style.css" media-type="text/css"/>
$manifestPages
    </manifest>
    <spine$direction>
$spineItems
    </spine>
</package>
"""
    }

    private fun navXhtml(
        title: String,
        pages: List<Page>,
    ): String {
        val items =
            pages.indices.joinToString("\n") { idx ->
                "                <li><a href=\"p%03d.xhtml\">Page ${idx + 1}</a></li>".format(idx + 1)
            }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
    <title>${escape(title)}</title>
    <meta charset="utf-8"/>
</head>
<body>
    <nav epub:type="toc" id="toc">
        <h1>${escape(title)}</h1>
        <ol>
$items
        </ol>
    </nav>
</body>
</html>
"""
    }

    private fun pageXhtml(
        title: String,
        imageName: String,
    ): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <title>${escape(title)}</title>
    <meta charset="utf-8"/>
    <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
    <div class="page"><img src="img/${escape(imageName)}" alt=""/></div>
</body>
</html>
"""

    private val STYLE_CSS =
        """
        html, body { margin: 0; padding: 0; }
        body { background: #000; }
        .page { display: flex; align-items: center; justify-content: center; min-height: 100vh; }
        img { max-width: 100%; max-height: 100vh; height: auto; width: auto; display: block; }
        """.trimIndent()
}
