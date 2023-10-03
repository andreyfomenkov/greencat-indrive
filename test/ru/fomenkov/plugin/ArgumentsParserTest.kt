package ru.fomenkov.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArgumentsParserTest {

    @Test
    fun `Test argument parser cases`() {
        // All arguments present
        assertEquals(
            ArgumentsParser.Result(
                classpath = "cp",
                packageName = "pn",
                componentName = "cn",
                focusedFilePath = "fo",
            ),
            ArgumentsParser(
                listOf(
                    "-classpath", "cp",
                    "-package", "pn",
                    "-component", "cn",
                    "-focus", "fo",
                )
            ).parse()
        )

        // Missing -classpath
        assertNull(
            ArgumentsParser(
                listOf(
                    "-package", "pn",
                    "-component", "cn",
                    "-focus", "fo",
                )
            ).parse()
        )

        // Missing -package
        assertNull(
            ArgumentsParser(
                listOf(
                    "-classpath", "cp",
                    "-component", "cn",
                    "-focus", "fo",
                )
            ).parse()
        )

        // Missing -component
        assertNull(
            ArgumentsParser(
                listOf(
                    "-classpath", "cp",
                    "-package", "pn",
                    "-focus", "fo",
                )
            ).parse()
        )

        // Missing -focus
        assertNull(
            ArgumentsParser(
                listOf(
                    "-classpath", "cp",
                    "-package", "pn",
                    "-component", "cn",
                )
            ).parse()
        )
    }
}