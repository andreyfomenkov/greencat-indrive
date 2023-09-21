package ru.fomenkov.parser

import java.io.File

class LibVersionsParser(private val path: String) {

    fun parse(): Map<String, String> {
        val versions = mutableMapOf<String, String>()
        val file = File(path)
        check(file.exists()) { "Lib versions file not found: $path" }

        file.readLines()
            .map(String::trim)
            .filterNot { line -> line.isBlank() || line.startsWith('[') || line.startsWith('#') }
            .forEach { line ->
                val parts = line.split('=').map(String::trim)

                if (parts.size == 2) {
                    val libName = parts[0]
                    val libVersion = parts[1]
                        .replace("\"", "")
                        .replace("'", "")
                    versions += libName to libVersion
                }
            }

        return versions
    }
}