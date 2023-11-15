package ru.fomenkov.parser

class AdbDevicesParser(private val input: List<String>) {

    fun parse(): Result {
        val onlineDevices = input
            .filterNot(String::isBlank)
            .filterNot { line -> line.trim().startsWith("List of devices", ignoreCase = true) }
            .filter { line -> line.split('\t').lastOrNull() == "device" }
            .map { line -> line.split('\t').first().trim() }

        return when (onlineDevices.size) {
            0 -> Result.NoDevices
            1 -> Result.SingleDevice(serialId = onlineDevices.first())
            else -> Result.MultipleDevices
        }
    }

    sealed class Result {
        data class SingleDevice(val serialId: String) : Result()
        data object NoDevices : Result()
        data object MultipleDevices : Result()
    }
}