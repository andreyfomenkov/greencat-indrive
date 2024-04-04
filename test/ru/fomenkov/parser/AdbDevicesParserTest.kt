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
            emptyList<String>(),
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
            listOf("420051a5d6c3a465"),
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
            listOf("420051a5d6c3a465"),
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
            listOf("420051a5d6c3a465", "1234567890123456"),
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
            emptyList<String>(),
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
            emptyList<String>(),
            AdbDevicesParser(output).parse(),
        )
    }
}