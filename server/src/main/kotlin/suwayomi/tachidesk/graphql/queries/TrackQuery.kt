package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.types.TrackServiceType
import suwayomi.tachidesk.server.trackManager

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

class TrackQuery {
    fun trackService(id: Long): TrackServiceType? =
        trackManager.services.find { it.id == id }?.let {
            TrackServiceType(it)
        }

    fun trackServices(): List<TrackServiceType> = trackManager.services.map {
        TrackServiceType(it)
    }
}
