package ru.fomenkov.plugin.dagger

import ru.fomenkov.data.Module
import java.io.File

class DaggerGeneratedClassesResolver(
    private val module: Module,
    private val sources: Set<String>,
    private val projectClasspath: Set<String>,
) {

    private val factoryClassSuffix = "_Factory.class"
    private val membersInjectorClassSuffix = "_MembersInjector.class"

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

            val factoryClassName = kotlinFileName.substring(0, dotIndex) + factoryClassSuffix
            val membersInjectorClassName = kotlinFileName.substring(0, dotIndex) + membersInjectorClassSuffix
            val generatedClassPath = packageParts.joinToString(separator = "/")

            projectClasspath
                .filter { it.contains(module.path) }
                .forEach { path ->
                    val factoryClassFile = File("$path/$generatedClassPath/$factoryClassName")
                    val membersInjectorClassFile = File("$path/$generatedClassPath/$membersInjectorClassName")

                    if (factoryClassFile.exists()) {
                        paths += factoryClassFile.absolutePath
                    }
                    if (membersInjectorClassFile.exists()) {
                        paths += membersInjectorClassFile.absolutePath
                    }
                }
        }
        return paths
    }
}