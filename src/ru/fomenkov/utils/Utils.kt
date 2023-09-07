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

    fun composeClassEntries(paths: Set<String>) =
        paths.map { path ->
            val index = if (path.contains('$')) {
                path.indexOf('$')
            } else {
                path.indexOf(".class")
            }
            check(index != -1) { "Failed to parse class file path: $path" }
            path.substring(0, index)
        }
            .toSet()
}