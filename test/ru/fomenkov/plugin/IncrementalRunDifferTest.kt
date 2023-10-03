package ru.fomenkov.plugin

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.fomenkov.Settings
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.utils.Log
import ru.fomenkov.utils.WorkerTaskExecutor
import java.io.File
import kotlin.random.Random

class IncrementalRunDifferTest {

    private val testRootDirectory = "test_differ"
    private val intermediateBuildDirectory = "$testRootDirectory/intermediate"
    private val finalBuildDirectory = "$testRootDirectory/final"
    private val diffBuildDirectory = "$testRootDirectory/diff"

    private lateinit var executor: WorkerTaskExecutor
    private lateinit var differ: IncrementalRunDiffer

    @Before
    fun setup() {
        Log.level = Log.Level.VERBOSE
        exec("rm -rf $testRootDirectory")
        assertFalse(File(testRootDirectory).exists())

        exec("mkdir $testRootDirectory")
        exec("mkdir $intermediateBuildDirectory")
        exec("mkdir $finalBuildDirectory")
        exec("mkdir $diffBuildDirectory")
        assertTrue(File(testRootDirectory).exists())
        assertTrue(File(intermediateBuildDirectory).exists())
        assertTrue(File(finalBuildDirectory).exists())
        assertTrue(File(diffBuildDirectory).exists())

        executor = WorkerTaskExecutor()
        differ = IncrementalRunDiffer(
            executor = executor,
            intermediateBuildPath = intermediateBuildDirectory,
            finalBuildPath = finalBuildDirectory,
        )
    }

    @Test
    fun `Test incremental diff steps`() {
        // Step 1: add sources 1, 2, 3
        assertEquals(
            IncrementalRunDiffer.Result(
                compileSourcePaths = setOf(
                    "$diffBuildDirectory/Source_1.kt".createOrUpdate(),
                    "$diffBuildDirectory/Source_2.kt".createOrUpdate(),
                    "$diffBuildDirectory/Source_3.kt".createOrUpdate(),
                ),
                removeSourcePaths = emptySet(),
            ),
            diff(
                "$diffBuildDirectory/Source_1.kt".createOrUpdate(),
                "$diffBuildDirectory/Source_2.kt".createOrUpdate(),
                "$diffBuildDirectory/Source_3.kt".createOrUpdate(),
            ),
        )
        // Step 2: modify source 2
        assertEquals(
            IncrementalRunDiffer.Result(
                compileSourcePaths = setOf(
                    "$diffBuildDirectory/Source_2.kt",
                ),
                removeSourcePaths = emptySet(),
            ),
            diff(
                "$diffBuildDirectory/Source_1.kt",
                "$diffBuildDirectory/Source_2.kt".createOrUpdate(),
                "$diffBuildDirectory/Source_3.kt",
            )
        )
        // Step 3: modify sources 1, 3
        assertEquals(
            IncrementalRunDiffer.Result(
                compileSourcePaths = setOf(
                    "$diffBuildDirectory/Source_1.kt",
                    "$diffBuildDirectory/Source_3.kt",
                ),
                removeSourcePaths = emptySet(),
            ),
            diff(
                "$diffBuildDirectory/Source_1.kt".createOrUpdate(),
                "$diffBuildDirectory/Source_2.kt",
                "$diffBuildDirectory/Source_3.kt".createOrUpdate(),
            ),
        )
        // Step 4: delete source 1 and modify sources 3, 4
        assertEquals(
            IncrementalRunDiffer.Result(
                compileSourcePaths = setOf(
                    "$diffBuildDirectory/Source_3.kt",
                    "$diffBuildDirectory/Source_4.kt",
                ),
                removeSourcePaths = setOf(
                    "$diffBuildDirectory/Source_1.kt",
                ),
            ),
            diff(
                "$diffBuildDirectory/Source_2.kt",
                "$diffBuildDirectory/Source_3.kt".createOrUpdate(),
                "$diffBuildDirectory/Source_4.kt".createOrUpdate(),
            ),
        )
        // Step 5: delete all sources and add source 456
        assertEquals(
            IncrementalRunDiffer.Result(
                compileSourcePaths = setOf(
                    "$diffBuildDirectory/Source_456.kt",
                ),
                removeSourcePaths = setOf(
                    "$diffBuildDirectory/Source_2.kt",
                    "$diffBuildDirectory/Source_3.kt",
                    "$diffBuildDirectory/Source_4.kt",
                ),
            ),
            diff(
                "$diffBuildDirectory/Source_456.kt".createOrUpdate(),
            ),
        )
    }

    @After
    fun teardown() {
        executor.release()
        exec("rm -rf $testRootDirectory")
        assertFalse(File(testRootDirectory).exists())
    }

    private fun diff(vararg gitDiffPaths: String): IncrementalRunDiffer.Result {
        return differ.run(gitDiffPaths.toSet())
            .also { (compileSourcePaths, removeSourcePaths) ->
                compileSourcePaths.forEach { path -> Log.v("[COMPILE] $path") }
                removeSourcePaths.forEach { path -> Log.v("[REMOVE]  $path") }
                Log.v("")
            }
    }

    private fun String.createOrUpdate(): String {
        val builder = StringBuilder()
        val linesCount = Random.nextInt(from = 10, until = 30)

        for (i in 0 until linesCount) {
            builder.append(Random.nextLong())
            builder.append('\n')
        }
        File(this).writeText(builder.toString())
        return this
    }
}