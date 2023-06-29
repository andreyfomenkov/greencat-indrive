package ru.fomenkov

import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.fomenkov.async.WorkerTaskExecutor
import ru.fomenkov.data.Repository
import ru.fomenkov.parser.BuildGradleParser
import ru.fomenkov.parser.SettingsGradleParser
import ru.fomenkov.utils.Log
import java.io.File
import java.util.concurrent.Callable
import kotlin.test.assertTrue

// Setup Android project root as a working directory for Run/Debug configuration
class AndroidProjectIntegrationTest {

    private lateinit var executor: WorkerTaskExecutor

    @Before
    fun setup() {
        Settings.isVerboseMode = true
        Settings.displayModuleDependencies = false
        executor = WorkerTaskExecutor()
    }

    @Test
    fun `Verify project structure`() {
        // Check working directory
        Log.d("Current working directory: ${File("").absolutePath}")
        assertTrue(File(Settings.SETTINGS_GRADLE_FILE_NAME).exists(), "No settings.gradle file found. Incorrect working directory?")

        // Parse settings.gradle file
        val modules = timeMsec("Parsing settings.gradle") {
            SettingsGradleParser(Settings.SETTINGS_GRADLE_FILE_NAME).parse()
        }
        Repository.Modules.setup(modules)
        assertTrue(modules.isNotEmpty(), "No Gradle modules parsed")

        // Check build.gradle files exist for declared modules
        val buildFilePaths = modules.map { module ->
            module.path + "/${Settings.BUILD_GRADLE_FILE}"
        }
        buildFilePaths.forEach { path ->
            val exists = File(path).exists()
            assertTrue(exists, "Build file doesn't exist: $path")
        }

        // Parse build.gradle files for declared modules
        val deps = timeMsec("Parse build.gradle files for declared modules") {
            modules.map { module ->
                Callable {
                    val path = module.path + "/${Settings.BUILD_GRADLE_FILE}"
                    val deps = BuildGradleParser(path).parse()
                    module to deps
                }
            }.run { executor.run(this) }
        }
        assertTrue(deps.isNotEmpty(), "No modules and dependencies")

        // Display module dependencies
        if (Settings.displayModuleDependencies) {
            deps.forEach { (module, deps) ->
                Log.d("Module: ${module.name}:")
                deps.forEach { dep -> Log.d(" - $dep") }
            }
        }
    }

    @After
    fun teardown() {
        executor.release()
    }

    private fun<T> timeMsec(step: String, action: () -> T): T {
        val start = System.currentTimeMillis()
        val result = action()
        val end = System.currentTimeMillis()

        Log.d("# $step: ${end - start} msec\n")
        return result
    }
}