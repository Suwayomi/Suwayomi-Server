package suwayomi.tachidesk.model.database.migration.lib

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

// originally licenced under MIT by Andreas Mausch, Changes are licenced under Mozilla Public License, v. 2.0.
// adopted from: https://gitlab.com/andreas-mausch/exposed-migrations/-/tree/4bf853c18a24d0170eda896ddbb899cb01233595

abstract class Migration {
    val name: String
    val version: Int

    init {
        val groups = Regex("^M(\\d+)_(.*)$").matchEntire(this::class.simpleName!!)?.groupValues
            ?: throw IllegalArgumentException("Migration class name doesn't match convention")
        version = groups[1].toInt()
        name = groups[2]
    }

    abstract fun run()
}
