package ru.fomenkov.update

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.fomenkov.prefs.Prefs
import java.io.File

class PrefsTest {

    private val prefsFileName = "test_data/prefs"

    @Before
    fun setup() {
        File(prefsFileName).delete()
    }

    @Test
    fun `Test prefs read and write`() {
        val prefs = Prefs(prefsFileName)

        assertEquals("", prefs.get("A"))
        assertEquals("", prefs.get("B"))

        prefs.put("B", "1")

        assertEquals("", prefs.get("A"))
        assertEquals("1", prefs.get("B"))

        prefs.put("A", "2")
        prefs.put("B", "3")
        prefs.put("C", "4")

        assertEquals("2", prefs.get("A"))
        assertEquals("3", prefs.get("B"))
        assertEquals("4", prefs.get("C"))
    }

    @After
    fun teardown() {
        File(prefsFileName).delete()
    }
}