package ru.fomenkov.plugin

import ru.fomenkov.data.Module
import java.io.File

class DaggerFactoryClassPathsResolver(
    private val module: Module,
    private val sources: Set<String>,
    private val projectClasspath: Set<String>,
) {

    fun resolve(): Set<String> {
        val paths = mutableSetOf<String>()

        sources.forEach { sourcePath ->
            check(sourcePath.startsWith(module.path)) { "Source $sourcePath doesn't belong to module ${module.path}" }
            val parts = sourcePath.substring(module.path.length, sourcePath.length)
                .split('/')
                .filterNot(String::isBlank)

            // Expected source paths ('*' stands for a flavor type):
            // <module_path>/src/*/java/<package>/SomeClass.kt
            // <module_path>/src/*/kotlin/<package>/SomeClass.kt
            check(parts[0] == "src" && (parts[2] == "java" || parts[2] == "kotlin")) {
                "Failed to parse source path: $sourcePath"
            }
            val packageParts = parts.subList(3, parts.size - 1)
            val kotlinFileName = parts.last()
            check(kotlinFileName.endsWith(".kt")) { "Expecting Kotlin source file: $kotlinFileName" }

            val dotIndex = kotlinFileName.lastIndexOf('.')
            check(dotIndex != -1) { "Failed to parse source file name: $kotlinFileName" }

            val factoryClassName = kotlinFileName.substring(0, dotIndex) + "_Factory.class"
            val factoryClassPath = (packageParts + factoryClassName).joinToString(separator = "/")

            projectClasspath
                .filter { it.contains(module.path) }
                .forEach { path ->
                    val file = File("$path/$factoryClassPath")

                    if (file.exists()) {
                        paths += file.absolutePath
                    }
                }
        }
        return paths
    }
}