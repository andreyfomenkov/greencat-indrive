package ru.fomenkov.async

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

class WorkerTaskExecutor(
    threadsCount: Int = Runtime.getRuntime().availableProcessors()
) {
    private val executor = Executors.newFixedThreadPool(threadsCount)

    fun <T> run(tasks: List<Callable<T>>): List<T> {
        if (executor.isShutdown) {
            error("Internal executor has been released")
        }
        return tasks
            .map(executor::submit)
            .map(Future<T>::get)
    }

    fun release() {
        if (!executor.isShutdown) {
            executor.shutdown()
        }
    }
}