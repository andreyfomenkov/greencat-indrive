package ru.fomenkov.utils

import org.junit.Test
import ru.fomenkov.data.Module
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}