package suwayomi.tachidesk.manga.impl.backup

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

abstract class AbstractBackupValidator {
    data class ValidationResult(val missingSources: List<String>, val missingTrackers: List<String>)
}
