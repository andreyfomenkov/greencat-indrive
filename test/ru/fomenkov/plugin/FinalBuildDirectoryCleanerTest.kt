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
            "build/final/ru/fomenkov/ClassA.class",
            "build/final/ru/fomenkov/ClassA\$Companion.class",
            "build/final/ru/fomenkov/ClassA\$onViewCreated\$\$inlined\$observe\$1.class",

            "build/final/ru/fomenkov/ClassB.class",
            "build/final/ru/fomenkov/ClassB\$Companion.class",
            "build/final/ru/fomenkov/ClassB\$onViewCreated\$\$inlined\$observe\$1.class",

            "build/final/ru/fomenkov/ClassC.class",
            "build/final/ru/fomenkov/ClassC\$Companion.class",
            "build/final/ru/fomenkov/ClassC\$onViewCreated\$\$inlined\$observe\$1.class",
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
            setOf(
                "build/final/sample/module/a/src/main/java/ru/fomenkov/ClassA.kt",
                "build/final/sample/module/a/src/debug/kotlin/ru/fomenkov/ClassC.java",
            )
        )

        exec("cd $finalBuildDirectory; find . -name '*.class'").let { result ->
            check(result.successful) { "Failed to execute `find` command" }
            assertEquals(
                setOf(
                    "build/final/ru/fomenkov/ClassB.class",
                    "build/final/ru/fomenkov/ClassB\$Companion.class",
                    "build/final/ru/fomenkov/ClassB\$onViewCreated\$\$inlined\$observe\$1.class",
                ),
                result.output
                    .map { path -> path.replace("./", "") }
                    .toSet(),
            )
        }
    }

    @After
    fun teardown() {
        exec("rm -rf $testRootDirectory")
        Assert.assertFalse(File(testRootDirectory).exists())
    }
}