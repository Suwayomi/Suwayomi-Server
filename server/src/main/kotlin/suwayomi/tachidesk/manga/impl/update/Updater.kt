package suwayomi.tachidesk.manga.impl.update

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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mu.KotlinLogging
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeInUpdate
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.util.HAScheduler
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.Preferences
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

    private val lastUpdateKey = "lastUpdateKey"
    private val lastAutomatedUpdateKey = "lastAutomatedUpdateKey"
    private val preferences = Preferences.userNodeForPackage(Updater::class.java)

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
        preferences.putLong(lastAutomatedUpdateKey, System.currentTimeMillis())

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
        running: Boolean,
        categories: Map<CategoryUpdateStatus, List<CategoryDataClass>>? = null,
        skippedMangas: List<MangaDataClass>? = null,
    ) {
        val updateStatusCategories = categories ?: _status.value.categoryStatusMap
        val tmpSkippedMangas = skippedMangas ?: _status.value.mangaStatusMap[JobStatus.SKIPPED] ?: emptyList()
        _status.update { UpdateStatus(updateStatusCategories, jobs, tmpSkippedMangas, running) }
    }

    private fun getOrCreateUpdateChannelFor(source: String): Channel<UpdateJob> {
        return updateChannels.getOrPut(source) {
            logger.debug { "getOrCreateUpdateChannelFor: created channel for $source - channels: ${updateChannels.size + 1}" }
            createUpdateChannel()
        }
    }

    private fun createUpdateChannel(): Channel<UpdateJob> {
        val channel = Channel<UpdateJob>(Channel.UNLIMITED)
        channel.consumeAsFlow()
            .onEach { job ->
                semaphore.withPermit {
                    updateStatus(
                        process(job),
                        tracker.any { (_, job) ->
                            job.status == JobStatus.PENDING || job.status == JobStatus.RUNNING
                        },
                    )
                }
            }
            .catch { logger.error(it) { "Error during updates" } }
            .launchIn(scope)
        return channel
    }

    private suspend fun process(job: UpdateJob): List<UpdateJob> {
        tracker[job.manga.id] = job.copy(status = JobStatus.RUNNING)
        updateStatus(tracker.values.toList(), true)
        tracker[job.manga.id] =
            try {
                logger.info { "Updating \"${job.manga.title}\" (source: ${job.manga.sourceId})" }
                Chapter.getChapterList(job.manga.id, true)
                job.copy(status = JobStatus.COMPLETE)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.error(e) { "Error while updating ${job.manga.title}" }
                job.copy(status = JobStatus.FAILED)
            }
        return tracker.values.toList()
    }

    override fun addCategoriesToUpdateQueue(
        categories: List<CategoryDataClass>,
        clear: Boolean?,
        forceAll: Boolean,
    ) {
        preferences.putLong(lastUpdateKey, System.currentTimeMillis())

        if (clear == true) {
            reset()
        }

        val includeInUpdateStatusToCategoryMap = categories.groupBy { it.includeInUpdate }
        val excludedCategories = includeInUpdateStatusToCategoryMap[IncludeInUpdate.EXCLUDE].orEmpty()
        val includedCategories = includeInUpdateStatusToCategoryMap[IncludeInUpdate.INCLUDE].orEmpty()
        val unsetCategories = includeInUpdateStatusToCategoryMap[IncludeInUpdate.UNSET].orEmpty()
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
