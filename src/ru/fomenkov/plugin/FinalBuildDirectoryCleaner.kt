package ru.fomenkov.plugin

import ru.fomenkov.shell.Shell.exec
import java.io.File

class FinalBuildDirectoryCleaner(private val finalBuildPath: String) {

    fun clean(sourcePaths: Set<String>) {
        val allPaths = exec("find $finalBuildPath -name '*.class'").let { result ->
            check(result.successful) { "Failed to execute `find` command" }
            result.output
        }
        val parts = sourcePaths
            .map { path -> "$finalBuildPath/$path" }
            .map { path ->
                val index = path.lastIndexOf('.')
                check(index != -1) { "Failed to parse path: $path" }
                path.substring(0, index)
            }
        val pathsToRemove = mutableSetOf<String>()

        allPaths.forEach { path ->
            parts.forEach { part ->
                if (path == "$part.class" || path.contains("$part\$")) {
                    pathsToRemove += path
                }
            }
        }
        pathsToRemove.forEach { path -> File(path).delete() }
    }
}