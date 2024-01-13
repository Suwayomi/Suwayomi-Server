package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import java.util.concurrent.CompletableFuture

class TrackerType(
    val id: Int,
    val name: String,
    val icon: String,
    val isLoggedIn: Boolean,
    val authUrl: String?,
) : Node {
    constructor(tracker: Tracker) : this(
        tracker.isLoggedIn,
        tracker,
    )

    constructor(isLoggedIn: Boolean, tracker: Tracker) : this(
        tracker.id,
        tracker.name,
        tracker.getLogo(),
        isLoggedIn,
        if (isLoggedIn) {
            null
        } else {
            tracker.authUrl()
        },
    )

    fun trackRecords(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<TrackRecordNodeList> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, TrackRecordNodeList>("TrackRecordsForTrackerIdDataLoader", id)
    }
}

class TrackRecordType(
    val id: Int,
    val mangaId: Int,
    val syncId: Int,
    val remoteId: Long,
    val libraryId: Long?,
    val title: String,
    val lastChapterRead: Double,
    val totalChapters: Int,
    val status: Int,
    val score: Double,
    val remoteUrl: String,
    val startDate: Long,
    val finishDate: Long,
) : Node {
    constructor(row: ResultRow) : this(
        row[TrackRecordTable.id].value,
        row[TrackRecordTable.mangaId].value,
        row[TrackRecordTable.syncId],
        row[TrackRecordTable.remoteId],
        row[TrackRecordTable.libraryId],
        row[TrackRecordTable.title],
        row[TrackRecordTable.lastChapterRead],
        row[TrackRecordTable.totalChapters],
        row[TrackRecordTable.status],
        row[TrackRecordTable.score],
        row[TrackRecordTable.remoteUrl],
        row[TrackRecordTable.startDate],
        row[TrackRecordTable.finishDate],
    )

    fun displayScore(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<String> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, String>("DisplayScoreForTrackRecordDataLoader", id)
    }

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, MangaType>("MangaDataLoader", mangaId)
    }

    fun tracker(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<TrackerType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, TrackerType>("TrackerDataLoader", syncId)
    }
}

class TrackSearchType(
    val syncId: Int,
    val mediaId: Long,
    val title: String,
    val totalChapters: Int,
    val trackingUrl: String,
    val coverUrl: String,
    val summary: String,
    val publishingStatus: String,
    val publishingType: String,
    val startDate: String,
) {
    constructor(trackSearch: TrackSearch) : this(
        trackSearch.sync_id,
        trackSearch.media_id,
        trackSearch.title,
        trackSearch.total_chapters,
        trackSearch.tracking_url,
        trackSearch.cover_url,
        trackSearch.summary,
        trackSearch.publishing_status,
        trackSearch.publishing_type,
        trackSearch.start_date,
    )

    fun tracker(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<TrackerType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, TrackerType>("TrackerDataLoader", syncId)
    }
}

data class TrackRecordNodeList(
    override val nodes: List<TrackRecordType>,
    override val edges: List<TrackRecordEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class TrackRecordEdge(
        override val cursor: Cursor,
        override val node: TrackRecordType,
    ) : Edge()

    companion object {
        fun List<TrackRecordType>.toNodeList(): TrackRecordNodeList {
            return TrackRecordNodeList(
                nodes = this,
                edges = getEdges(),
                pageInfo =
                    PageInfo(
                        hasNextPage = false,
                        hasPreviousPage = false,
                        startCursor = Cursor(0.toString()),
                        endCursor = Cursor(lastIndex.toString()),
                    ),
                totalCount = size,
            )
        }

        private fun List<TrackRecordType>.getEdges(): List<TrackRecordEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                TrackRecordEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                TrackRecordEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}

data class TrackerNodeList(
    override val nodes: List<TrackerType>,
    override val edges: List<TrackerEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class TrackerEdge(
        override val cursor: Cursor,
        override val node: TrackerType,
    ) : Edge()

    companion object {
        fun List<TrackerType>.toNodeList(): TrackerNodeList {
            return TrackerNodeList(
                nodes = this,
                edges = getEdges(),
                pageInfo =
                    PageInfo(
                        hasNextPage = false,
                        hasPreviousPage = false,
                        startCursor = Cursor(0.toString()),
                        endCursor = Cursor(lastIndex.toString()),
                    ),
                totalCount = size,
            )
        }

        private fun List<TrackerType>.getEdges(): List<TrackerEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                TrackerEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                TrackerEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
