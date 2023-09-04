package ru.fomenkov.plugin

import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.fomenkov.Settings
import ru.fomenkov.shell.Shell.exec
import java.io.File

class FinalBuildDirectoryCleanerTest {

    private val testRootDirectory = "test_cleaner"
    private val finalBuildDirectory = "$testRootDirectory/final"

    @Before
    fun setup() {
        Settings.isVerboseMode = true
        exec("rm -rf $testRootDirectory")
        Assert.assertFalse(File(testRootDirectory).exists())

        exec("mkdir $testRootDirectory")
        exec("mkdir $finalBuildDirectory")
        Assert.assertTrue(File(testRootDirectory).exists())
        Assert.assertTrue(File(finalBuildDirectory).exists())
    }

    @Test
    fun `Test final directory cleanup`() {
        setOf(
            "ru/fomenkov/ClassA.class",
            "ru/fomenkov/ClassA\$Companion.class",
            "ru/fomenkov/ClassA\$onViewCreated\$\$inlined\$observe\$1.class",

            "ru/fomenkov/ClassB.class",
            "ru/fomenkov/ClassB\$Companion.class",
            "ru/fomenkov/ClassB\$onViewCreated\$\$inlined\$observe\$1.class",

            "ru/fomenkov/ClassC.class",
            "ru/fomenkov/ClassC\$Companion.class",
            "ru/fomenkov/ClassC\$onViewCreated\$\$inlined\$observe\$1.class",
        )
            .forEach { path ->
                val file = File("$finalBuildDirectory/$path")
                val parent = file.parentFile

                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    error("Failed to create directories for file: ${file.path}")
                }
                file.createNewFile()
            }

        FinalBuildDirectoryCleaner(finalBuildDirectory).clean(
            setOf("ru/fomenkov/ClassA.kt", "ru/fomenkov/ClassC.java")
        )

        exec("cd $finalBuildDirectory; find . -name '*.class'").let { result ->
            check(result.successful) { "Failed to execute `find` command" }
            assertEquals(
                setOf(
                    "./ru/fomenkov/ClassB.class",
                    "./ru/fomenkov/ClassB\$Companion.class",
                    "./ru/fomenkov/ClassB\$onViewCreated\$\$inlined\$observe\$1.class",
                ),
                result.output.toSet(),
            )
        }
    }

    @After
    fun teardown() {
        exec("rm -rf $testRootDirectory")
        Assert.assertFalse(File(testRootDirectory).exists())
    }
}