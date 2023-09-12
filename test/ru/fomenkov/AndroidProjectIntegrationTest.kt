package ru.fomenkov

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.fomenkov.data.Dependency
import ru.fomenkov.data.Module
import ru.fomenkov.utils.WorkerTaskExecutor
import ru.fomenkov.data.Repository
import ru.fomenkov.data.Round
import ru.fomenkov.parser.*
import ru.fomenkov.plugin.ClassFileSignatureSupplier
import ru.fomenkov.plugin.CompilationRoundsBuilder
import ru.fomenkov.plugin.FinalBuildDirectoryCleaner
import ru.fomenkov.plugin.IncrementalRunDiffer
import ru.fomenkov.plugin.compiler.DexPatchCompiler
import ru.fomenkov.plugin.compiler.Params
import ru.fomenkov.plugin.strategy.CompilationStrategy
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
 * [3] Add PACKAGE_NAME and COMPONENT_NAME for Run/Debug configuration
 */
class AndroidProjectIntegrationTest {

    private lateinit var executor: WorkerTaskExecutor

    @Before
    fun setup() {
        with(Settings) {
            isVerboseMode = true
            displayModuleDependencies = false
            displayResolvingChildModules = false
            displayKotlinCompilerModuleNames = false
            useIncrementalDiff = true
            usePlainCompilationStrategyOnly = true
        }
        Repository.Modules.clear()
        Repository.Graph.clear()
        Repository.CompilerModuleNameParam.clear()
        executor = WorkerTaskExecutor()
    }

    @Test
    fun `Test plugin workflow`() {
        val totalTimeStart = System.currentTimeMillis()
        val packageName = System.getenv()["PACKAGE_NAME"] ?: ""
        val componentName = System.getenv()["COMPONENT_NAME"] ?: ""

        // Check working directory
        checkWorkingDirectory()

        // Check application package and component name
        checkAppPackageAndComponentName(packageName, componentName)

        // Check shell commands
        ShellCommandsValidator.validate()

        // Recreate intermediate build directory
        recreateIntermediateBuildDirectory()

        // Create final build directory if it doesn't exist
        createFinalBuildDirectory()

        // Parse settings.gradle file
        val modules = parseSettingsGradleFile()

        // Resolve module names for Kotlin compiler `-module-name` param
        resolveModuleNamesForKotlinCompiler(modules)

        // Check build.gradle or build.gradle.kts files exist for the declared modules
        checkBuildGradleFilesExist(modules)

        // Parse build.gradle files for declared modules
        val deps = parseBuildGradleFiles(modules)

        // Setup dependency graph
        setupDependencyGraph(deps)

        // Display module dependencies
        if (Settings.displayModuleDependencies) {
            deps.forEach { (module, deps) ->
                Log.d("Module: ${module.name}:")
                deps.forEach { dep -> Log.d(" - $dep") }
            }
        }

        // Get project git diff files (see step [2] above)
        val gitDiffFiles = getProjectGitDiffFiles()

        // Split supported and not supported source files
        val (supportedSourceFiles, unknownSourceFiles) = gitDiffFiles.partition(Utils::isSourceFileSupported)

        // Show details in case no supported source files found
        if (supportedSourceFiles.isEmpty() && unknownSourceFiles.isEmpty()) {
            Log.d("\nNothing to compile. Please modify supported files to proceed")
            return

        } else if (supportedSourceFiles.isEmpty()) {
            unknownSourceFiles.forEach { path -> Log.d(" - (NOT SUPPORTED) $path") }
            Log.d("\nNo supported source files to compile. Please modify supported files to proceed")
            return
        }

        // TODO: push existing DEX patch or just launch app in case nothing to compile (check for removed!)
        // 1. Empty git diff / no supported files: delete final dir, patch locally and patch on the device, restart app
        // 2. Empty to compile, empty to remove: push existing patch if any, restart app
        // 4. Empty to compile, has to remove: clean final dir, create patch, push, restart app
        // 3. Has to compile: compile, push patch, restart app
        //
        // TODO: in case of previous compilation FAILED?
        // TODO: clear removed classes in final build directory before compilation

        // Analyze incremental diff
        val (compileSourcePaths, removeSourcePaths) = analyzeIncrementalDiff(supportedSourceFiles)

        // Display details for the current incremental diff step
        Log.d("# Incremental diff details #")
        if (compileSourcePaths.isEmpty()) {
            Log.d("[COMPILE] * nothing *")
        } else {
            compileSourcePaths.forEach { path -> Log.d("[COMPILE] $path") }
        }
        if (removeSourcePaths.isEmpty()) {
            Log.d("[REMOVE] * nothing *")
        } else {
            removeSourcePaths.forEach { path -> Log.d("[REMOVE]  $path") }
        }

        // Cleanup unnecessary .class files in the final build directory
        cleanupFinalBuildDirectory(removeSourcePaths)

        // Check for any sources for incremental compilation
        if (compileSourcePaths.isEmpty()) {
            Log.d("\nNothing to compile since the previous incremental run")
            return
        }

        // Compose compilation rounds
        val rounds = composeCompilationRounds(compileSourcePaths)

        // Perform compilation for all rounds
        compileRounds(rounds).let { result ->
            if (result is CompilationStrategy.Result.Failed) {
                Log.d("\n### Compilation failed ###\n")
                result.output.forEach(Log::d)
                return
            }
        }

        // Add Greencat signature for all generated .class files
        addGreenCatSignatures()

        // Recursively copy intermediate .class files to the final build directory
        copyIntermediateClassesToFinalBuildDirectory()

        // Build DEX patch
        buildDexPatch()

        // Get available Android devices via adb
        checkAndroidDeviceConnected()

        // Compose destination DEX patch path
        val dstDexPatchPath = composePatchDestinationPath(packageName)

        // Remove previous patch files on Android device
        removePatchesOnDevice()

        // Push DEX patch
        pushPatchToDevice(dstDexPatchPath)

        // Restart app
        restartApp(packageName, componentName)

        // Total time output
        val totalTimeEnd = System.currentTimeMillis()
        val dexFile = File(Params.DEX_PATCH_SOURCE_PATH)
        check(dexFile.exists()) { "No DEX file found: ${Params.DEX_PATCH_SOURCE_PATH}" }
        Log.d("\n### Total time: ${(totalTimeEnd - totalTimeStart) / 1000} sec, size: ${dexFile.length() / 1024} KB ###")
    }

    private fun checkWorkingDirectory() {
        Log.d("Current working directory: ${File("").absolutePath}")
        assertTrue("No settings.gradle file found. Incorrect working directory?", File(Settings.SETTINGS_GRADLE_FILE_NAME).exists())
    }

    private fun checkAppPackageAndComponentName(packageName: String, componentName: String) {
        assert(packageName.isNotBlank()) { "No package name provided. Add PACKAGE_NAME env variable to run configuration" }
        assert(componentName.isNotBlank()) { "No component name provided. Add COMPONENT_NAME env variable to run configuration" }
    }

    private fun recreateIntermediateBuildDirectory() {
        exec("rm -rf ${Params.BUILD_PATH_INTERMEDIATE}; mkdir -p ${Params.BUILD_PATH_INTERMEDIATE}")
    }

    private fun createFinalBuildDirectory() {
        exec("mkdir -p ${Params.BUILD_PATH_FINAL}")
    }

    private fun parseSettingsGradleFile(): Set<Module> {
        val modules = timeMsec("Parsing settings.gradle") {
            SettingsGradleParser(Settings.SETTINGS_GRADLE_FILE_NAME).parse()
        }
        Repository.Modules.setup(modules)
        assertTrue("No Gradle modules parsed", modules.isNotEmpty())
        return modules
    }

    private fun resolveModuleNamesForKotlinCompiler(modules: Set<Module>) {
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
    }

    private fun checkBuildGradleFilesExist(modules: Set<Module>) {
        modules.forEach { module ->
            val buildGradlePath = module.path + "/${Settings.BUILD_GRADLE_FILE}"
            val buildGradleKtsPath = module.path + "/${Settings.BUILD_GRADLE_KTS_FILE}"
            val buildFileExists = File(buildGradlePath).exists() || File(buildGradleKtsPath).exists()

            if (!buildFileExists) {
                Log.d("[WARNING] Module ${module.path} has no build file")
            }
        }
    }

    private fun parseBuildGradleFiles(modules: Set<Module>): List<Pair<Module, Set<Dependency>>> {
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
        return deps
    }

    private fun setupDependencyGraph(deps: List<Pair<Module, Set<Dependency>>>) {
        val graph = deps.associate { (module, deps) -> module to deps }
        Repository.Graph.setup(graph)
    }

    private fun getProjectGitDiffFiles(): Set<String> {
        val (gitDiffOutput) = exec("export LANG=en_US; git status")
        val dirtySourcesResult = GitStatusParser(gitDiffOutput).parse()
        Log.d("Current branch name: ${dirtySourcesResult.branch}\n")
        return dirtySourcesResult.files
    }

    private fun analyzeIncrementalDiff(supportedSourceFiles: List<String>) =
        IncrementalRunDiffer(
            executor = executor,
            intermediateBuildPath = Params.BUILD_PATH_INTERMEDIATE,
            finalBuildPath = Params.BUILD_PATH_FINAL,
        )
            .run(supportedSourceFiles.toSet())
            .let { (compileSourcePaths, removeSourcePaths) ->
                if (Settings.useIncrementalDiff) {
                    Log.d("Incremental diff is enabled\n")
                    compileSourcePaths to removeSourcePaths
                } else {
                    Log.d("Incremental diff is disabled\n")
                    supportedSourceFiles to removeSourcePaths
                }
            }

    private fun composeCompilationRounds(compileSourcePaths: Collection<String>): List<Round> {
        val rounds = CompilationRoundsBuilder(compileSourcePaths.toSet()).build()

        rounds.forEachIndexed { index, round ->
            Log.d("\nRound ${index + 1}:")

            round.items.entries.forEach { (module, sourcePaths) ->
                sourcePaths.forEach { path ->
                    Log.d(" - [${module.name}] $path")
                }
            }
        }
        Log.d("")
        return rounds
    }

    private fun compileRounds(rounds: List<Round>): CompilationStrategy.Result {
        val compileTimeStart = System.currentTimeMillis()

        rounds.forEachIndexed { index, round ->
            val strategy = CompilationStrategySelector.select(round)
            Log.d("Compilation round ${index + 1} -> using ${strategy.javaClass.simpleName}")
            val result = strategy.perform(round)

            if (result is CompilationStrategy.Result.Failed) {
                return result
            }
        }
        val compileTimeEnd = System.currentTimeMillis()

        Log.d("Compilation time: ${(compileTimeEnd - compileTimeStart) / 1000} sec")
        return CompilationStrategy.Result.OK
    }

    private fun addGreenCatSignatures() {
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
    }

    private fun copyIntermediateClassesToFinalBuildDirectory() {
        // Slash symbol is important!
        check(
            exec("cp -r ${Params.BUILD_PATH_INTERMEDIATE}/ ${Params.BUILD_PATH_FINAL}/").successful
        ) { "Failed to copy intermediate build directory into final" }
    }

    private fun cleanupFinalBuildDirectory(removeSourcePaths: Set<String>) {
        FinalBuildDirectoryCleaner(finalBuildPath = Params.BUILD_PATH_FINAL)
            .clean(removeSourcePaths)
            .let { paths ->
                if (paths.isNotEmpty()) {
                    Log.d("\n# Class files removed from ${Params.BUILD_PATH_FINAL} #")
                    paths.forEach(Log::d)
                }
            }
    }

    private fun buildDexPatch() {
        exec("find ${Params.BUILD_PATH_FINAL} -name '*.class'").let { result ->
            if (result.successful) {
                Log.d("# Class entries included into patch #")
                val paths = result.output.toSet()
                check(paths.isNotEmpty()) { "No .class files found at ${Params.BUILD_PATH_FINAL}" }

                // Debug output for all class entries included into patch
                Utils.composeClassEntries(paths).forEach(Log::d)
                Log.d("")
                val dexingTimeStart = System.currentTimeMillis()

                if (!DexPatchCompiler.run(paths)) {
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
    }

    private fun checkAndroidDeviceConnected() {
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
    }

    private fun composePatchDestinationPath(packageName: String): String {
        // Get application last modified timestamp
        val lastUpdateTimestamp = AppLastModifiedTimestampResolver.resolve(packageName)
        Log.d("Package: $packageName, last updated timestamp: $lastUpdateTimestamp")

        // Compose destination DEX patch path
        "${Params.DEX_PATCH_DEST_PATH_PREFIX}${lastUpdateTimestamp}000.dex"

        return "${Params.DEX_PATCH_DEST_PATH_PREFIX}${lastUpdateTimestamp}000.dex".also { path ->
            Log.d("Patch output path: $path")
        }
    }

    private fun removePatchesOnDevice() {
        exec("${Params.ADB_TOOL_PATH} shell rm -f '${Params.DEX_PATCH_DEST_PATH_PREFIX}*.dex'").let { result ->
            if (!result.successful) {
                val message = "Failed to clean previous patch files on Android device:\n" +
                        result.output.joinToString(separator = "\n")
                error(message)
            }
        }
    }

    private fun pushPatchToDevice(destinationPath: String) {
        exec("${Params.ADB_TOOL_PATH} push ${Params.DEX_PATCH_SOURCE_PATH} $destinationPath").let { result ->
            if (result.successful) {
                Log.d("DEX patch successfully pushed to Android device")
            } else {
                error("Failed to push DEX patch to Android device")
            }
        }
    }

    private fun restartApp(packageName: String, componentName: String) {
        // Stop current process
        exec("${Params.ADB_TOOL_PATH} shell am force-stop $packageName").let { result ->
            if (result.successful) {
                Log.d("Force stop application: $packageName")
            } else {
                error("Failed to stop process")
            }
        }

        // Start application
        exec("${Params.ADB_TOOL_PATH} shell am start -n $packageName/$componentName -a android.intent.action.MAIN -c android.intent.category.LAUNCHER")
            .let { result ->
                if (result.successful) {
                    Log.d("Starting $componentName")
                } else {
                    error("Failed to start Activity: $componentName")
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