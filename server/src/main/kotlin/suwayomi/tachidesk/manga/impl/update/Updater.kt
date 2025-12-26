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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.util.HAScheduler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
class Updater : IUpdater {
    private val logger = KotlinLogging.logger {}
    private val notifyFlowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val notifyFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Deprecated("Replaced with updatesFlow", replaceWith = ReplaceWith("updatesFlow"))
    private val statusFlow = MutableSharedFlow<UpdateStatus>()

    @Deprecated("Replaced with updates", replaceWith = ReplaceWith("updates"))
    override val status = statusFlow.onStart { emit(getStatusDeprecated(null)) }

    private val updatesFlow = MutableSharedFlow<UpdateUpdates>()
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
    private var updateStatusCategories: Map<CategoryUpdateStatus, List<CategoryDataClass>> = emptyMap()
    private var updateStatusSkippedMangas: List<MangaDataClass> = emptyList()

    private val tracker = ConcurrentHashMap<Int, UpdateJob>()
    private val updateChannels = ConcurrentHashMap<String, Channel<UpdateJob>>()

    private var maxSourcesInParallel = 20 // max permits, necessary to be set to be able to release up to 20 permits
    private val semaphore = Semaphore(maxSourcesInParallel)

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

                val addMorePermits = permitDifference < 0
                for (i in 1..permitDifference.absoluteValue) {
                    if (addMorePermits) {
                        semaphore.release()
                    } else {
                        semaphore.acquire()
                    }
                }
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

    private fun autoUpdateTask() {
        try {
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
            addCategoriesToUpdateQueue(Category.getCategoryList(), clear = true, forceAll = false)
        } catch (e: Exception) {
            logger.error(e) { "autoUpdateTask: failed due to" }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun scheduleUpdateTask() {
        HAScheduler.deschedule(currentUpdateTaskId)

        val isAutoUpdateDisabled = serverConfig.globalUpdateInterval.value == 0.0
        if (isAutoUpdateDisabled) {
            return
        }

        val updateInterval = serverConfig.globalUpdateInterval.value.hours.inWholeMilliseconds
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
     * Pass "isRunning" to force a specific running state
     */
    private suspend fun updateStatus(
        immediate: Boolean = false,
        categoryUpdates: List<CategoryUpdateJob> = emptyList(),
        mangaUpdates: List<UpdateJob> = emptyList(),
        isRunning: Boolean? = null,
    ) {
        mangaUpdates.forEach { this.mangaUpdates[it.manga.id] = it }
        categoryUpdates.forEach { this.categoryUpdates[it.category.id] = it }

        if (immediate) {
            val status = getStatusDeprecated(running = isRunning)
            val updates = getUpdates(isRunning)

            this.mangaUpdates.clear()
            this.categoryUpdates.clear()

            statusFlow.emit(status)
            _status.update { status }
            updatesFlow.emit(updates)

            return
        }

        notifyFlow.emit(Unit)
    }

    private fun getOrCreateUpdateChannelFor(source: String): Channel<UpdateJob> =
        updateChannels.getOrPut(source) {
            logger.debug { "getOrCreateUpdateChannelFor: created channel for $source - channels: ${updateChannels.size + 1}" }
            createUpdateChannel(source)
        }

    private fun createUpdateChannel(source: String): Channel<UpdateJob> {
        val channel = Channel<UpdateJob>(Channel.UNLIMITED)
        channel
            .consumeAsFlow()
            .onEach { job ->
                semaphore.withPermit {
                    process(job)
                }
            }.catch {
                logger.error(it) { "Error during updates (source: $source)" }
                handleChannelUpdateFailure(source)
            }.onCompletion { updateChannels.remove(source) }
            .launchIn(scope)
        return channel
    }

    private suspend fun handleChannelUpdateFailure(source: String) {
        val isFailedSourceUpdate = { job: UpdateJob ->
            val isForSource = job.manga.sourceId == source
            val hasFailed = job.status == JobStatus.FAILED

            isForSource && hasFailed
        }

        // fail all updates for source
        val sourceUpdateJobs = tracker.filter { (_, job) -> !isFailedSourceUpdate(job) }
        sourceUpdateJobs.forEach { (mangaId, job) -> tracker[mangaId] = job.copy(status = JobStatus.FAILED) }

        updateStatus(mangaUpdates = sourceUpdateJobs.values.toList())
    }

    private suspend fun process(job: UpdateJob) {
        tracker[job.manga.id] = job.copy(status = JobStatus.RUNNING)
        updateStatus(mangaUpdates = listOf(tracker[job.manga.id]!!))

        tracker[job.manga.id] =
            try {
                logger.info { "Updating ${job.manga}" }
                if (serverConfig.updateMangas.value || !job.manga.initialized) {
                    Manga.getManga(job.manga.id, true)
                }
                Chapter.getChapterList(job.manga.id, true)
                job.copy(status = JobStatus.COMPLETE)
            } catch (e: Exception) {
                logger.error(e) { "Error while updating ${job.manga}" }
                if (e is CancellationException) throw e
                job.copy(status = JobStatus.FAILED)
            }

        val wasLastJob = tracker.values.none { it.status == JobStatus.PENDING || it.status == JobStatus.RUNNING }

        // in case this is the last update job, the running flag has to be true, before it gets set to false, to be able
        // to properly clear the dataloader store in UpdateType
        updateStatus(immediate = wasLastJob, isRunning = true, mangaUpdates = listOf(tracker[job.manga.id]!!))

        if (wasLastJob) {
            updateStatus(isRunning = false)
        }
    }

    override fun addCategoriesToUpdateQueue(
        categories: List<CategoryDataClass>,
        clear: Boolean?,
        forceAll: Boolean,
    ) {
        scope.launch {
            SyncManager.ensureSync()

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
                    .flatMap { CategoryManga.getCategoryMangaList(it.id) }
                    .distinctBy { it.id }
            val mangasToCategoriesMap = CategoryManga.getMangasCategories(categoriesToUpdateMangas.map { it.id })
            val mangasToUpdate =
                categoriesToUpdateMangas
                    .asSequence()
                    .filter { it.updateStrategy == UpdateStrategy.ALWAYS_UPDATE }
                    .filter {
                        if (serverConfig.excludeUnreadChapters.value) {
                            (it.unreadCount ?: 0L) == 0L
                        } else {
                            true
                        }
                    }.filter {
                        if (it.initialized && serverConfig.excludeNotStarted.value) {
                            it.lastReadAt != null
                        } else {
                            true
                        }
                    }.filter {
                        if (serverConfig.excludeCompleted.value) {
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

            this@Updater.updateStatusCategories = updateStatusCategories
            this@Updater.updateStatusSkippedMangas = skippedMangas

            if (mangasToUpdate.isEmpty()) {
                // In case no manga gets updated and no update job was running before, the client would never receive an info
                // about its update request
                scope.launch {
                    updateStatus(immediate = true)
                }
                return@launch
            }

            scope.launch {
                updateStatus(
                    categoryUpdates =
                        updateStatusCategories[CategoryUpdateStatus.UPDATING]
                            ?.map {
                                CategoryUpdateJob(it, CategoryUpdateStatus.UPDATING)
                            }.orEmpty(),
                    mangaUpdates = mangasToUpdate.map { UpdateJob(it) },
                    isRunning = true,
                )
            }

            addMangasToQueue(
                mangasToUpdate
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, MangaDataClass::title)),
            )
        }
    }

    override fun addMangasToQueue(mangas: List<MangaDataClass>) {
        // create all manga update jobs before adding them to the queue so that the client is able to calculate the
        // progress properly right form the start
        mangas.forEach { tracker[it.id] = UpdateJob(it) }
        mangas.forEach { addMangaToQueue(it) }
    }

    private fun addMangaToQueue(manga: MangaDataClass) {
        val updateChannel = getOrCreateUpdateChannelFor(manga.sourceId)
        scope.launch {
            updateChannel.send(UpdateJob(manga))
        }
    }

    override fun reset() {
        scope.coroutineContext.cancelChildren()

        tracker.clear()
        this.mangaUpdates.clear()
        this.categoryUpdates.clear()
        this.updateStatusCategories = emptyMap()
        this.updateStatusSkippedMangas = emptyList()

        scope.launch {
            updateStatus(immediate = true, isRunning = false)
        }

        updateChannels.forEach { (_, channel) -> channel.cancel() }
        updateChannels.clear()
    }
}
