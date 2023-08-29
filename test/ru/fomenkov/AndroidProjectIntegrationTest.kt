package ru.fomenkov

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.fomenkov.utils.WorkerTaskExecutor
import ru.fomenkov.data.Repository
import ru.fomenkov.parser.AdbDevicesParser
import ru.fomenkov.parser.BuildGradleParser
import ru.fomenkov.parser.GitStatusParser
import ru.fomenkov.parser.SettingsGradleParser
import ru.fomenkov.plugin.ClassFileSignatureSupplier
import ru.fomenkov.plugin.CompilationRoundsBuilder
import ru.fomenkov.plugin.compiler.DexPatchCompiler
import ru.fomenkov.plugin.compiler.Params
import ru.fomenkov.plugin.strategy.CompilationStrategySelector
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.shell.ShellCommandsValidator
import ru.fomenkov.utils.CompilerModuleNameResolver
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
        Settings.isVerboseMode = true
        Settings.displayModuleDependencies = false
        Settings.displayResolvingChildModules = false
        Settings.displayKotlinCompilerModuleNames = false
        Repository.Modules.clear()
        Repository.Graph.clear()
        Repository.CompilerModuleNameParam.clear()
        executor = WorkerTaskExecutor()
    }

    @Test
    fun `Test plugin workflow`() {
        val totalTimeStart = System.currentTimeMillis()

        // Check working directory
        Log.d("Current working directory: ${File("").absolutePath}")
        assertTrue("No settings.gradle file found. Incorrect working directory?", File(Settings.SETTINGS_GRADLE_FILE_NAME).exists())

        // Check shell commands
        ShellCommandsValidator.validate()

        // Remove intermediate build directory
        exec("rm -rf ${Params.BUILD_PATH_INTERMEDIATE}")

        // Parse settings.gradle file
        val modules = timeMsec("Parsing settings.gradle") {
            SettingsGradleParser(Settings.SETTINGS_GRADLE_FILE_NAME).parse()
        }
        Repository.Modules.setup(modules)
        assertTrue("No Gradle modules parsed", modules.isNotEmpty())

        // Resolve module names for Kotlin compiler `-module-name` param
        Log.v(Settings.displayKotlinCompilerModuleNames, "[Kotlin compiler module names]")
        modules.forEach { module ->
            val paths = CompilerModuleNameResolver.getPossibleBuildPaths(module)
            val name = CompilerModuleNameResolver.resolve(paths)

            if (name.isBlank()) {
                Log.v(Settings.displayKotlinCompilerModuleNames, "????????? -> [${module.name}]")
            } else {
                Log.v(Settings.displayKotlinCompilerModuleNames, "$name -> [${module.name}]")
            }
            Repository.CompilerModuleNameParam.set(module, name)
        }

        // Check build.gradle or build.gradle.kts files exist for the declared modules
        modules.forEach { module ->
            val buildGradlePath = module.path + "/${Settings.BUILD_GRADLE_FILE}"
            val buildGradleKtsPath = module.path + "/${Settings.BUILD_GRADLE_KTS_FILE}"
            val buildFileExists = File(buildGradlePath).exists() || File(buildGradleKtsPath).exists()

            if (!buildFileExists) {
                Log.d("[WARNING] Module ${module.path} has no build file")
            }
        }

        // Parse build.gradle files for declared modules
        val deps = timeMsec("Parse build.gradle files for declared modules") {
            modules.map { module ->
                Callable {
                    val buildGradlePath = module.path + "/${Settings.BUILD_GRADLE_FILE}"
                    val buildGradleKtsPath = module.path + "/${Settings.BUILD_GRADLE_KTS_FILE}"
                    val buildFilePath = when {
                        File(buildGradlePath).exists() -> buildGradlePath
                        File(buildGradleKtsPath).exists() -> buildGradleKtsPath
                        else -> null
                    }
                    val deps = if (buildFilePath == null) {
                        emptySet()
                    } else {
                        BuildGradleParser(buildFilePath).parse()
                    }
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
        val (diff) = exec("export LANG=en_US; git status")
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
            Log.d("\nRound ${index + 1}:")

            round.items.entries.forEach { (module, sourcePaths) ->
                sourcePaths.forEach { path ->
                    Log.d(" - [${module.name}] $path")
                }
            }
        }
        Log.d("")

        // Select compilation strategy (plain or kapt) for a particular round
        val compileTimeStart = System.currentTimeMillis()

        rounds.forEachIndexed { index, round ->
            val strategy = CompilationStrategySelector.select(round)
            Log.d("Compilation round ${index + 1} -> using ${strategy.javaClass.simpleName}")
            strategy.perform(round)
        }
        val compileTimeEnd = System.currentTimeMillis()

        Log.d("Compilation time: ${(compileTimeEnd - compileTimeStart) / 1000} sec")

        // Add Greencat signature for all generated .class files
        val signatureTimeStart = System.currentTimeMillis()
        var classFilesCount = 0

        exec("find ${Params.BUILD_PATH_INTERMEDIATE} -name '*.class'").let { result ->
            if (result.successful) {
                check(result.output.isNotEmpty()) { "No .class files found" }
                classFilesCount = result.output.size

                result.output.forEach { path ->
                    if (!ClassFileSignatureSupplier.run(path, path)) {
                        error("Failed to add signature into file: $path")
                    }
                }
            } else {
                error("Failed to list all .class files in ${Params.BUILD_PATH_INTERMEDIATE} directory")
            }
        }
        val signatureTimeEnd = System.currentTimeMillis()
        Log.d("\n$classFilesCount signature(s) were added in ${signatureTimeEnd - signatureTimeStart} ms")

        // Copy intermediate build directory into final recursively
        // Slash symbol is important!
        check(
            exec("cp -r ${Params.BUILD_PATH_INTERMEDIATE}/ ${Params.BUILD_PATH_FINAL}/").successful
        ) { "Failed to copy intermediate build directory into final" }

        // Create DEX patch
        exec("find ${Params.BUILD_PATH_FINAL} -name '*.class'").let { result ->
            if (result.successful) {
                val dexingTimeStart = System.currentTimeMillis()

                if (!DexPatchCompiler.run(result.output.toSet())) {
                    Log.d("")
                    DexPatchCompiler.output().forEach(Log::d)
                    error("Failed to create DEX patch")
                }
                val dexingTimeEnd = System.currentTimeMillis()
                Log.d("Creating DEX patch finished in ${dexingTimeEnd - dexingTimeStart} ms")
            } else {
                error("Failed to list all .class files in ${Params.BUILD_PATH_FINAL} directory")
            }
        }

        // Get available Android devices via adb
        check(File(Params.ADB_TOOL_PATH).exists()) { "ADB not found at: ${Params.ADB_TOOL_PATH}" }
        exec("export LANG=en_US; ${Params.ADB_TOOL_PATH} devices").let { result ->
            if (result.successful) {
                when (AdbDevicesParser(result.output).parse()) {
                    AdbDevicesParser.Result.SUCCESS -> Log.d("Android device found")
                    AdbDevicesParser.Result.NO_DEVICES -> {
                        Log.d("No devices connected")
                        return@let
                    }
                    AdbDevicesParser.Result.MULTIPLE_DEVICES -> {
                        Log.d("Multiple devices connected")
                        return@let
                    }
                }
            } else {
                error("Failed to get Android devices via adb")
            }
        }

        // Push DEX patch
        exec("${Params.ADB_TOOL_PATH} push ${Params.DEX_PATCH_SOURCE_PATH} ${Params.DEX_PATCH_DEST_PATH}").let { result ->
            if (!result.successful) {
                error("Failed to push DEX patch to Android device")
            }
        }

        // Total time output
        val totalTimeEnd = System.currentTimeMillis()
        val dexFile = File(Params.DEX_PATCH_SOURCE_PATH)
        check(dexFile.exists()) { "No DEX file found: ${dexFile.absolutePath} ${Params.DEX_PATCH_DEST_PATH}" }

        Log.d("\n### Total time: ${(totalTimeEnd - totalTimeStart) / 1000} sec, size: ${dexFile.length() / 1024} KB ###")
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