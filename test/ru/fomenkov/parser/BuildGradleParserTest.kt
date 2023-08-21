package ru.fomenkov.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.fomenkov.data.Dependency

class BuildGradleParserTest {

    @Test
    fun `Test parsing Groovy build file`() {
        val deps = BuildGradleParser("test_data/build.gradle").parse()

        assertEquals(
            setOf(
                Dependency.Module(name = "m1:sub1", isTransitive = true),
                Dependency.Module(name = "m1:sub2", isTransitive = true),
                Dependency.Module(name = "m2:sub1", isTransitive = false),
                Dependency.Module(name = "m2:sub2", isTransitive = false),
                Dependency.Module(name = "m2:sub3:sub4", isTransitive = false),
                Dependency.Module(name = "m3:sub1", isTransitive = false),
                Dependency.Module(name = "m3:sub2", isTransitive = false),
                Dependency.Module(name = "m3:sub3:sub4", isTransitive = false),
            ),
            deps,
        )
    }

    @Test
    fun `Test parsing KTS build file`() {
        val deps = BuildGradleParser("test_data/build.gradle.kts").parse()

        assertEquals(
            setOf(
                Dependency.Module(name = "m1:sub1", isTransitive = true),
                Dependency.Module(name = "m1:sub2", isTransitive = true),
                Dependency.Module(name = "m2:sub1", isTransitive = false),
                Dependency.Module(name = "m2:sub2", isTransitive = false),
                Dependency.Module(name = "m2:sub3:sub4", isTransitive = false),
                Dependency.Module(name = "m3:sub1", isTransitive = false),
                Dependency.Module(name = "m3:sub2", isTransitive = false),
                Dependency.Module(name = "m3:sub3:sub4", isTransitive = false),
            ),
            deps,
        )
    }
}