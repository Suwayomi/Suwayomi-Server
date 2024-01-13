
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
import java.util.Date
import java.util.PriorityQueue
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val cronParser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CRON4J))

abstract class BaseHATask(
    val id: String,
    val execute: () -> Unit,
    val name: String?,
) : Comparable<BaseHATask> {
    abstract fun getLastExecutionTime(): Long

    abstract fun getNextExecutionTime(): Long

    abstract fun getTimeToNextExecution(): Long

    override fun compareTo(other: BaseHATask): Int {
        return getTimeToNextExecution().compareTo(other.getTimeToNextExecution())
    }

    override fun toString(): String {
        return "Task \"$name\" ($id) " +
            "lastExecution= ${Date(getLastExecutionTime())} " +
            "nextExecution= ${Date(getNextExecutionTime())}"
    }
}

class HACronTask(
    id: String,
    val cronExpr: String,
    execute: () -> Unit,
    name: String?,
) : BaseHATask(id, execute, name) {
    private val executionTime = ExecutionTime.forCron(cronParser.parse(cronExpr))

    override fun getLastExecutionTime(): Long {
        return executionTime.lastExecution(ZonedDateTime.now())
            .get()
            .toEpochSecond()
            .seconds
            .inWholeMilliseconds
    }

    override fun getNextExecutionTime(): Long {
        return executionTime.nextExecution(ZonedDateTime.now())
            .get()
            .toEpochSecond()
            .seconds
            .inWholeMilliseconds
    }

    override fun getTimeToNextExecution(): Long {
        return executionTime.timeToNextExecution(ZonedDateTime.now()).get().toMillis()
    }

    override fun toString(): String {
        return "${super.toString()} interval= $cronExpr"
    }
}

class HATask(
    id: String,
    val interval: Long,
    execute: () -> Unit,
    val timerTask: TimerTask,
    name: String?,
    val initialDelay: Long = interval,
) : BaseHATask(
        id,
        execute,
        name,
    ) {
    private val firstExecutionTime = System.currentTimeMillis() + initialDelay

    private fun getElapsedTimeOfCurrentInterval(): Long {
        val timeSinceFirstExecution = System.currentTimeMillis() - firstExecutionTime
        return timeSinceFirstExecution.mod(interval)
    }

    override fun getLastExecutionTime(): Long {
        var lastExecutionTime = System.currentTimeMillis() - getElapsedTimeOfCurrentInterval()

        while (lastExecutionTime > System.currentTimeMillis()) {
            lastExecutionTime -= interval
        }

        return lastExecutionTime
    }

    override fun getNextExecutionTime(): Long {
        return System.currentTimeMillis() + getTimeToNextExecution()
    }

    override fun getTimeToNextExecution(): Long {
        return interval - getElapsedTimeOfCurrentInterval()
    }

    override fun toString(): String {
        return "${super.toString()} interval= $interval, initialDelay= $initialDelay"
    }
}

/**
 * The "HAScheduler" ("HibernateAwareScheduler") is a scheduler that recognizes when the system was hibernating/suspended
 * and triggers tasks that have missed their execution points.
 */
object HAScheduler {
    private val logger = KotlinLogging.logger { }

    private val scheduledTasks = PriorityQueue<BaseHATask>()
    private val scheduler = Scheduler()

    private val timer = Timer()

    private val HIBERNATION_THRESHOLD = 10.seconds.inWholeMilliseconds
    private const val TASK_THRESHOLD = 0.1

    init {
        scheduleHibernateCheckerTask(1.minutes)
    }

    private fun scheduleHibernateCheckerTask(interval: Duration) {
        timer.scheduleAtFixedRate(
            object : TimerTask() {
                var lastExecutionTime = System.currentTimeMillis()

                override fun run() {
                    val currentTime = System.currentTimeMillis()
                    val elapsedTime = currentTime - lastExecutionTime
                    lastExecutionTime = currentTime

                    val systemWasInHibernation = elapsedTime > interval.inWholeMilliseconds + HIBERNATION_THRESHOLD
                    if (systemWasInHibernation) {
                        logger.debug {
                            "System hibernation detected, task was delayed by " +
                                "${elapsedTime - interval.inWholeMilliseconds}ms"
                        }
                        scheduledTasks.toList().forEach {
                            val wasLastExecutionMissed = currentTime - it.getLastExecutionTime() - elapsedTime < 0
                            if (wasLastExecutionMissed) {
                                logger.debug { "$it missed its execution, executing now..." }

                                when (it) {
                                    is HATask -> reschedule(it.id, it.interval)
                                    is HACronTask -> rescheduleCron(it.id, it.cronExpr)
                                }

                                it.execute()
                            }

                            // queue is ordered by next execution time, thus, loop can be exited early
                            if (!wasLastExecutionMissed) {
                                return@forEach
                            }
                        }
                    }
                }
            },
            interval.inWholeMilliseconds,
            interval.inWholeMilliseconds,
        )
    }

    private fun createTimerTask(
        interval: Long,
        execute: () -> Unit,
    ): TimerTask {
        return object : TimerTask() {
            var lastExecutionTime: Long = 0

            override fun run() {
                // If a task scheduled via "Timer::scheduleAtFixedRate" is delayed for some reason, the Timer will
                // trigger tasks in quick succession to "catch up" to the set interval.
                //
                // We want to prevent this, since we don't care about how many executions were missed and only want
                // one execution to be triggered for these missed executions.
                //
                // The missed execution gets triggered by "HAScheduler::scheduleHibernateCheckerTask" and thus, we
                // debounce this behaviour of "Timer::scheduleAtFixedRate".
                val isCatchUpExecution = System.currentTimeMillis() - lastExecutionTime < interval - HIBERNATION_THRESHOLD
                if (isCatchUpExecution) {
                    return
                }

                lastExecutionTime = System.currentTimeMillis()
                execute()
            }
        }
    }

    fun schedule(
        execute: () -> Unit,
        interval: Long,
        delay: Long,
        name: String?,
    ): String {
        val taskId = UUID.randomUUID().toString()
        val timerTask = createTimerTask(interval, execute)

        val task = HATask(taskId, interval, execute, timerTask, name, delay)
        scheduledTasks.add(task)
        timer.scheduleAtFixedRate(timerTask, delay, interval)

        logger.debug { "schedule: $task" }

        return taskId
    }

    fun deschedule(taskId: String): HATask? {
        val task = (scheduledTasks.find { it.id == taskId } ?: return null) as HATask
        task.timerTask.cancel()
        scheduledTasks.remove(task)

        logger.debug { "deschedule: $task" }

        return task
    }

    fun reschedule(
        taskId: String,
        interval: Long,
    ) {
        val task = deschedule(taskId) ?: return

        val timerTask = createTimerTask(interval, task.execute)

        val timeToNextExecution = task.getTimeToNextExecution()
        val intervalDifference = interval - task.interval
        val remainingTimeTillNextExecution = (timeToNextExecution + intervalDifference).coerceAtLeast(0)

        val updatedTask = HATask(taskId, interval, task.execute, timerTask, task.name, initialDelay = remainingTimeTillNextExecution)
        scheduledTasks.add(updatedTask)
        timer.scheduleAtFixedRate(timerTask, remainingTimeTillNextExecution, interval)

        logger.debug { "reschedule: new= $updatedTask, old= $task" }
    }

    fun scheduleCron(
        execute: () -> Unit,
        cronExpr: String,
        name: String?,
    ): String {
        if (!scheduler.isStarted) {
            scheduler.start()
        }

        val taskId =
            scheduler.schedule(
                cronExpr,
                object : Task() {
                    override fun execute(context: TaskExecutionContext?) {
                        execute()
                    }
                },
            )

        val task = HACronTask(taskId, cronExpr, execute, name)
        scheduledTasks.add(task)

        logger.debug { "scheduleCron: $task" }

        return taskId
    }

    fun descheduleCron(taskId: String) {
        scheduler.deschedule(taskId)
        val task = scheduledTasks.find { it.id == taskId } ?: return
        scheduledTasks.remove(task)

        logger.debug { "descheduleCron: $task" }
    }

    fun rescheduleCron(
        taskId: String,
        cronExpr: String,
    ) {
        val task = scheduledTasks.find { it.id == taskId } ?: return

        val updatedTask = HACronTask(taskId, cronExpr, task.execute, task.name)
        scheduledTasks.remove(task)
        scheduledTasks.add(updatedTask)

        scheduler.reschedule(taskId, cronExpr)

        logger.debug { "rescheduleCron: new= $updatedTask, old= $task" }
    }
}
