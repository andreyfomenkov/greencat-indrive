package ru.fomenkov

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.fomenkov.async.WorkerTaskExecutor
import ru.fomenkov.data.Repository
import ru.fomenkov.parser.BuildGradleParser
import ru.fomenkov.parser.GitStatusParser
import ru.fomenkov.parser.SettingsGradleParser
import ru.fomenkov.plugin.CompilationRoundsBuilder
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.shell.ShellCommandsValidator
import ru.fomenkov.utils.Log
import ru.fomenkov.utils.Utils
import java.io.File
import java.util.concurrent.Callable

/**
 * [1] Setup Android project root as a working directory for Run/Debug configuration
 * [2] Modify files in project to get not empty `git status` command output (optional)
 */
class AndroidProjectIntegrationTest {

    private lateinit var executor: WorkerTaskExecutor

    @Before
    fun setup() {
        Settings.isVerboseMode = false
        Settings.displayModuleDependencies = false
        Repository.Modules.clear()
        Repository.Graph.clear()
        executor = WorkerTaskExecutor()
    }

    @Test
    fun `Test plugin workflow`() {
        // Check working directory
        Log.d("Current working directory: ${File("").absolutePath}")
        assertTrue("No settings.gradle file found. Incorrect working directory?", File(Settings.SETTINGS_GRADLE_FILE_NAME).exists())

        // Check shell commands
        ShellCommandsValidator.validate()

        // Parse settings.gradle file
        val modules = timeMsec("Parsing settings.gradle") {
            SettingsGradleParser(Settings.SETTINGS_GRADLE_FILE_NAME).parse()
        }
        Repository.Modules.setup(modules)
        assertTrue("No Gradle modules parsed", modules.isNotEmpty())

        // Check build.gradle files exist for declared modules
        val buildFilePaths = modules.map { module ->
            module.path + "/${Settings.BUILD_GRADLE_FILE}"
        }
        buildFilePaths.forEach { path ->
            val exists = File(path).exists()
            assertTrue("Build file doesn't exist: $path", exists)
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
        assertTrue("No modules and dependencies", deps.isNotEmpty())

        // Setup dependency graph
        val graph = deps.associate { (module, deps) -> module to deps }
        Repository.Graph.setup(graph)

        // Display module dependencies
        if (Settings.displayModuleDependencies) {
            deps.forEach { (module, deps) ->
                Log.d("Module: ${module.name}:")
                deps.forEach { dep -> Log.d(" - $dep") }
            }
        }

        // Get project dirty files (see step [2] above)
        val diff = exec("export LANG=en_US; git status")
        val diffOutput = GitStatusParser(diff).parse()
        Log.d("Current branch name: ${diffOutput.branch}")

        // Split supported and not supported source files
        val (supportedSourceFiles, unknownSourceFiles) = diffOutput.files.partition(Utils::isSourceFileSupported)

        if (supportedSourceFiles.isEmpty() && unknownSourceFiles.isEmpty()) {
            Log.d("\nNothing to compile. Please modify supported files to proceed")
            return

        } else if (supportedSourceFiles.isEmpty()) {
            unknownSourceFiles.forEach { path -> Log.d(" - (NOT SUPPORTED) $path") }
            Log.d("\nNo supported source files to compile. Please modify supported files to proceed")
            return
        }

        // Compose compilation rounds
        val rounds = CompilationRoundsBuilder(supportedSourceFiles.toSet()).build()

        rounds.forEachIndexed { index, round ->
            Log.d("Round ${index + 1}:")

            round.items.entries.forEach { (module, sourcePaths) ->
                sourcePaths.forEach { path ->
                    Log.d(" - [${module.name}] $path")
                }
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