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
            // Unit tests are useless for patching + they can contain method names with spaces (not supported by ART)
            if (contains("/src/test/java/") || contains("/src/test/kotlin/")) {
                return false
            }
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

    /**
     * Input:  some/app/module/src/main/java/com/my/Class.kt
     * Output: com/my/Class.kt (removeExtension == false), com/my/Class (removeExtension == true)
     */
    fun extractSourceFilePathInModule(path: String, removeExtension: Boolean): String {
        val parts = path.split('/')
        var srcDirIndex = -1

        for (i in 0 until parts.size - 2) {
            // Find /src/*/java or /src/*/kotlin path entry
            if (parts[i] == "src" && (parts[i + 2] == "java" || parts[i + 2] == "kotlin")) {
                srcDirIndex = i + 3
                break
            }
        }
        check(srcDirIndex != -1) { "Failed to parse source file path in module: $path" }
        val output = parts.subList(srcDirIndex, parts.size).joinToString(separator = "/")

        return if (removeExtension) {
            val dotIndex = output.lastIndexOf(".")

            if (dotIndex == -1) {
                output
            } else {
                output.substring(0, dotIndex)
            }
        } else {
            output
        }
    }

    fun printTextInFrame(text: String) {
        val vItem = '|'
        val hItem = '-'
        val padding = 4
        val line = hItem.toString().repeat(text.length + padding)
        val spaces = " ".repeat(text.length + padding)
        Log.i("+$line+")
        Log.i("$vItem$spaces$vItem")
        Log.i("$vItem  $text  $vItem")
        Log.i("$vItem$spaces$vItem")
        Log.i("+$line+")
    }
}