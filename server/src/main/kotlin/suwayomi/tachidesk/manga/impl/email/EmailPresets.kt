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

    // Only Gmail is exposed as a one-click preset. Other providers use
    // 'Custom' to fill host/port manually because their SMTP basic-auth
    // posture varies (Outlook killed it for personal accounts, etc).
    val presets: List<Preset> =
        listOf(
            Preset("GMAIL", "Gmail", "smtp.gmail.com", 587, true),
        )

    fun byId(id: String): Preset? = presets.firstOrNull { it.id == id }
}
