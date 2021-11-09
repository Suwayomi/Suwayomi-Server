package suwayomi.tachidesk.manga.impl.update

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import rx.Observable
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import java.util.concurrent.TimeUnit

class UpdateQueue : IUpdater, Thread() {
    private val logger = KotlinLogging.logger {}
    private var isRunning: Boolean = false
    private var status: Observable<UpdateStatus> = Observable.never()
    override fun getStatus(): Observable<UpdateStatus> {
        return Observable.interval(1, TimeUnit.SECONDS).map {
            UpdateStatus(queue, isRunning)
        }
    }

    private val queue = ArrayList<UpdateJob>()

    override fun addMangaToQueue(manga: MangaDataClass) {
        queue.add(UpdateJob(manga))
        startUpdater()
    }

    override fun isUpdaterRunning(): Boolean {
        return isRunning
    }

    override fun listJobs(): List<UpdateJob> {
        return queue
    }

    override fun startUpdater() {
        isRunning = true
        if (!isAlive) {
            start()
        }
    }

    override fun resetUpdater() {
        logger.info { "Resetting updater" }
        isRunning = false
        queue.clear()
    }

    override fun run() {
        while (true) {
            if (!isRunning) {
                interrupt()
                continue
            }
            val job = queue.firstOrNull { it.status == JobStatus.PENDING }
            if (job == null) {
                logger.info { "No jobs to process" }
                resetUpdater()
                continue
            }
            job.status = JobStatus.RUNNING
            try {
                logger.info { "Updating ${job.manga.title}" }
                runBlocking { Chapter.getChapterList(job.manga.id, true) }
                job.status = JobStatus.COMPLETE
            } catch (e: Exception) {
                logger.error(e) { "Error while updating ${job.manga.title}" }
                job.status = JobStatus.FAILED
            }
//            status = UpdateStatus(queue, isRunning)
        }
    }
}
