package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.AddColumnMigration

// SHA-256 fingerprint (hex) of the signature the extension was installed/updated with.
// NULL until the extension is installed with a verifiable signature (TOFU on first install).
@Suppress("ClassName", "unused")
class M0068_AddSignatureHashToExtensionTable :
    AddColumnMigration(
        "extension",
        "signature_hash",
        "VARCHAR(64)",
        "NULL",
    )
