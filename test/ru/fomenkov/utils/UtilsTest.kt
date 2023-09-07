package ru.fomenkov.utils

import org.junit.Assert.*
import org.junit.Test
import ru.fomenkov.data.Module

class UtilsTest {

    @Test
    fun `Test convert name to module`() {
        assertEquals(
            Module(name = "m1", path = "m1"),
            Utils.toModule("m1"),
        )
        assertEquals(
            Module(name = "m1:m2", path = "m1/m2"),
            Utils.toModule("m1:m2"),
        )
        assertEquals(
            Module(name = "m1:m2:m3", path = "m1/m2/m3"),
            Utils.toModule("m1:m2:m3"),
        )
    }

    @Test
    fun `Test supported file extensions`() {
        assertTrue(Utils.isSourceFileSupported("project/FooBar.kt"))
        assertTrue(Utils.isSourceFileSupported(" project/FooBar.kt  "))
        assertFalse(Utils.isSourceFileSupported("project/FooBar.java"))
        assertFalse(Utils.isSourceFileSupported(" project/FooBar.java  "))
        assertFalse(Utils.isSourceFileSupported("project/FooBar.xml"))
        assertFalse(Utils.isSourceFileSupported(" project/FooBar.xml  "))
    }

    @Test
    fun `Test compose class entries`() {
        val paths = setOf(
            "build/final/ru/fomenkov/ClassA.class",
            "build/final/ru/fomenkov/ClassA\$foo.class",
            "build/final/ru/fomenkov/ClassA\$foo\$bar.class",

            "build/final/ru/fomenkov/ClassB.class",
            "build/final/ru/fomenkov/ClassB\$foo.class",
            "build/final/ru/fomenkov/ClassC\$foo\$bar.class",

            "build/final/ru/fomenkov/ClassD\$foo.class",
            "build/final/ru/fomenkov/ClassE\$foo\$bar.class",

            "build/final/ru/fomenkov/ClassF.class",
        )
        assertEquals(
            setOf(
                "build/final/ru/fomenkov/ClassA",
                "build/final/ru/fomenkov/ClassB",
                "build/final/ru/fomenkov/ClassC",
                "build/final/ru/fomenkov/ClassD",
                "build/final/ru/fomenkov/ClassE",
                "build/final/ru/fomenkov/ClassF",
            ),
            Utils.composeClassEntries(paths),
        )
    }
}