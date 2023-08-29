package ru.fomenkov.parser

class AdbDevicesParser(private val input: List<String>) {

    fun parse(): Result {
        val lines = input
            .filterNot(String::isBlank)
            .filterNot { line -> line.trim().startsWith("List of devices", ignoreCase = true) }
            .filter { line -> line.split('\t').lastOrNull() == "device" }

        return when (lines.size) {
            0 -> Result.NO_DEVICES
            1 -> Result.SUCCESS
            else -> Result.MULTIPLE_DEVICES
        }
    }

    enum class Result {
        SUCCESS,
        NO_DEVICES,
        MULTIPLE_DEVICES,
    }
}