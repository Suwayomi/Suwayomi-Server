package suwayomi.tachidesk.manga.impl.update

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
import mu.KotlinLogging
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import java.util.concurrent.ConcurrentHashMap

class Updater : IUpdater {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow(UpdateStatus())
    override val status = _status.asStateFlow()

    private val tracker = ConcurrentHashMap<Int, UpdateJob>()
    private var updateChannel = createUpdateChannel()

    private fun createUpdateChannel(): Channel<UpdateJob> {
        val channel = Channel<UpdateJob>(Channel.UNLIMITED)
        channel.consumeAsFlow()
            .onEach { job ->
                _status.value = UpdateStatus(
                    process(job),
                    tracker.any { (_, job) ->
                        job.status == JobStatus.PENDING || job.status == JobStatus.RUNNING
                    }
                )
            }
            .catch { logger.error(it) { "Error during updates" } }
            .launchIn(scope)
        return channel
    }

    private suspend fun process(job: UpdateJob): List<UpdateJob> {
        tracker[job.manga.id] = job.copy(status = JobStatus.RUNNING)
        _status.update { UpdateStatus(tracker.values.toList(), true) }
        tracker[job.manga.id] = try {
            logger.info { "Updating ${job.manga.title}" }
            Chapter.getChapterList(job.manga.id, true)
            job.copy(status = JobStatus.COMPLETE)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.error(e) { "Error while updating ${job.manga.title}" }
            job.copy(status = JobStatus.FAILED)
        }
        return tracker.values.toList()
    }

    override fun addMangaToQueue(manga: MangaDataClass) {
        scope.launch {
            updateChannel.send(UpdateJob(manga))
        }
        tracker[manga.id] = UpdateJob(manga)
        _status.update { UpdateStatus(tracker.values.toList(), true) }
    }

    override fun reset() {
        scope.coroutineContext.cancelChildren()
        tracker.clear()
        _status.update { UpdateStatus() }
        updateChannel.cancel()
        updateChannel = createUpdateChannel()
    }
}
