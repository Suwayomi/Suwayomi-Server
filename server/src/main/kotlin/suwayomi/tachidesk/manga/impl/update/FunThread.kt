package suwayomi.tachidesk.manga.impl.update

import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance

object FunThread : Thread() {
    private val logger = KotlinLogging.logger {}
    private val updater by DI.global.instance<IUpdater>()

    override fun run() {
        while (true) {
            sleep(5000)
            updater.getStatus().subscribe({
                logger.info { "Updater status: ${it.running} ${it.statusMap.getOrDefault(JobStatus.PENDING, ArrayList()).size}" }
            }, {
                logger.error { "Updater status error: $it" }
            })

            logger.info { "FunThread" }
        }
    }
}
