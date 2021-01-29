package ir.armor.tachidesk

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import net.harawata.appdirs.AppDirsFactory

object Config {
    val dataRoot = AppDirsFactory.getInstance().getUserDataDir("Tachidesk", null, null)
    val extensionsRoot = "$dataRoot/extensions"
    val thumbnailsRoot = "$dataRoot/thumbnails"
}
