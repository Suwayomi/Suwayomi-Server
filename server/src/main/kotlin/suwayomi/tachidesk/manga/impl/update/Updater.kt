package suwayomi.tachidesk.manga.impl.update

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserSettingsTable
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.UserSettings
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.util.HAScheduler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
class Updater : IUpdater {
    private val logger = KotlinLogging.logger {}
    private val notifyFlowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // submission coroutines run on this scope instead of [scope]: reset() cancels [scope]'s
    // children, and an enqueue killed mid-flight would leave a PENDING zombie in the tracker
    private val enqueueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val notifyFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // buffered: a stalled subscriber must not block emitters (a 0-buffer emit would suspend
    // inside semaphore.withPermit/process and leak the permit, and _status updates were
    // queued behind the suspending emit and could be frozen entirely)
    @Deprecated("Replaced with updatesFlow", replaceWith = ReplaceWith("updatesFlow"))
    private val statusFlow =
        MutableSharedFlow<UpdateStatus>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    @Deprecated("Replaced with updates", replaceWith = ReplaceWith("updates"))
    override val status = statusFlow.onStart { emit(getStatusDeprecated(null)) }

    private val updatesFlow =
        MutableSharedFlow<UpdateUpdates>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val updates = updatesFlow.onStart { emit(getUpdates(addInitial = true)) }

    init {
        // has to be in its own scope (notifyFlowScope), otherwise, the collection gets canceled due to canceling the scopes (scope) children in the reset function
        notifyFlowScope.launch {
            notifyFlow.sample(1.seconds).collect {
                updateStatus(immediate = true)
            }
        }
    }

    private val _status = MutableStateFlow(UpdateStatus())
    override val statusDeprecated = _status.asStateFlow()

    private val mangaUpdates = ConcurrentHashMap<Int, UpdateJob>()
    private val categoryUpdates = ConcurrentHashMap<Int, CategoryUpdateJob>()

    @Volatile
    private var updateStatusCategories: Map<CategoryUpdateStatus, List<CategoryDataClass>> = emptyMap()

    @Volatile
    private var updateStatusSkippedMangas: List<MangaDataClass> = emptyList()

    // serializes the read-modify-write cycles on mangaUpdates/categoryUpdates/updateStatus* so
    // concurrent updateStatus()/addCategoriesToUpdateQueue calls can't lose batches, and the
    // incremental maps are snapshotted+drained atomically
    private val statusMutex = Mutex()

    private val tracker = ConcurrentHashMap<Int, UpdateJob>()
    private val updateChannels = ConcurrentHashMap<String, Channel<UpdateJob>>()

    // serializes "tracker.put + channel.trySend" against reset()'s clear so a submission can
    // never land in the gap between the tracker write and the channel send
    private val queueLock = Any()

    // manga ids of automated (userId==null) jobs for which at least one library owner enabled
    // updateMangas; batch-prefetched at enqueue time so process() doesn't need a per-manga DB
    // round-trip while holding a source permit
    private val automatedUpdateMangas = ConcurrentHashMap<Int, Boolean>()

    private var maxSourcesInParallel = 20 // max permits, necessary to be set to be able to release up to 20 permits
    private val semaphore = Semaphore(maxSourcesInParallel)

    // >0 while a maxSourcesInParallel shrink is in progress: permits released by finished jobs
    // are retired instead of being handed back to the semaphore until the deficit is consumed.
    // This keeps the effective permit count converging to maxSourcesInParallel without ever
    // suspending the config-subscription collector
    private val pendingPermitRemovals = AtomicInteger(0)

    private val lastUpdateKey = "lastGlobalUpdate"
    private val lastAutomatedUpdateKey = "lastAutomatedGlobalUpdate"
    private val preferences = Injekt.get<Application>().getSharedPreferences("server_util", Context.MODE_PRIVATE)

    private var currentUpdateTaskId = ""

    init {
        serverConfig.subscribeTo(serverConfig.globalUpdateInterval, ::scheduleUpdateTask)
        serverConfig.subscribeTo(
            serverConfig.maxSourcesInParallel,
            { newMaxPermits ->
                val permitDifference = maxSourcesInParallel - newMaxPermits
                maxSourcesInParallel = newMaxPermits
                updateMaxSourcePermits(permitDifference)
            },
            ignoreInitialValue = false,
        )
    }

    override fun getLastUpdateTimestamp(): Long = preferences.getLong(lastUpdateKey, 0)

    fun saveLastUpdateTimestamp() {
        preferences.edit().putLong(lastUpdateKey, System.currentTimeMillis()).apply()
    }

    fun getLastAutomatedUpdateTimestamp(): Long = preferences.getLong(lastAutomatedUpdateKey, 0)

    fun saveLastAutomatedUpdateTimestamp() {
        preferences.edit().putLong(lastAutomatedUpdateKey, System.currentTimeMillis()).apply()
    }

    override fun deleteLastAutomatedUpdateTimestamp() {
        preferences.edit().remove(lastAutomatedUpdateKey).apply()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun autoUpdateTask() {
        // runs on HAScheduler's single Timer thread — the actual work involves blocking DB
        // transactions and must not stall every other scheduled task (incl. the hibernation
        // checker), so this entry point only dispatches to a coroutine
        GlobalScope.launch {
            try {
                runAutoUpdate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "autoUpdateTask: failed due to" }
            }
        }
    }

    private suspend fun runAutoUpdate() {
        val lastAutomatedUpdate = getLastAutomatedUpdateTimestamp()
        saveLastAutomatedUpdateTimestamp()

        if (getStatus().isRunning) {
            logger.debug { "Global update is already in progress" }
            return
        }

        logger.info {
            "Trigger global update (interval= ${serverConfig.globalUpdateInterval.value}h, lastAutomatedUpdate= ${Date(
                lastAutomatedUpdate,
            )})"
        }

        val userIds =
            transaction {
                UserAccountTable.selectAll().map { it[UserAccountTable.id].value }
            }

        reset()

        userIds.forEach { userId ->
            addCategoriesToUpdateQueue(
                userId,
                Category.getCategoryList(userId),
                clear = false,
                forceAll = false,
                automatedUpdate = true,
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun scheduleUpdateTask() {
        // any exception escaping here would kill the globalUpdateInterval config-subscription
        // collector for good, so it has to be contained
        try {
            HAScheduler.deschedule(currentUpdateTaskId)

            val isAutoUpdateDisabled = serverConfig.globalUpdateInterval.value == 0.0
            if (isAutoUpdateDisabled) {
                return
            }

            val updateInterval = serverConfig.globalUpdateInterval.value.hours.inWholeMilliseconds
            if (updateInterval <= 0) {
                // a sub-millisecond or negative interval would crash `mod(updateInterval)`
                // (ArithmeticException) or Timer.scheduleAtFixedRate (IAE) — treat like disabled
                logger.warn {
                    "Global update interval of ${serverConfig.globalUpdateInterval.value}h resolves to " +
                        "${updateInterval}ms, skipping auto-update scheduling"
                }
                return
            }
            val lastAutomatedUpdate = getLastAutomatedUpdateTimestamp()
            val isInitialScheduling = lastAutomatedUpdate == 0L

            val timeToNextExecution =
                if (!isInitialScheduling) {
                    (updateInterval - (System.currentTimeMillis() - lastAutomatedUpdate)).mod(updateInterval)
                } else {
                    updateInterval
                }

            if (isInitialScheduling) {
                saveLastAutomatedUpdateTimestamp()
            }

            val wasPreviousUpdateTriggered =
                System.currentTimeMillis() - (
                    if (!isInitialScheduling) lastAutomatedUpdate else System.currentTimeMillis()
                ) < updateInterval
            if (!wasPreviousUpdateTriggered) {
                GlobalScope.launch {
                    autoUpdateTask()
                }
            }

            currentUpdateTaskId = HAScheduler.schedule(::autoUpdateTask, updateInterval, timeToNextExecution, "global-update")
        } catch (e: Exception) {
            logger.error(e) { "scheduleUpdateTask: failed" }
        }
    }

    private fun isRunning(): Boolean =
        tracker.values.toList().any { job -> job.status == JobStatus.PENDING || job.status == JobStatus.RUNNING }

    // old status that is still required for the deprecated endpoints
    private fun getStatusDeprecated(running: Boolean? = null): UpdateStatus {
        val jobs = tracker.values.toList()
        val isRunning = running ?: isRunning()
        return UpdateStatus(this.updateStatusCategories, jobs, this.updateStatusSkippedMangas, isRunning)
    }

    private fun getStatus(
        categories: List<CategoryUpdateJob>,
        mangas: List<UpdateJob>,
        running: Boolean? = null,
        addInitial: Boolean? = false,
    ): UpdateUpdates =
        UpdateUpdates(
            running ?: isRunning(),
            categories,
            mangas,
            tracker.size,
            tracker.values.count { it.status == JobStatus.COMPLETE || it.status == JobStatus.FAILED },
            this.updateStatusCategories[CategoryUpdateStatus.SKIPPED]?.size ?: 0,
            this.updateStatusSkippedMangas.size,
            if (addInitial == true) getStatus() else null,
        )

    override fun getStatus(): UpdateUpdates =
        getStatus(
            this.updateStatusCategories[CategoryUpdateStatus.UPDATING]
                ?.map {
                    CategoryUpdateJob(
                        it,
                        CategoryUpdateStatus.UPDATING,
                    )
                }.orEmpty(),
            tracker.values.toList(),
        )

    private fun getUpdates(
        running: Boolean? = null,
        addInitial: Boolean? = null,
    ): UpdateUpdates =
        getStatus(
            categoryUpdates.values.toList(),
            mangaUpdates.values.toList(),
            running,
            addInitial = addInitial,
        )

    /**
     * Pass "isRunning" to force a specific running state.
     *
     * Note: [isRunning] is only honored on the immediate emission path. The debounced
     * (non-immediate) path only notifies the notifyFlow collector, which recomputes the
     * running state at collection time — the flag is deliberately dropped there.
     */
    private suspend fun updateStatus(
        immediate: Boolean = false,
        categoryUpdates: List<CategoryUpdateJob> = emptyList(),
        mangaUpdates: List<UpdateJob> = emptyList(),
        isRunning: Boolean? = null,
    ) {
        statusMutex.withLock {
            mangaUpdates.forEach { this.mangaUpdates[it.manga.id] = it }
            categoryUpdates.forEach { this.categoryUpdates[it.category.id] = it }

            if (immediate) {
                val running = isRunning ?: isRunning()
                if (!running) {
                    // the batch is finished: drop stale UPDATING categories so getStatus() stops
                    // reporting categories as updating while isRunning=false
                    this.updateStatusCategories = this.updateStatusCategories - CategoryUpdateStatus.UPDATING
                }
                val status = getStatusDeprecated(running = running)
                val updates = getUpdates(running)

                // drain incrementally accumulated updates, atomically with the snapshot above
                this.mangaUpdates.clear()
                this.categoryUpdates.clear()

                // update the state flow before the suspending emits so statusDeprecated can
                // never be frozen by a stalled flow subscriber
                _status.update { status }
                statusFlow.emit(status)
                updatesFlow.emit(updates)

                return
            }
        }

        notifyFlow.emit(Unit)
    }

    private fun getOrCreateUpdateChannelFor(source: String): Channel<UpdateJob> =
        // computeIfAbsent: getOrPut is not atomic and a losing thread would leak a channel
        // whose collector lives forever while never being tracked in the map
        updateChannels.computeIfAbsent(source) {
            logger.debug { "getOrCreateUpdateChannelFor: created channel for $source - channels: ${updateChannels.size + 1}" }
            createUpdateChannel(source)
        }

    private fun createUpdateChannel(source: String): Channel<UpdateJob> {
        val channel = Channel<UpdateJob>(Channel.UNLIMITED)
        channel
            .consumeAsFlow()
            .onEach { job ->
                withSourcePermit {
                    process(job)
                }
            }.catch {
                logger.error(it) { "Error during updates (source: $source)" }
                handleChannelUpdateFailure(source)
            }.onCompletion {
                // remove only if still mapped to *this* channel instance — a newer channel for
                // the same source may already have replaced it after a reset/re-creation
                updateChannels.remove(source, channel)
            }.launchIn(scope)
        return channel
    }

    private suspend fun handleChannelUpdateFailure(source: String) {
        // fail only this source's unfinished jobs — jobs of other sources and already finished
        // jobs must keep their state (the inverted predicate used to fail every other source's
        // jobs and re-mark COMPLETE jobs as FAILED)
        val sourceUpdateJobs =
            tracker.filter { (_, job) ->
                job.manga.sourceId == source &&
                    job.status != JobStatus.COMPLETE &&
                    job.status != JobStatus.FAILED
            }
        val failedJobs =
            sourceUpdateJobs.map { (mangaId, job) ->
                job.copy(status = JobStatus.FAILED).also { tracker[mangaId] = it }
            }

        updateStatus(mangaUpdates = failedJobs)
    }

    private suspend fun <T> withSourcePermit(block: suspend () -> T): T {
        semaphore.acquire()
        try {
            return block()
        } finally {
            releaseOrRetirePermit()
        }
    }

    /**
     * Returns the permit to the semaphore, unless a shrink requested by the
     * maxSourcesInParallel subscription is still outstanding — in that case the permit is
     * retired instead. Together with [updateMaxSourcePermits] this calibrates the effective
     * permit count on the consumer side and never suspends the config collector.
     */
    private fun releaseOrRetirePermit() {
        while (true) {
            val pending = pendingPermitRemovals.get()
            if (pending <= 0) {
                semaphore.release()
                return
            }
            if (pendingPermitRemovals.compareAndSet(pending, pending - 1)) {
                return
            }
        }
    }

    /**
     * Applies a maxSourcesInParallel change without suspending: a shrink retires idle permits
     * immediately via tryAcquire and registers the remainder to be consumed by
     * [releaseOrRetirePermit] as running jobs finish; a growth releases new permits after
     * absorbing any outstanding shrink debt.
     */
    private fun updateMaxSourcePermits(permitDifference: Int) {
        if (permitDifference < 0) {
            var toAdd = -permitDifference
            // absorb outstanding shrink debt first so the net count matches the new target
            while (toAdd > 0) {
                val pending = pendingPermitRemovals.get()
                if (pending <= 0) break
                val absorb = minOf(pending, toAdd)
                if (pendingPermitRemovals.compareAndSet(pending, pending - absorb)) {
                    toAdd -= absorb
                }
            }
            repeat(toAdd) { semaphore.release() }
        } else {
            var remaining = permitDifference
            while (remaining > 0 && semaphore.tryAcquire()) {
                remaining--
            }
            if (remaining > 0) {
                pendingPermitRemovals.addAndGet(remaining)
            }
        }
    }

    private suspend fun process(job: UpdateJob) {
        val runningJob = job.copy(status = JobStatus.RUNNING)
        tracker[job.manga.id] = runningJob
        updateStatus(mangaUpdates = listOf(runningJob))

        // updateMangas is per-user. For automated global updates (job.userId == null), update the manga info if any
        // user with this manga has it enabled. For user-triggered updates, the triggering user's setting applies.
        val updateManga =
            if (job.userId != null) {
                userConfig.updateMangas.value(job.userId) || !job.manga.initialized
            } else {
                (
                    // batch-prefetched at enqueue time; fall back to a live query for jobs that
                    // bypassed addCategoriesToUpdateQueue
                    automatedUpdateMangas[job.manga.id]
                        ?: anyUserWantsMangaUpdate(job.manga.id)
                ) ||
                    !job.manga.initialized
            }

        val finishedJob =
            try {
                logger.debug { "Updating manga ${job.manga.id} (\"${job.manga.title}\")" }
                Manga.updateMangaAndChapters(
                    job.manga.id,
                    updateManga = updateManga,
                )
                job.copy(status = JobStatus.COMPLETE)
            } catch (e: Exception) {
                logger.error(e) { "Error while updating manga ${job.manga.id} (\"${job.manga.title}\")" }
                if (e is CancellationException) throw e
                job.copy(status = JobStatus.FAILED)
            }
        tracker[job.manga.id] = finishedJob

        val wasLastJob = tracker.values.none { it.status == JobStatus.PENDING || it.status == JobStatus.RUNNING }

        // in case this is the last update job, the running flag has to be true, before it gets set to false, to be able
        // to properly clear the dataloader store in UpdateType
        updateStatus(immediate = wasLastJob, isRunning = true, mangaUpdates = listOf(finishedJob))

        if (wasLastJob) {
            updateStatus(isRunning = false)
        }
    }

    override fun addCategoriesToUpdateQueue(
        userId: Int,
        categories: List<CategoryDataClass>,
        clear: Boolean?,
        forceAll: Boolean,
        automatedUpdate: Boolean,
    ) {
        // enqueueScope: submission must not be killed by reset()'s cancelChildren — a cancelled
        // enqueue leaves tracker entries that were never sent to a channel (PENDING zombies)
        enqueueScope.launch {
            try {
                SyncManager.ensureSync(userId)

                saveLastUpdateTimestamp()

                if (clear == true) {
                    reset()
                }

                val includeInUpdateStatusToCategoryMap = categories.groupBy { it.includeInUpdate }
                val excludedCategories = includeInUpdateStatusToCategoryMap[IncludeOrExclude.EXCLUDE].orEmpty()
                val includedCategories = includeInUpdateStatusToCategoryMap[IncludeOrExclude.INCLUDE].orEmpty()
                val unsetCategories = includeInUpdateStatusToCategoryMap[IncludeOrExclude.UNSET].orEmpty()
                val categoriesToUpdate =
                    if (forceAll) {
                        categories
                    } else {
                        includedCategories.ifEmpty { unsetCategories }
                    }
                val skippedCategories = categories.subtract(categoriesToUpdate.toSet()).toList()
                val updateStatusCategories =
                    mapOf(
                        Pair(CategoryUpdateStatus.UPDATING, categoriesToUpdate),
                        Pair(CategoryUpdateStatus.SKIPPED, skippedCategories),
                    )

                logger.debug { "Updating categories: '${categoriesToUpdate.joinToString("', '") { it.name }}'" }

                val categoriesToUpdateMangas =
                    categoriesToUpdate
                        .flatMap { CategoryManga.getCategoryMangaList(userId, it.id) }
                        .distinctBy { it.id }
                val mangasToCategoriesMap = CategoryManga.getMangasCategories(userId, categoriesToUpdateMangas.map { it.id })
                val mangasToUpdate =
                    categoriesToUpdateMangas
                        .asSequence()
                        .filter { it.updateStrategy == UpdateStrategy.ALWAYS_UPDATE }
                        .filter {
                            if (userConfig.excludeUnreadChapters.value(userId)) {
                                (it.unreadCount ?: 0L) == 0L
                            } else {
                                true
                            }
                        }.filter {
                            if (it.initialized && userConfig.excludeNotStarted.value(userId)) {
                                it.lastReadAt != null
                            } else {
                                true
                            }
                        }.filter {
                            if (userConfig.excludeCompleted.value(userId)) {
                                it.status != MangaStatus.COMPLETED.name
                            } else {
                                true
                            }
                        }.filter {
                            forceAll ||
                                !excludedCategories.any { category ->
                                    mangasToCategoriesMap[it.id]?.contains(category) == true
                                }
                        }.toList()
                val skippedMangas = categoriesToUpdateMangas.subtract(mangasToUpdate.toSet()).toList()

                statusMutex.withLock {
                    if (clear == true) {
                        this@Updater.updateStatusCategories = updateStatusCategories
                        this@Updater.updateStatusSkippedMangas = skippedMangas
                    } else {
                        this@Updater.updateStatusCategories =
                            mergeCategoryStatusMaps(this@Updater.updateStatusCategories, updateStatusCategories)
                        this@Updater.updateStatusSkippedMangas =
                            (this@Updater.updateStatusSkippedMangas + skippedMangas).distinctBy { it.id }
                    }
                }

                if (automatedUpdate) {
                    // pre-fetch the per-user updateMangas flags for the whole batch in one query
                    // so process() doesn't hit the DB per manga while holding a source permit
                    runCatching {
                        warmAutomatedUpdateMangas(mangasToUpdate.map { it.id })
                    }.onFailure {
                        logger.warn(it) { "Failed to pre-fetch updateMangas flags, falling back to per-manga queries" }
                    }
                }

                if (mangasToUpdate.isEmpty()) {
                    // In case no manga gets updated and no update job was running before, the client would never receive an info
                    // about its update request
                    updateStatus(immediate = true)
                    return@launch
                }

                updateStatus(
                    categoryUpdates =
                        updateStatusCategories[CategoryUpdateStatus.UPDATING]
                            ?.map {
                                CategoryUpdateJob(it, CategoryUpdateStatus.UPDATING)
                            }.orEmpty(),
                    mangaUpdates = mangasToUpdate.map { UpdateJob(it, userId = if (automatedUpdate) null else userId) },
                    isRunning = true,
                )

                addMangasToQueue(
                    mangasToUpdate
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, MangaDataClass::title)),
                    if (automatedUpdate) null else userId,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.error(e) { "addCategoriesToUpdateQueue: failed (userId=$userId)" }
                // surface the failure instead of dying silently in the supervisor scope
                runCatching { updateStatus(immediate = true) }
            }
        }
    }

    override fun addMangasToQueue(
        mangas: List<MangaDataClass>,
        userId: Int?,
    ) {
        // the queueLock makes "is queued? -> tracker.put -> channel.trySend" atomic against
        // reset() and against concurrent submissions from other users
        synchronized(queueLock) {
            // the same manga can be part of multiple users' queues (e.g. automated per-user
            // global updates) — dedupe by manga id so it isn't fetched once per user
            val newJobs =
                mangas
                    .distinctBy { it.id }
                    .map { UpdateJob(it, userId = userId) }
                    .filter { job ->
                        val existing = tracker[job.manga.id]
                        existing == null ||
                            (existing.status != JobStatus.PENDING && existing.status != JobStatus.RUNNING)
                    }
            // create all manga update jobs before adding them to the queue so that the client is able to calculate the
            // progress properly right form the start
            newJobs.forEach { tracker[it.manga.id] = it }
            newJobs.forEach { addMangaToQueue(it) }
        }
    }

    /**
     * Must be called under [queueLock].
     */
    private fun addMangaToQueue(job: UpdateJob) {
        var updateChannel = getOrCreateUpdateChannelFor(job.manga.sourceId)

        // trySend is non-suspending, so the enqueue can't be cancelled mid-flight; a channel
        // cancelled by reset() (or a dead collector) but still mapped is dropped and recreated once
        var result = updateChannel.trySend(job)
        if (result.isFailure) {
            updateChannels.remove(job.manga.sourceId, updateChannel)
            updateChannel = getOrCreateUpdateChannelFor(job.manga.sourceId)
            result = updateChannel.trySend(job)
        }

        if (result.isFailure) {
            // roll the tracker entry back instead of leaving a PENDING zombie that would pin
            // isRunning()=true and deadlock automated updates
            tracker.remove(job.manga.id, job)
            logger.error(result.exceptionOrNull()) {
                "addMangaToQueue: failed to enqueue update job for manga ${job.manga.id} (source ${job.manga.sourceId})"
            }
        }
    }

    override fun reset() {
        scope.coroutineContext.cancelChildren()

        synchronized(queueLock) {
            tracker.clear()
            this.mangaUpdates.clear()
            this.categoryUpdates.clear()
            this.updateStatusCategories = emptyMap()
            this.updateStatusSkippedMangas = emptyList()
            this.automatedUpdateMangas.clear()

            updateChannels.forEach { (_, channel) -> channel.cancel() }
            updateChannels.clear()
        }

        // enqueueScope, not scope: a subsequent reset must not cancel this emission
        enqueueScope.launch {
            updateStatus(immediate = true, isRunning = false)
        }
    }

    /**
     * Returns true if any user who has [mangaId] in their library has the per-user `updateMangas` setting enabled
     */
    private fun anyUserWantsMangaUpdate(mangaId: Int): Boolean =
        transaction {
            MangaUserTable
                .innerJoin(
                    UserSettingsTable,
                    { MangaUserTable.user },
                    { UserSettingsTable.user },
                    { UserSettingsTable.key eq userConfig.updateMangas.key },
                ).select(MangaUserTable.id)
                .where {
                    (MangaUserTable.manga eq mangaId) and
                        (MangaUserTable.inLibrary eq true) and
                        (UserSettingsTable.value eq UserSettings.encode(true))
                }.count() > 0
        }

    /**
     * Batch variant of [anyUserWantsMangaUpdate]: a single query per chunk instead of a
     * per-manga round-trip while a source permit is held in [process]. Results are recorded
     * in [automatedUpdateMangas] for the duration of the batch.
     */
    private fun warmAutomatedUpdateMangas(mangaIds: List<Int>) {
        if (mangaIds.isEmpty()) return

        mangaIds.distinct().chunked(1000).forEach { chunk ->
            val wanted =
                transaction {
                    MangaUserTable
                        .innerJoin(
                            UserSettingsTable,
                            { MangaUserTable.user },
                            { UserSettingsTable.user },
                            { UserSettingsTable.key eq userConfig.updateMangas.key },
                        ).select(MangaUserTable.manga)
                        .where {
                            (MangaUserTable.manga inList chunk) and
                                (MangaUserTable.inLibrary eq true) and
                                (UserSettingsTable.value eq UserSettings.encode(true))
                        }.map { it[MangaUserTable.manga].value }
                        .toSet()
                }
            chunk.forEach { automatedUpdateMangas[it] = it in wanted }
        }
    }

    private fun mergeCategoryStatusMaps(
        existing: Map<CategoryUpdateStatus, List<CategoryDataClass>>,
        new: Map<CategoryUpdateStatus, List<CategoryDataClass>>,
    ): Map<CategoryUpdateStatus, List<CategoryDataClass>> {
        val merged = existing.toMutableMap()
        new.forEach { (status, categories) ->
            merged[status] = (merged[status].orEmpty() + categories).distinctBy { it.id }
        }
        return merged
    }
}
