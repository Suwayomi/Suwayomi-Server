package suwayomi.tachidesk.server.database.migration

import suwayomi.tachidesk.server.database.migration.helpers.INITIAL_ORDER_NAME
import suwayomi.tachidesk.server.database.migration.helpers.RenameFieldMigration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

@Suppress("ClassName", "unused")
class M0052_RenameCategoryOrderColumn :
    RenameFieldMigration(
        "Category",
        INITIAL_ORDER_NAME,
        "sort_order",
    )
