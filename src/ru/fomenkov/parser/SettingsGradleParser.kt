package ru.fomenkov.parser

import ru.fomenkov.data.Module
import ru.fomenkov.utils.Log
import java.io.File

class SettingsGradleParser(private val path: String) {

    fun parse(): Set<Module> {
        val modules = mutableSetOf<Module>()

        File(path).readLines()
            .asSequence()
            .map(String::trim)
            .filter { line -> line.startsWith("include ") || line.startsWith("':") }
            .forEach { line ->
                val startIndex = line.indexOfFirst { c -> c == ':' }
                val endIndex = line.indexOfLast { c -> c == '\'' }

                if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
                    error("Failed to parse module name: $line")
                }
                val name = line.subSequence(startIndex + 1, endIndex).toString()
                val path = name.replace(':', '/')
                val module = Module(name, path)
                val exists = File(path).exists()

                if (exists) {
                    modules += module
                } else {
                    Log.v("Excluding module path, because it doesn't exist: $path")
                }
            }
        return modules
    }
}