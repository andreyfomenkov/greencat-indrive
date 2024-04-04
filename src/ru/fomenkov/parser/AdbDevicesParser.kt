package ru.fomenkov.parser

class AdbDevicesParser(private val input: List<String>) {

    fun parse(): List<String> {
        return input
            .filterNot(String::isBlank)
            .filterNot { line -> line.trim().startsWith("List of devices", ignoreCase = true) }
            .filter { line -> line.split('\t').lastOrNull() == "device" }
            .map { line -> line.split('\t').first().trim() }
    }
}