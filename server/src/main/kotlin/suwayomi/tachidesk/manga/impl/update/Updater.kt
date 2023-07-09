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
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeInUpdate
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.server.serverConfig
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.Preferences
import kotlin.time.Duration.Companion.hours

class Updater : IUpdater {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow(UpdateStatus())
    override val status = _status.asStateFlow()

    private val tracker = ConcurrentHashMap<Int, UpdateJob>()
    private val updateChannels = ConcurrentHashMap<String, Channel<UpdateJob>>()

    private val semaphore = Semaphore(serverConfig.maxParallelUpdateRequests)

    private val lastAutomatedUpdateKey = "lastAutomatedUpdateKey"
    private val preferences = Preferences.userNodeForPackage(Updater::class.java)

    private val updateTimer = Timer()
    private var currentUpdateTask: TimerTask? = null

    init {
        scheduleUpdateTask()
    }

    private fun scheduleUpdateTask() {
        if (!serverConfig.automaticallyTriggerGlobalUpdate) {
            return
        }

        val minInterval = 6.hours
        val interval = serverConfig.globalUpdateInterval.hours
        val updateInterval = interval.coerceAtLeast(minInterval).inWholeMilliseconds

        val lastAutomatedUpdate = preferences.getLong(lastAutomatedUpdateKey, 0)
        val initialDelay = updateInterval - (System.currentTimeMillis() - lastAutomatedUpdate) % updateInterval

        currentUpdateTask?.cancel()
        currentUpdateTask = object : TimerTask() {
            override fun run() {
                preferences.putLong(lastAutomatedUpdateKey, System.currentTimeMillis())

                if (status.value.running) {
                    logger.debug { "Global update is already in progress, do not trigger global update" }
                    return
                }

                logger.info { "Trigger global update (interval= ${serverConfig.globalUpdateInterval}h, lastAutomatedUpdate= ${Date(lastAutomatedUpdate)})" }
                addCategoriesToUpdateQueue(Category.getCategoryList(), true)
            }
        }

        updateTimer.scheduleAtFixedRate(currentUpdateTask, initialDelay, updateInterval)
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
                    _status.value = UpdateStatus(
                        process(job),
                        tracker.any { (_, job) ->
                            job.status == JobStatus.PENDING || job.status == JobStatus.RUNNING
                        }
                    )
                }
            }
            .catch { logger.error(it) { "Error during updates" } }
            .launchIn(scope)
        return channel
    }

    private suspend fun process(job: UpdateJob): List<UpdateJob> {
        tracker[job.manga.id] = job.copy(status = JobStatus.RUNNING)
        _status.update { UpdateStatus(tracker.values.toList(), true) }
        tracker[job.manga.id] = try {
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

    override fun addCategoriesToUpdateQueue(categories: List<CategoryDataClass>, clear: Boolean?) {
        val updater by DI.global.instance<IUpdater>()
        if (clear == true) {
            updater.reset()
        }

        val includeInUpdateStatusToCategoryMap = categories.groupBy { it.includeInUpdate }
        val excludedCategories = includeInUpdateStatusToCategoryMap[IncludeInUpdate.EXCLUDE].orEmpty()
        val includedCategories = includeInUpdateStatusToCategoryMap[IncludeInUpdate.INCLUDE].orEmpty()
        val unsetCategories = includeInUpdateStatusToCategoryMap[IncludeInUpdate.UNSET].orEmpty()
        val categoriesToUpdate = includedCategories.ifEmpty { unsetCategories }

        logger.debug { "Updating categories: '${categoriesToUpdate.joinToString("', '") { it.name }}'" }

        val categoriesToUpdateMangas = categoriesToUpdate
            .flatMap { CategoryManga.getCategoryMangaList(it.id) }
            .distinctBy { it.id }
        val mangasToCategoriesMap = CategoryManga.getMangasCategories(categoriesToUpdateMangas.map { it.id })
        val mangasToUpdate = categoriesToUpdateMangas
            .asSequence()
            .filter { it.updateStrategy == UpdateStrategy.ALWAYS_UPDATE }
            .filter { if (serverConfig.excludeUnreadChapters) { (it.unreadCount ?: 0L) == 0L } else true }
            .filter { if (serverConfig.excludeNotStarted) { it.lastReadAt != null } else true }
            .filter { if (serverConfig.excludeCompleted) { it.status != MangaStatus.COMPLETED.name } else true }
            .filter { !excludedCategories.any { category -> mangasToCategoriesMap[it.id]?.contains(category) == true } }
            .toList()

        // In case no manga gets updated and no update job was running before, the client would never receive an info about its update request
        if (mangasToUpdate.isEmpty()) {
            UpdaterSocket.notifyAllClients(UpdateStatus())
            return
        }

        addMangasToQueue(
            mangasToUpdate
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, MangaDataClass::title))
        )
    }

    private fun addMangasToQueue(mangas: List<MangaDataClass>) {
        mangas.forEach { tracker[it.id] = UpdateJob(it) }
        _status.update { UpdateStatus(tracker.values.toList(), mangas.isNotEmpty()) }
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
        _status.update { UpdateStatus() }
        updateChannels.forEach { (_, channel) -> channel.cancel() }
        updateChannels.clear()
    }
}
