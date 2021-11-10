package suwayomi.tachidesk.manga.impl.update

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mu.KotlinLogging
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

class Updater : IUpdater {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var isRunning: Boolean = false

    private var queue = ArrayList<UpdateJob>()
    private var updateChannel = Channel<UpdateJob>()
    private val statusChannel = MutableStateFlow(UpdateStatus())

    init {
        logger.info { "Updater initialized" }
        scope.launch {
            while (true) {
                if (updateChannel.isEmpty && !isRunning) {
                    isRunning = false
                }
                val job = updateChannel.receive()
                try {
                    logger.info { "Updating ${job.manga.title}" }
                    Chapter.getChapterList(job.manga.id, true)
                    job.status = JobStatus.COMPLETE
                } catch (e: Exception) {
                    logger.error(e) { "Error while updating ${job.manga.title}" }
                    job.status = JobStatus.FAILED
                }
                queue.add(job)
                statusChannel.emit(UpdateStatus(queue, !updateChannel.isEmpty))
                if (updateChannel.isEmpty) {
                    isRunning = false
                }
            }
        }
    }

    override fun addMangaToQueue(manga: MangaDataClass) {
        GlobalScope.launch { updateChannel.send(UpdateJob(manga)); }
    }

    override fun getStatus(): StateFlow<UpdateStatus> {
        return statusChannel
    }
}
