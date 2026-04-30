package suwayomi.tachidesk.manga.impl.email

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

object EmailPresets {
    data class Preset(
        val id: String,
        val displayName: String,
        val host: String,
        val port: Int,
        val useStartTls: Boolean,
    )

    val presets: List<Preset> =
        listOf(
            Preset("GMAIL", "Gmail", "smtp.gmail.com", 587, true),
            Preset("OUTLOOK", "Outlook / Hotmail", "smtp-mail.outlook.com", 587, true),
            Preset("YAHOO", "Yahoo", "smtp.mail.yahoo.com", 587, true),
            Preset("ICLOUD", "iCloud", "smtp.mail.me.com", 587, true),
        )

    fun byId(id: String): Preset? = presets.firstOrNull { it.id == id }
}
