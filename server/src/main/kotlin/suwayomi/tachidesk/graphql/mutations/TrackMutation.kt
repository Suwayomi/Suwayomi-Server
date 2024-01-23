package suwayomi.tachidesk.graphql.mutations

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.TrackRecordType
import suwayomi.tachidesk.graphql.types.TrackerType
import suwayomi.tachidesk.manga.impl.track.Track
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class TrackMutation {
    data class LoginTrackerOAuthInput(
        val clientMutationId: String? = null,
        val trackerId: Int,
        val callbackUrl: String,
    )

    data class LoginTrackerOAuthPayload(
        val clientMutationId: String?,
        val isLoggedIn: Boolean,
        val tracker: TrackerType,
    )

    fun loginTrackerOAuth(input: LoginTrackerOAuthInput): CompletableFuture<LoginTrackerOAuthPayload> {
        val tracker =
            requireNotNull(TrackerManager.getTracker(input.trackerId)) {
                "Could not find tracker"
            }
        return future {
            tracker.authCallback(input.callbackUrl)
            val trackerType = TrackerType(tracker)
            LoginTrackerOAuthPayload(
                input.clientMutationId,
                trackerType.isLoggedIn,
                trackerType,
            )
        }
    }

    data class LoginTrackerCredentialsInput(
        val clientMutationId: String? = null,
        val trackerId: Int,
        val username: String,
        val password: String,
    )

    data class LoginTrackerCredentialsPayload(
        val clientMutationId: String?,
        val isLoggedIn: Boolean,
        val tracker: TrackerType,
    )

    fun loginTrackerCredentials(input: LoginTrackerCredentialsInput): CompletableFuture<LoginTrackerCredentialsPayload> {
        val tracker =
            requireNotNull(TrackerManager.getTracker(input.trackerId)) {
                "Could not find tracker"
            }
        return future {
            tracker.login(input.username, input.password)
            val trackerType = TrackerType(tracker)
            LoginTrackerCredentialsPayload(
                input.clientMutationId,
                trackerType.isLoggedIn,
                trackerType,
            )
        }
    }

    data class LogoutTrackerInput(
        val clientMutationId: String? = null,
        val trackerId: Int,
    )

    data class LogoutTrackerPayload(
        val clientMutationId: String?,
        val isLoggedIn: Boolean,
        val tracker: TrackerType,
    )

    fun logoutTracker(input: LogoutTrackerInput): CompletableFuture<LogoutTrackerPayload> {
        val tracker =
            requireNotNull(TrackerManager.getTracker(input.trackerId)) {
                "Could not find tracker"
            }
        require(tracker.isLoggedIn) {
            "Cannot logout of a tracker that is not logged-in"
        }
        return future {
            tracker.logout()
            val trackerType = TrackerType(tracker)
            LogoutTrackerPayload(
                input.clientMutationId,
                trackerType.isLoggedIn,
                trackerType,
            )
        }
    }

    data class BindTrackInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
        val trackerId: Int,
        val remoteId: Long,
    )

    data class BindTrackPayload(
        val clientMutationId: String?,
        val trackRecord: TrackRecordType,
    )

    fun bindTrack(input: BindTrackInput): CompletableFuture<BindTrackPayload> {
        val (clientMutationId, mangaId, trackerId, remoteId) = input

        return future {
            Track.bind(
                mangaId,
                trackerId,
                remoteId,
            )
            val trackRecord =
                transaction {
                    TrackRecordTable.select {
                        TrackRecordTable.mangaId eq mangaId and (TrackRecordTable.trackerId eq trackerId)
                    }.first()
                }
            BindTrackPayload(
                clientMutationId,
                TrackRecordType(trackRecord),
            )
        }
    }

    data class UpdateTrackInput(
        val clientMutationId: String? = null,
        val recordId: Int,
        val status: Int? = null,
        val lastChapterRead: Double? = null,
        val scoreString: String? = null,
        val startDate: Long? = null,
        val finishDate: Long? = null,
        val unbind: Boolean? = null,
    )

    data class UpdateTrackPayload(
        val clientMutationId: String?,
        val trackRecord: TrackRecordType?,
    )

    fun updateTrack(input: UpdateTrackInput): CompletableFuture<UpdateTrackPayload> {
        return future {
            Track.update(
                Track.UpdateInput(
                    input.recordId,
                    input.status,
                    input.lastChapterRead,
                    input.scoreString,
                    input.startDate,
                    input.finishDate,
                    input.unbind,
                ),
            )

            val trackRecord =
                transaction {
                    TrackRecordTable.select {
                        TrackRecordTable.id eq input.recordId
                    }.firstOrNull()
                }
            UpdateTrackPayload(
                input.clientMutationId,
                trackRecord?.let { TrackRecordType(it) },
            )
        }
    }
}
