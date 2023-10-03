package ru.fomenkov.plugin.bytecode

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.fomenkov.utils.Log

/**
 * ### Source code for test_data/SampleInjectionClass.class ###
 *
 * import dagger.assisted.AssistedInject
 * import javax.inject.Inject
 *
 * class SampleInjectionClass {
 *
 *     @Inject
 *     constructor(arg1: Int) {
 *     }
 *
 *     @AssistedInject
 *     constructor(arg1: Long, arg2: String) {
 *     }
 *
 *     constructor(arg1: Long, arg2: String, arg3: Byte) {
 *     }
 *
 *     @Inject
 *     lateinit var lat1: String
 *
 *     lateinit var lat2: String
 *
 *     val con1 = "ABC-123"
 *
 *     @Inject
 *     fun fooInj(arg1: Int): String {
 *         return ""
 *     }
 *
 *     @Inject
 *     fun barInj(arg1: Int, arg2: Long): Double {
 *         return 0.0
 *     }
 *
 *     fun foo(arg1: Byte): Long {
 *         return 1
 *     }
 *
 *     fun bar(arg1: Byte, arg2: Char): Short {
 *         return 2
 *     }
 * }
 */
class ClassFileInjectionSnapshotTest {

    @Before
    fun setup() {
        Log.level = Log.Level.VERBOSE
    }

    @Test
    fun `Test class file injection snapshot`() {
        val path = "test_data/SampleInjectionClass.class"
        val startTime = System.currentTimeMillis()
        val actualSnapshot = ClassFileInjectionSnapshotMaker.make(path)
        val endTime = System.currentTimeMillis()
        val expectedSnapshot = """
               <init>, (I)V
               <init>, (JLjava/lang/String;)V
               barInj, (IJ)D
               fooInj, (I)Ljava/lang/String;
               lat1, Ljava/lang/String;
        """.trimIndent()

        Log.d("Snapshot value: $actualSnapshot, hash value: ${actualSnapshot.hashCode()}")
        Log.d("# Execution time: ${endTime - startTime} ms #")

        assertEquals(expectedSnapshot, actualSnapshot)
    }
}