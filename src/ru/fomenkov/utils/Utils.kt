package ru.fomenkov.utils

import ru.fomenkov.data.Module

object Utils {

    fun timeMsec(action: () -> Unit): Long {
        val start = System.currentTimeMillis()
        action()
        val end = System.currentTimeMillis()

        return end - start
    }

    fun toModule(name: String) = Module(
        name = name,
        path = name.replace(':', '/'),
    )

    fun isSourceFileSupported(path: String): Boolean {
        with(path.trim()) {
            if (endsWith(".kt")) {
                return true
            }
        }
        return false
    }
}