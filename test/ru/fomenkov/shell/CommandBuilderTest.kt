package ru.fomenkov.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandBuilderTest {

    @Test
    fun `Test command builder`() {
        val expected = "kotlinc -cp CLASSPATH -jvm-target 1.8 -no-stdlib A.kt B.kt C.kt"
        val command = CommandBuilder("kotlinc")
            .param(" -cp", "CLASSPATH ")
            .param("-jvm-target", "1.8")
            .param("  -no-stdlib  ")
            .param(" A.kt", "B.kt  ", "C.kt ")
            .build()
        assertEquals(expected, command)
    }
}