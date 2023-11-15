package ru.fomenkov.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class AdbDevicesParserTest {

    @Test
    fun `Test no devices connected output`() {
        val output = listOf(
            "List of devices attached",
            "",
        )
        assertEquals(
            AdbDevicesParser.Result.NoDevices,
            AdbDevicesParser(output).parse(),
        )
    }

    @Test
    fun `Test one device connected output`() {
        val output = listOf(
            "List of devices attached",
            "420051a5d6c3a465\tdevice",
            "",
        )
        assertEquals(
            AdbDevicesParser.Result.SingleDevice(serialId = "420051a5d6c3a465"),
            AdbDevicesParser(output).parse(),
        )
    }

    @Test
    fun `Test one device connected and one offline output`() {
        val output = listOf(
            "List of devices attached",
            "420051a5d6c3a465\tdevice",
            "1234567890123456\toffline",
            "",
        )
        assertEquals(
            AdbDevicesParser.Result.SingleDevice(serialId = "420051a5d6c3a465"),
            AdbDevicesParser(output).parse(),
        )
    }

    @Test
    fun `Test multiple devices connected output`() {
        val output = listOf(
            "List of devices attached",
            "420051a5d6c3a465\tdevice",
            "1234567890123456\tdevice",
            "",
        )
        assertEquals(
            AdbDevicesParser.Result.MultipleDevices,
            AdbDevicesParser(output).parse(),
        )
    }

    @Test
    fun `Test one offline device connected output`() {
        val output = listOf(
            "List of devices attached",
            "420051a5d6c3a465\toffline",
            "",
        )
        assertEquals(
            AdbDevicesParser.Result.NoDevices,
            AdbDevicesParser(output).parse(),
        )
    }

    @Test
    fun `Test one unauthorized device connected output`() {
        val output = listOf(
            "List of devices attached",
            "420051a5d6c3a465\tunauthorized",
            "",
        )
        assertEquals(
            AdbDevicesParser.Result.NoDevices,
            AdbDevicesParser(output).parse(),
        )
    }
}