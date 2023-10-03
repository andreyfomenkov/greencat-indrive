package ru.fomenkov.plugin.strategy

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.fomenkov.Settings
import ru.fomenkov.data.Module
import ru.fomenkov.data.Round
import ru.fomenkov.utils.Log
import java.io.File
import java.lang.IllegalStateException

class CompilationStrategySelectorTest {

    private val testFileSystemPath = "test_fs"

    @Before
    fun setup() {
        Log.level = Log.Level.VERBOSE
        createTestFileSystemDirectory()
    }

    @Test
    fun `Test strategy selector with plain source files only`() {
        val sourcePaths = mutableSetOf<String>()
        sourcePaths += testSourceFile("DataClass.kt") {
            """
                package ru.fomenkov
                
                import ru.fomenkov.data
                import ru.fomenkov.plugin
                import ru.fomenkov.utils
                
                data class(...)
            """.trimIndent()
        }
        sourcePaths += testSourceFile("Interactor.kt") {
            """
                package ru.fomenkov
                
                import ru.fomenkov.data
                import ru.fomenkov.plugin
                import ru.fomenkov.utils
                
                class Interactor(...)
            """.trimIndent()
        }
        val round = Round(
            items = mapOf(
                Module(name = "module-1", path = "") to sourcePaths,
                Module(name = "module-2", path = "") to sourcePaths,
                Module(name = "module-3", path = "") to sourcePaths,
            ),
        )
        val strategy = CompilationStrategySelector.select(round)

        assertTrue("Expecting plain compilation strategy", strategy is PlainCompilationStrategy)
    }

    @Test
    fun `Test strategy selector for sources with inject annotation`() {
        val sourcePaths = mutableSetOf<String>()
        sourcePaths += testSourceFile("DataClass.kt") {
            """
                package ru.fomenkov
                
                import ru.fomenkov.data
                import ru.fomenkov.plugin
                import ru.fomenkov.utils
                
                data class(...)
            """.trimIndent()
        }
        sourcePaths += testSourceFile("Interactor.kt") {
            """
                package ru.fomenkov
                
                import ru.fomenkov.data
                import ru.fomenkov.plugin
                import ru.fomenkov.utils
                import javax.inject.Inject
                
                class Interactor(...)
            """.trimIndent()
        }
        val round = Round(
            items = mapOf(
                Module(name = "module-1", path = "") to sourcePaths,
                Module(name = "module-2", path = "") to sourcePaths,
                Module(name = "module-3", path = "") to sourcePaths,
            ),
        )
        val strategy = CompilationStrategySelector.select(round)

        assertTrue("Expecting kapt compilation strategy", strategy is KaptCompilationStrategy)
    }

    @Test
    fun `Test strategy selector for sources with Dagger annotation`() {
        val sourcePaths = mutableSetOf<String>()
        sourcePaths += testSourceFile("DataClass.kt") {
            """
                package ru.fomenkov
                
                import ru.fomenkov.data
                import ru.fomenkov.plugin
                import ru.fomenkov.utils
                
                data class(...)
            """.trimIndent()
        }
        sourcePaths += testSourceFile("Interactor.kt") {
            """
                package ru.fomenkov
                
                import ru.fomenkov.data
                import ru.fomenkov.plugin
                import ru.fomenkov.utils
                import dagger.Binds
                import dagger.Module
                
                class Interactor(...)
            """.trimIndent()
        }
        val round = Round(
            items = mapOf(
                Module(name = "module-1", path = "") to sourcePaths,
                Module(name = "module-2", path = "") to sourcePaths,
                Module(name = "module-3", path = "") to sourcePaths,
            ),
        )
        val strategy = CompilationStrategySelector.select(round)

        assertTrue("Expecting kapt compilation strategy", strategy is KaptCompilationStrategy)
    }

    @Test(expected = IllegalStateException::class)
    fun `Test strategy selector with no source files`() {
        val round = Round(items = emptyMap())
        CompilationStrategySelector.select(round)
    }

    @After
    fun teardown() {
        deleteTestFileSystemDirectory()
    }

    private fun createTestFileSystemDirectory() {
        with (File(testFileSystemPath)) {
            if (exists()) {
                deleteTestFileSystemDirectory()
            }
            check(mkdir()) { "Failed to create test filesystem directory" }
        }
    }

    private fun deleteTestFileSystemDirectory() {
        check(File(testFileSystemPath).deleteRecursively()) { "Failed to delete test filesystem directory" }
    }

    private fun testSourceFile(name: String, code: () -> String): String {
        val path = "$testFileSystemPath/$name"

        with (File(path)) {
            if (exists()) {
                check(delete()) { "Failed to delete file: $path" }
            } else {
                createNewFile()
                writeText(code())
            }
        }
        return path
    }
}