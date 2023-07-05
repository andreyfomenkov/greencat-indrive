package ru.fomenkov.parser

import ru.fomenkov.data.Dependency
import ru.fomenkov.utils.Log
import java.io.File


class BuildGradleParser(private val path: String) {

    fun parse(): Set<Dependency> {
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
}