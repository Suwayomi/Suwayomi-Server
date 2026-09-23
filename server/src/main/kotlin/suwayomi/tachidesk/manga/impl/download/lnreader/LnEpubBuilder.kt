package suwayomi.tachidesk.manga.impl.download.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class LnEpubAsset(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is LnEpubAsset && name == other.name && mimeType == other.mimeType && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * (31 * name.hashCode() + mimeType.hashCode()) + bytes.contentHashCode()
}

object LnEpubBuilder {
    private const val MIMETYPE_STRING = "application/epub+zip"

    // A fixed UTC date keeps the KOReader hash stable when rebuilding unchanged content.
    private const val MODIFIED_TIMESTAMP = "1970-01-01T00:00:00Z"
    private val MIMETYPE_BYTES = MIMETYPE_STRING.toByteArray(Charsets.US_ASCII)
    private val LANGUAGE_TAG = Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")

    fun build(
        file: File,
        mangaTitle: String,
        chapterTitle: String,
        mangaId: Int,
        chapterId: Int,
        xhtmlBody: String,
        assets: List<LnEpubAsset> = emptyList(),
        language: String = "und",
        customCss: String? = null,
        customJs: String? = null,
    ) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).buffered().use { fos ->
            build(
                fos,
                mangaTitle,
                chapterTitle,
                mangaId,
                chapterId,
                xhtmlBody,
                assets,
                language,
                customCss,
                customJs,
            )
        }
    }

    fun build(
        outputStream: OutputStream,
        mangaTitle: String,
        chapterTitle: String,
        mangaId: Int,
        chapterId: Int,
        xhtmlBody: String,
        assets: List<LnEpubAsset> = emptyList(),
        language: String = "und",
        customCss: String? = null,
        customJs: String? = null,
    ) {
        val languageTag = language.takeIf(LANGUAGE_TAG::matches) ?: "und"
        val sortedAssets = assets.sortedBy { it.name }

        ZipOutputStream(outputStream).use { zipOut ->
            zipOut.setLevel(Deflater.DEFAULT_COMPRESSION)

            // 1. mimetype (first, STORED, 0L timestamp, no extra data)
            val mimetypeEntry =
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = MIMETYPE_BYTES.size.toLong()
                    compressedSize = MIMETYPE_BYTES.size.toLong()
                    val crc = CRC32()
                    crc.update(MIMETYPE_BYTES)
                    this.crc = crc.value
                    time = 0L
                    extra = null
                }
            zipOut.putNextEntry(mimetypeEntry)
            zipOut.write(MIMETYPE_BYTES)
            zipOut.closeEntry()

            // 2. META-INF/container.xml
            writeDeflatedEntry(zipOut, "META-INF/container.xml", buildContainerXml().toByteArray(Charsets.UTF_8))

            // 3. OEBPS/package.opf
            val opfContent =
                buildPackageOpf(
                    mangaTitle,
                    chapterTitle,
                    mangaId,
                    chapterId,
                    sortedAssets,
                    languageTag,
                    customCss != null,
                    customJs != null,
                )
            writeDeflatedEntry(zipOut, "OEBPS/package.opf", opfContent.toByteArray(Charsets.UTF_8))

            // 4. OEBPS/nav.xhtml
            val navContent = buildNavXhtml(chapterTitle, languageTag)
            writeDeflatedEntry(zipOut, "OEBPS/nav.xhtml", navContent.toByteArray(Charsets.UTF_8))

            // 5. OEBPS/chapter.xhtml
            val chapterContent = buildChapterXhtml(chapterTitle, xhtmlBody, languageTag, customCss != null)
            writeDeflatedEntry(zipOut, "OEBPS/chapter.xhtml", chapterContent.toByteArray(Charsets.UTF_8))

            customCss?.let { writeDeflatedEntry(zipOut, "OEBPS/custom.css", it.toByteArray(Charsets.UTF_8)) }
            // Preserve source JS as inert metadata; it is never referenced by the EPUB XHTML.
            customJs?.let { writeDeflatedEntry(zipOut, "OEBPS/custom-js.txt", it.toByteArray(Charsets.UTF_8)) }

            // 6. Assets (sorted alphabetically)
            for (asset in sortedAssets) {
                writeDeflatedEntry(zipOut, "OEBPS/assets/${asset.name}", asset.bytes)
            }
        }
    }

    private fun writeDeflatedEntry(
        zipOut: ZipOutputStream,
        name: String,
        bytes: ByteArray,
    ) {
        val entry =
            ZipEntry(name).apply {
                method = ZipEntry.DEFLATED
                time = 0L
                extra = null
            }
        zipOut.putNextEntry(entry)
        zipOut.write(bytes)
        zipOut.closeEntry()
    }

    private fun buildContainerXml(): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

    private fun buildPackageOpf(
        mangaTitle: String,
        chapterTitle: String,
        mangaId: Int,
        chapterId: Int,
        assets: List<LnEpubAsset>,
        language: String,
        hasCustomCss: Boolean,
        hasCustomJs: Boolean,
    ): String {
        val manifestAssets =
            assets
                .mapIndexed { index, asset ->
                    "\n    <item id=\"asset_${index + 1}\" href=\"assets/${escapeXml(
                        asset.name,
                    )}\" media-type=\"${escapeXml(asset.mimeType)}\"/>"
                }.joinToString("")
        val sourceStylesheet =
            if (hasCustomCss) "\n    <item id=\"custom-css\" href=\"custom.css\" media-type=\"text/css\"/>" else ""
        val sourceScriptText =
            if (hasCustomJs) "\n    <item id=\"custom-js-text\" href=\"custom-js.txt\" media-type=\"text/plain\"/>" else ""

        return """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="pub-id">urn:suwayomi:manga:$mangaId:chapter:$chapterId</dc:identifier>
    <dc:title>${escapeXml(chapterTitle)}</dc:title>
    <dc:source>${escapeXml(mangaTitle)}</dc:source>
    <dc:language>${escapeXml(language)}</dc:language>
    <meta property="dcterms:modified">$MODIFIED_TIMESTAMP</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>$sourceStylesheet$sourceScriptText$manifestAssets
  </manifest>
  <spine>
    <itemref idref="chapter"/>
  </spine>
</package>"""
    }

    private fun buildNavXhtml(
        chapterTitle: String,
        language: String,
    ): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="${escapeXml(
            language,
        )}" xml:lang="${escapeXml(language)}">
<head>
  <meta charset="utf-8"/>
  <title>Navigation</title>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>Table of Contents</h1>
    <ol>
      <li><a href="chapter.xhtml">${escapeXml(chapterTitle)}</a></li>
    </ol>
  </nav>
</body>
</html>"""

    private fun buildChapterXhtml(
        chapterTitle: String,
        xhtmlBody: String,
        language: String,
        hasCustomCss: Boolean,
    ): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="${escapeXml(language)}" xml:lang="${escapeXml(language)}">
<head>
  <meta charset="utf-8"/>
  <title>${escapeXml(chapterTitle)}</title>
  <style>
    body { font-family: sans-serif; line-height: 1.6; margin: 1em; }
    img { max-width: 100%; height: auto; }
  </style>
  ${if (hasCustomCss) "<link rel=\"stylesheet\" type=\"text/css\" href=\"custom.css\"/>" else ""}
</head>
<body>
$xhtmlBody
</body>
</html>"""

    fun escapeXml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
