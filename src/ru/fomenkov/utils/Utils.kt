package ru.fomenkov.utils

import ru.fomenkov.data.Module

object Utils {

    inline fun timeMsec(action: () -> Unit): Long {
        val start = System.currentTimeMillis()
        action()
        val end = System.currentTimeMillis()

        return end - start
    }

    fun toModule(name: String) = Module(
        name = name,
        path = name.replace(':', '/'),
    )
}