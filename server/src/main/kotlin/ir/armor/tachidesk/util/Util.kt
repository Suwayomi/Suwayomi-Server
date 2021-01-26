package ir.armor.tachidesk.util

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ir.armor.tachidesk.Config
import ir.armor.tachidesk.database.makeDataBaseTables
import java.io.File

fun applicationSetup() {
    // make dirs we need
    File(Config.dataRoot).mkdirs()
    File(Config.extensionsRoot).mkdirs()

    makeDataBaseTables()
}
