package ru.fomenkov.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.fomenkov.data.Configuration

class JsonConfigParserTest {

    @Test
    fun `Test parse json configuration`() {
        val configs = JsonConfigParser("test_data/config.json").parse()

        assertEquals(4, configs.size)
        assertEquals(
            Configuration(
                name = "passenger",
                modules = setOf(
                    ":passenger:main",
                    ":passenger:ui",
                    ":passenger:data",
                ),
            ),
            configs[0],
        )
        assertEquals(
            Configuration(
                name = "driver",
                modules = setOf(
                    ":driver:main",
                    ":driver:ui",
                    ":driver:data",
                ),
            ),
            configs[1],
        )
        assertEquals(
            Configuration(
                name = "passenger_and_driver",
                modules = setOf(
                    ":passenger:main",
                    ":passenger:ui",
                    ":passenger:data",
                    ":driver:main",
                    ":driver:ui",
                    ":driver:data",
                    ":app:main",
                    ":app:common",
                ),
            ),
            configs[2],
        )
        assertEquals(
            Configuration(
                name = "empty",
                modules = emptySet(),
            ),
            configs[3],
        )
    }
}