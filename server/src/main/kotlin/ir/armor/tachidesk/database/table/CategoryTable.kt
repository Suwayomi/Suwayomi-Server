package ir.armor.tachidesk.database.table

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable

object CategoryTable : IntIdTable() {
    val name = varchar("name", 64)
    val isLanding = bool("is_landing").default(false)
}
