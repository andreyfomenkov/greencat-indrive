package ru.fomenkov.utils

object Log {

    enum class Level { INFO, DEBUG, VERBOSE }
    var level = Level.INFO

    private val debugLogsCollector = mutableListOf<String>()

    fun i(message: String) {
        if (level == Level.INFO || level == Level.DEBUG || level == Level.VERBOSE) {
            println(message)
        }
    }

    fun d(message: String) {
        if (level == Level.DEBUG || level == Level.VERBOSE) {
            println(message)
        }
        debugLogsCollector += message
    }

    fun v(message: String) {
        v(true, message)
    }

    fun v(display: Boolean, message: String) {
        if (display && level == Level.VERBOSE) {
            println(message)
        }
        debugLogsCollector += message
    }

    fun printDebugLogs() {
        debugLogsCollector.forEach(::println)
    }

    fun getDebugLogs() = debugLogsCollector.toList()
}