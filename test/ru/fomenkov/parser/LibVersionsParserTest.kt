package ru.fomenkov.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class LibVersionsParserTest {

    @Test
    fun `Test parsing lib versions file`() {
        assertEquals(
            mapOf(
                "detekt" to "1.22.0",
                "firebase" to "2.9.7",
            ),
            LibVersionsParser("test_data/libversions").parse(),
        )
    }
}