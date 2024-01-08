package ru.fomenkov.utils

import ru.fomenkov.data.Module
import java.io.File

object CompilerModuleNameResolver {

    fun getPossibleBuildPaths(module: Module) = setOf(
        "${module.path}/build/tmp/kotlin-classes",
        "${module.path}/build/classes/kotlin",
    )

    fun resolve(paths: Set<String>): String {
        paths.forEach { path ->
            if (File(path).exists()) {
                val dir = File(path)
                    .listFiles { file ->
                        file.isDirectory && !file.name.lowercase().contains("release") && !file.name.lowercase().contains("test")
                    }
                    ?.sortedByDescending(File::lastModified)
                    ?.firstOrNull()

                if (dir != null) {
                    val parts = path.split('/')
                    var lastDirName = ""

                    parts.forEach { part ->
                        if (part == "build" && lastDirName.isNotBlank()) {
                            return "${lastDirName.replace('-', '_')}_${dir.name}"
                        }
                        lastDirName = part
                    }
                }
            }
        }
        return ""
    }
}