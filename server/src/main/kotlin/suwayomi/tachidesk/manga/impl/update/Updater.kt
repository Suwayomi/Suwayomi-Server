package suwayomi.tachidesk.manga.impl.update

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import mu.KotlinLogging
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

class Updater : IUpdater {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var isRunning: Boolean = false

    private var tracker = mutableMapOf<String, UpdateJob>()
    private var updateChannel = Channel<UpdateJob>()
    private val statusChannel = MutableStateFlow(UpdateStatus())
    private var updateJob: Job? = null

    init {
        updateJob = createUpdateJob()
    }

    private fun createUpdateJob(): Job {
        return scope.launch {
            while (true) {
                val job = updateChannel.receive()
                process(job)
                isRunning = !updateChannel.isEmpty
                statusChannel.value = UpdateStatus(tracker.values.toList(), isRunning)
            }
        }
    }

    private suspend fun process(job: UpdateJob) {
        isRunning = true
        job.status = JobStatus.RUNNING
        tracker["${job.manga.id}"] = job
        statusChannel.value = UpdateStatus(tracker.values.toList(), isRunning)
        try {
            logger.info { "Updating ${job.manga.title}" }
            Chapter.getChapterList(job.manga.id, true)
            job.status = JobStatus.COMPLETE
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.error(e) { "Error while updating ${job.manga.title}" }
            job.status = JobStatus.FAILED
        }
        tracker["${job.manga.id}"] = job
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun addMangaToQueue(manga: MangaDataClass) {
        scope.launch {
            updateChannel.send(UpdateJob(manga))
        }
        tracker["${manga.id}"] = UpdateJob(manga)
        statusChannel.value = UpdateStatus(tracker.values.toList(), isRunning)
    }

    override fun getStatus(): StateFlow<UpdateStatus> {
        return statusChannel
    }

    override suspend fun reset() {
        isRunning = false
        tracker.clear()
        updateChannel.cancel()
        updateJob?.cancel("Reset")
        updateChannel = Channel<UpdateJob>()
        updateJob = createUpdateJob()
    }
}
