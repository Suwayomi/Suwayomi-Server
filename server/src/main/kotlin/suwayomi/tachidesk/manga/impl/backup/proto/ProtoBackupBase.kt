package suwayomi.tachidesk.manga.impl.backup.proto

import kotlinx.serialization.protobuf.ProtoBuf

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

open class ProtoBackupBase {
    var sourceMapping: Map<Long, String> = emptyMap()

    val parser = ProtoBuf
}