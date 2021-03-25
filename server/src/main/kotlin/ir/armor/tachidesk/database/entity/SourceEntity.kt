package ir.armor.tachidesk.database.entity

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ir.armor.tachidesk.database.table.SourceTable
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID

class SourceEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : EntityClass<Long, SourceEntity>(SourceTable, null)

    var sourceId by SourceTable.id
    var name by SourceTable.name
    var lang by SourceTable.lang
    var extension by ExtensionEntity referencedOn SourceTable.extension
    var partOfFactorySource by SourceTable.partOfFactorySource
    var positionInFactorySource by SourceTable.positionInFactorySource
}
