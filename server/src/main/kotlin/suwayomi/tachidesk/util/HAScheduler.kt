
package suwayomi.tachidesk.util

import com.cronutils.model.CronType.CRON4J
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import it.sauronsoftware.cron4j.Scheduler
import it.sauronsoftware.cron4j.Task
import it.sauronsoftware.cron4j.TaskExecutionContext
import mu.KotlinLogging
import java.time.ZonedDateTime
import java.util.PriorityQueue
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val cronParser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CRON4J))

class HATask(val id: String, val cronExpr: String, val execute: () -> Unit, val name: String?) : Comparable<HATask> {
    private val executionTime = ExecutionTime.forCron(cronParser.parse(cronExpr))

    fun getLastExecutionTime(): Long {
        return executionTime.lastExecution(ZonedDateTime.now()).get().toEpochSecond().seconds.inWholeMilliseconds
    }

    fun getNextExecutionTime(): Long {
        return executionTime.nextExecution(ZonedDateTime.now()).get().toEpochSecond().seconds.inWholeMilliseconds
    }

    fun getTimeToNextExecution(): Long {
        return executionTime.timeToNextExecution(ZonedDateTime.now()).get().toMillis()
    }

    override fun compareTo(other: HATask): Int {
        return getTimeToNextExecution().compareTo(other.getTimeToNextExecution())
    }
}

/**
 * The "HAScheduler" ("HibernateAwareScheduler") is a scheduler that recognizes when the system was hibernating/suspended
 * and triggers tasks that have missed their execution points.
 */
object HAScheduler {
    private val logger = KotlinLogging.logger { }

    private val scheduledTasks = PriorityQueue<HATask>()
    private val scheduler = Scheduler()

    private val HIBERNATION_THRESHOLD = 10.seconds.inWholeMilliseconds
    private const val TASK_THRESHOLD = 0.1

    init {
        scheduleHibernateCheckerTask(1.minutes)
    }

    private fun scheduleHibernateCheckerTask(interval: Duration) {
        val timer = Timer()
        timer.scheduleAtFixedRate(
            object : TimerTask() {
                var lastExecutionTime = System.currentTimeMillis()

                override fun run() {
                    val currentTime = System.currentTimeMillis()
                    val elapsedTime = currentTime - lastExecutionTime
                    lastExecutionTime = currentTime

                    val systemWasInHibernation = elapsedTime > interval.inWholeMilliseconds + HIBERNATION_THRESHOLD
                    if (systemWasInHibernation) {
                        logger.debug { "System hibernation detected, task was delayed by ${elapsedTime - interval.inWholeMilliseconds}ms" }
                        scheduledTasks.forEach {
                            val missedExecution = currentTime - it.getLastExecutionTime() - elapsedTime < 0
                            val taskInterval = it.getNextExecutionTime() - it.getLastExecutionTime()
                            // in case the next task execution doesn't take long the missed execution can be ignored to prevent a double execution
                            val taskThresholdMet = taskInterval * TASK_THRESHOLD > it.getTimeToNextExecution()

                            val triggerTask = missedExecution && taskThresholdMet
                            if (triggerTask) {
                                logger.debug { "Task \"${it.name ?: it.id}\" missed its execution, executing now..." }
                                rescheduleCron(it.id, it.cronExpr)
                                it.execute()
                            }

                            // queue is ordered by next execution time, thus, loop can be exited early
                            if (!missedExecution) {
                                return@forEach
                            }
                        }
                    }
                }
            },
            interval.inWholeMilliseconds,
            interval.inWholeMilliseconds
        )
    }

    fun scheduleCron(execute: () -> Unit, cronExpr: String, name: String?): String {
        if (!scheduler.isStarted) {
            scheduler.start()
        }

        val taskId = scheduler.schedule(
            cronExpr,
            object : Task() {
                override fun execute(context: TaskExecutionContext?) {
                    execute()
                }
            }
        )

        scheduledTasks.add(HATask(taskId, cronExpr, execute, name))

        return taskId
    }

    fun descheduleCron(taskId: String) {
        scheduler.deschedule(taskId)
        scheduledTasks.removeIf { it.id == taskId }
    }

    fun rescheduleCron(taskId: String, cronExpr: String) {
        val task = scheduledTasks.find { it.id == taskId } ?: return

        scheduledTasks.remove(task)
        scheduledTasks.add(HATask(taskId, cronExpr, task.execute, task.name))

        scheduler.reschedule(taskId, cronExpr)
    }
}
