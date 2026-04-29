package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.text.Normalizer

/**
 * Normalizes manga titles for fuzzy matching (duplicate detection).
 *
 * Strategy:
 * - Unicode NFKD normalize (split combining accents).
 * - Strip combining marks (so "café" == "cafe").
 * - Lowercase.
 * - Drop common bracketed annotations like "(uncensored)", "[2024]".
 * - Strip everything that is not a letter or digit.
 *
 * Two titles are considered a likely match when their normalized forms
 * are equal. This is intentionally lossy — callers must treat results
 * as candidates, not authoritative duplicates.
 */
object MangaTitleNormalizer {
    private val bracketed = Regex("[\\[(\\{][^\\])\\}]*[\\])\\}]")
    private val combiningMarks = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val nonAlnum = Regex("[^\\p{L}\\p{Nd}]+")

    fun normalize(title: String?): String {
        if (title.isNullOrBlank()) return ""
        val withoutBrackets = bracketed.replace(title, " ")
        val nfkd = Normalizer.normalize(withoutBrackets, Normalizer.Form.NFKD)
        val withoutMarks = combiningMarks.replace(nfkd, "")
        return nonAlnum.replace(withoutMarks, "").lowercase()
    }
}
