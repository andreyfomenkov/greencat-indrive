package ru.fomenkov.plugin

import ru.fomenkov.shell.Shell.exec
import java.io.File

class FinalBuildDirectoryCleaner(private val finalBuildPath: String) {

    fun clean(sourcePaths: Set<String>): Set<String> {
        val allPaths = exec("find $finalBuildPath -name '*.class'").let { result ->
            check(result.successful) { "Failed to execute `find` command" }
            result.output
        }
        val parts = sourcePaths
            .map { path ->
                val parts = path.split('/')
                var index = -1

                for (i in 0 until parts.size - 2) {
                    // Find /src/*/java or /src/*/kotlin path entry
                    if (parts[i] == "src" && (parts[i + 2] == "java" || parts[i + 2] == "kotlin")) {
                        index = i + 3
                        break
                    }
                }
                check(index != -1) { "Failed to detect src directory for source path: $path" }
                parts.subList(index, parts.size).joinToString(separator = "/")
            }
            .map { path ->
                val index = path.lastIndexOf('.')
                check(index != -1) { "Failed to parse path: $path" }
                path.substring(0, index)
            }
        val pathsToRemove = mutableSetOf<String>()

        allPaths.forEach { path ->
            parts.forEach { part ->
                if (path.contains("/$part.class") || path.contains("/$part\$")) {
                    pathsToRemove += path
                }
            }
        }
        val pathsRemoved = mutableSetOf<String>()
        pathsToRemove.forEach { path ->
            if (File(path).delete()) {
                pathsRemoved += path
            }
        }
        return pathsRemoved
    }
}