package ru.fomenkov.utils

import ru.fomenkov.Settings

object Log {

    fun d(message: String) {
        println(message)
    }

    fun e(message: String) {
        println(message)
    }

    fun v(message: String) {
        if (Settings.isVerboseMode) {
            println(message)
        }
    }
}