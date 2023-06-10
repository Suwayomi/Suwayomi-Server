package suwayomi.tachidesk.graphql.types

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.data.track.TrackService
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo

data class TrackServiceType(
    val id: Long,
    val name: String
) : Node {
    constructor(trackService: TrackService) : this(
        trackService.id,
        trackService.name
    )
}

data class TrackServiceNodeList(
    override val nodes: List<TrackServiceType>,
    override val edges: List<TrackServiceEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int
) : NodeList() {
    data class TrackServiceEdge(
        override val cursor: Cursor,
        override val node: TrackServiceType
    ) : Edge()
}
