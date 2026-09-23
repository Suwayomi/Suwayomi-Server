package suwayomi.tachidesk.manga.impl.text

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jsoup.Jsoup
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

object ChapterTextSanitizer {
    private val safelist: Safelist =
        Safelist
            .relaxed()
            .addTags(
                "figure",
                "figcaption",
                "ruby",
                "rt",
                "rp",
                "hr",
                "section",
                "article",
                "header",
                "footer",
                "main",
            ).addAttributes(":all", "class", "id", "title", "style")
            .addAttributes("img", "src", "alt", "title", "width", "height", "loading")
            .addAttributes("a", "href", "title", "target", "rel")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https", "data")

    fun sanitize(
        rawHtml: String,
        baseUrl: String? = null,
    ): String {
        val baseUri = baseUrl.orEmpty()
        val doc = Jsoup.parse(rawHtml, baseUri)

        doc.select("[style]").forEach { element ->
            val style =
                element
                    .attr("style")
                    .split(';')
                    .mapNotNull { declaration ->
                        val parts = declaration.split(':', limit = 2)
                        val property = parts.first().trim().lowercase()
                        val value = parts.getOrNull(1)?.trim().orEmpty()
                        if (property in SAFE_STYLE_PROPERTIES && value.matches(SAFE_STYLE_VALUE)) "$property:$value" else null
                    }.joinToString(";")
            if (style.isEmpty()) element.removeAttr("style") else element.attr("style", style)
        }

        doc.select("img").forEach { img ->
            val src = img.attr("src")
            val lazySrc =
                img.attr("data-src").ifBlank {
                    img.attr("data-lazy-src").ifBlank {
                        img.attr("data-original")
                    }
                }
            if ((src.isBlank() || src.startsWith("data:image/gif;base64,R0lGODlhAQABA")) && lazySrc.isNotBlank()) {
                img.attr("src", lazySrc)
            }
            if (baseUri.isNotBlank()) {
                val absSrc = img.attr("abs:src")
                if (absSrc.isNotBlank()) {
                    img.attr("src", absSrc)
                }
            }
        }

        if (baseUri.isNotBlank()) {
            doc.select("a[href]").forEach { a ->
                val absHref = a.attr("abs:href")
                if (absHref.isNotBlank()) {
                    a.attr("href", absHref)
                }
            }
        }

        val cleanDoc = Cleaner(safelist).clean(doc)
        cleanDoc.select("a").forEach { a ->
            a.attr("target", "_blank")
            a.attr("rel", "noopener noreferrer")
        }

        return cleanDoc.body().html()
    }

    private val SAFE_STYLE_PROPERTIES =
        setOf(
            "color",
            "display",
            "font-size",
            "font-weight",
            "letter-spacing",
            "margin",
            "margin-bottom",
            "opacity",
            "text-align",
            "text-transform",
        )
    private val SAFE_STYLE_VALUE = Regex("[#A-Za-z0-9.,%\\s-]+")
}
