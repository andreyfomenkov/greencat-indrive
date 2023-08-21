package ru.fomenkov.parser

import ru.fomenkov.data.Dependency
import ru.fomenkov.utils.Log
import java.io.File

class BuildGradleParser(private val path: String) {

    fun parse() = when {
        path.endsWith(".gradle") -> parseGroovyFile()
        path.endsWith(".kts") -> parseKtsFile()
        else -> error("Unknown build file: $path")
    }

    private fun parseGroovyFile(): Set<Dependency> {
        val entities = mutableSetOf<Dependency>()
        var inDepsBlock = false
        var isTransitive = false

        File(path).readLines()
            .map(String::trim)
            .forEach { line ->
                if (inDepsBlock) {
                    if (line.startsWith("project")) {
                        val str = line.replace(" ", "")
                            .replace('"', '\'')
                            .replace(",", "")
                            .replace("path:", "")
                        val startIndex = str.indexOf("(':")

                        if (startIndex == -1) {
                            error("Unable to parse module name in project block (line = $line)")
                        }
                        val endIndex = str.indexOf("')")

                        if (endIndex == -1) {
                            error("Unable to parse module name in project block (line = $line)")
                        }
                        val moduleName = str.substring(startIndex + 3, endIndex)
                        entities += Dependency.Module(moduleName, isTransitive)

                    } else if (line.startsWith("implementation")) {
                        isTransitive = false
                    } else if (line.startsWith("api")) {
                        isTransitive = true
                    }
                } else if (line.replace(" ", "").startsWith("dependencies{")) {
                    inDepsBlock = true
                }
            }
        if (!inDepsBlock) {
            Log.v("No dependencies block found in $path")
        }
        return entities
    }

    private fun parseKtsFile(): Set<Dependency> {
        val entities = mutableSetOf<Dependency>()
        var inDepsBlock = false

        File(path).readLines()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { line ->
                if (inDepsBlock) {
                    var isTransitive: Boolean? = null
                    val str = line.replace(" ", "")
                        .replace('"', '\'')
                        .replace(",", "")
                        .replace("path:", "")

                    if (str.startsWith("implementation(project(")) {
                        isTransitive = false
                    } else if (str.startsWith("api(project(")) {
                        isTransitive = true
                    }
                    if (isTransitive != null) {
                        val startIndex = str.indexOf("('")

                        if (startIndex == -1) {
                            error("Unable to parse module name in project block (line = $line)")
                        }
                        val endIndex = str.indexOf("')")

                        if (endIndex == -1) {
                            error("Unable to parse module name in project block (line = $line)")
                        }
                        val moduleName = str.substring(startIndex + 3, endIndex)
                        entities += Dependency.Module(moduleName, isTransitive)
                    }
                } else if (line.replace(" ", "").startsWith("dependencies{")) {
                    inDepsBlock = true
                }
            }
        return entities
    }
}