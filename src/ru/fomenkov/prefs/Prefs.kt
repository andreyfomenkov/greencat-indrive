package ru.fomenkov.prefs

import java.io.File
import java.lang.StringBuilder

class Prefs(private val filePath: String) {

    fun put(prefKey: String, prefValue: String) {
        require(!prefKey.trim().contains(' ')) { "Key must not contain spaces" }
        require(!prefValue.trim().contains(' ')) { "Value must not contain spaces" }

        val file = File(filePath)
        val map = if (file.exists()) {
            mutableMapOf<String, String>().apply {
                file.readLines()
                    .forEach { line ->
                        val parts = line.split(' ')
                        check(parts.size == 2) { "Failed to parse $filePath" }

                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        put(key, value)
                    }
            }
        } else {
            mutableMapOf()
        }
        map += prefKey.trim() to prefValue.trim()

        StringBuilder().apply {
            map.forEach { (key, value) -> append("$key $value\n") }
            file.writeText(toString())
        }
    }

    fun get(prefKey: String): String {
        val file = File(filePath)

        if (!file.exists()) {
            return ""
        }
        file.readLines()
            .map { line ->
                val parts = line.split(' ')
                check(parts.size == 2) { "Failed to parse $filePath" }

                val key = parts[0].trim()
                val value = parts[1].trim()

                if (key == prefKey) {
                    return value
                }
            }
        return ""
    }
}