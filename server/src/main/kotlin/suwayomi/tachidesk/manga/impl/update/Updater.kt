package suwayomi.tachidesk.manga.impl.update

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mu.KotlinLogging
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

class Updater : IUpdater {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow(UpdateStatus())
    override val status = _status.asStateFlow()

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
            { value ->
                val newMaxPermits = value.coerceAtLeast(1).coerceAtMost(20)
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

    override fun getLastUpdateTimestamp(): Long {
        return preferences.getLong(lastUpdateKey, 0)
    }

    private fun autoUpdateTask() {
        val lastAutomatedUpdate = preferences.getLong(lastAutomatedUpdateKey, 0)
        preferences.edit().putLong(lastAutomatedUpdateKey, System.currentTimeMillis()).apply()

        if (status.value.running) {
            logger.debug { "Global update is already in progress" }
            return
        }

        logger.info {
            "Trigger global update (interval= ${serverConfig.globalUpdateInterval.value}h, lastAutomatedUpdate= ${Date(
                lastAutomatedUpdate,
            )})"
        }
        addCategoriesToUpdateQueue(Category.getCategoryList(), clear = true, forceAll = false)
    }

    fun scheduleUpdateTask() {
        HAScheduler.deschedule(currentUpdateTaskId)

        val isAutoUpdateDisabled = serverConfig.globalUpdateInterval.value == 0.0
        if (isAutoUpdateDisabled) {
            return
        }

        val updateInterval = serverConfig.globalUpdateInterval.value.hours.coerceAtLeast(6.hours).inWholeMilliseconds
        val lastAutomatedUpdate = preferences.getLong(lastAutomatedUpdateKey, 0)
        val timeToNextExecution = (updateInterval - (System.currentTimeMillis() - lastAutomatedUpdate)).mod(updateInterval)

        val wasPreviousUpdateTriggered =
            System.currentTimeMillis() - (
                if (lastAutomatedUpdate > 0) lastAutomatedUpdate else System.currentTimeMillis()
            ) < updateInterval
        if (!wasPreviousUpdateTriggered) {
            autoUpdateTask()
        }

        HAScheduler.schedule(::autoUpdateTask, updateInterval, timeToNextExecution, "global-update")
    }

    /**
     * Updates the status and sustains the "skippedMangas"
     */
    private fun updateStatus(
        jobs: List<UpdateJob>,
        running: Boolean? = null,
        categories: Map<CategoryUpdateStatus, List<CategoryDataClass>>? = null,
        skippedMangas: List<MangaDataClass>? = null,
    ) {
        val isRunning =
            running
                ?: jobs.any { job ->
                    job.status == JobStatus.PENDING || job.status == JobStatus.RUNNING
                }
        val updateStatusCategories = categories ?: _status.value.categoryStatusMap
        val tmpSkippedMangas = skippedMangas ?: _status.value.mangaStatusMap[JobStatus.SKIPPED] ?: emptyList()
        _status.update { UpdateStatus(updateStatusCategories, jobs, tmpSkippedMangas, isRunning) }
    }

    private fun getOrCreateUpdateChannelFor(source: String): Channel<UpdateJob> {
        return updateChannels.getOrPut(source) {
            logger.debug { "getOrCreateUpdateChannelFor: created channel for $source - channels: ${updateChannels.size + 1}" }
            createUpdateChannel(source)
        }
    }

    private fun createUpdateChannel(source: String): Channel<UpdateJob> {
        val channel = Channel<UpdateJob>(Channel.UNLIMITED)
        channel.consumeAsFlow()
            .onEach { job ->
                semaphore.withPermit {
                    process(job)
                }
            }
            .catch {
                logger.error(it) { "Error during updates (source: $source)" }
                handleChannelUpdateFailure(source)
            }
            .onCompletion { updateChannels.remove(source) }
            .launchIn(scope)
        return channel
    }

    private fun handleChannelUpdateFailure(source: String) {
        val isFailedSourceUpdate = { job: UpdateJob ->
            val isForSource = job.manga.sourceId == source
            val hasFailed = job.status == JobStatus.FAILED

            isForSource && hasFailed
        }

        // fail all updates for source
        tracker
            .filter { (_, job) -> !isFailedSourceUpdate(job) }
            .forEach { (mangaId, job) ->
                tracker[mangaId] = job.copy(status = JobStatus.FAILED)
            }

        updateStatus(
            tracker.values.toList(),
            tracker.any { (_, job) ->
                job.status == JobStatus.PENDING || job.status == JobStatus.RUNNING
            },
        )
    }

    private suspend fun process(job: UpdateJob) {
        tracker[job.manga.id] = job.copy(status = JobStatus.RUNNING)
        updateStatus(tracker.values.toList(), true)

        tracker[job.manga.id] =
            try {
                logger.info { "Updating ${job.manga}" }
                if (serverConfig.updateMangas.value) {
                    Manga.getManga(job.manga.id, true)
                }
                Chapter.getChapterList(job.manga.id, true)
                job.copy(status = JobStatus.COMPLETE)
            } catch (e: Exception) {
                logger.error(e) { "Error while updating ${job.manga}" }
                if (e is CancellationException) throw e
                job.copy(status = JobStatus.FAILED)
            }

        updateStatus(tracker.values.toList())
    }

    override fun addCategoriesToUpdateQueue(
        categories: List<CategoryDataClass>,
        clear: Boolean?,
        forceAll: Boolean,
    ) {
        preferences.edit().putLong(lastUpdateKey, System.currentTimeMillis()).apply()

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
                }
                .filter {
                    if (serverConfig.excludeNotStarted.value) {
                        it.lastReadAt != null
                    } else {
                        true
                    }
                }
                .filter {
                    if (serverConfig.excludeCompleted.value) {
                        it.status != MangaStatus.COMPLETED.name
                    } else {
                        true
                    }
                }
                .filter { forceAll || !excludedCategories.any { category -> mangasToCategoriesMap[it.id]?.contains(category) == true } }
                .toList()
        val skippedMangas = categoriesToUpdateMangas.subtract(mangasToUpdate.toSet()).toList()

        // In case no manga gets updated and no update job was running before, the client would never receive an info about its update request
        updateStatus(emptyList(), mangasToUpdate.isNotEmpty(), updateStatusCategories, skippedMangas)

        if (mangasToUpdate.isEmpty()) {
            return
        }

        addMangasToQueue(
            mangasToUpdate
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, MangaDataClass::title)),
        )
    }

    private fun addMangasToQueue(mangasToUpdate: List<MangaDataClass>) {
        mangasToUpdate.forEach { tracker[it.id] = UpdateJob(it) }
        updateStatus(tracker.values.toList(), mangasToUpdate.isNotEmpty())
        mangasToUpdate.forEach { addMangaToQueue(it) }
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
        updateStatus(emptyList(), false)
        updateChannels.forEach { (_, channel) -> channel.cancel() }
        updateChannels.clear()
    }
}
