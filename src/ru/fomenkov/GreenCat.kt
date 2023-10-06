package ru.fomenkov

import org.junit.Assert
import ru.fomenkov.data.Dependency
import ru.fomenkov.data.Module
import ru.fomenkov.data.Repository
import ru.fomenkov.data.Round
import ru.fomenkov.parser.*
import ru.fomenkov.plugin.ArgumentsParser
import ru.fomenkov.plugin.CompilationRoundsBuilder
import ru.fomenkov.plugin.FinalBuildDirectoryCleaner
import ru.fomenkov.plugin.IncrementalRunDiffer
import ru.fomenkov.plugin.bytecode.ClassFileSignatureSupplier
import ru.fomenkov.plugin.compiler.DexPatchCompiler
import ru.fomenkov.plugin.compiler.Params
import ru.fomenkov.plugin.strategy.CompilationStrategy
import ru.fomenkov.plugin.strategy.CompilationStrategySelector
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.shell.ShellCommandsValidator
import ru.fomenkov.utils.CompilerModuleNameResolver
import ru.fomenkov.utils.Log
import ru.fomenkov.utils.Utils
import ru.fomenkov.utils.WorkerTaskExecutor
import java.io.File
import java.util.concurrent.Callable

fun main(vararg args: String) {
    with(Settings) {
        displayModuleDependencies = false
        displayResolvingChildModules = false
        displayKotlinCompilerModuleNames = false
        useIncrementalDiff = true
        usePlainCompilationStrategyOnly = false
    }
    Log.level = Log.Level.INFO
    ArgumentsParser(args.toList())
        .parse()
        ?.let { result ->
            if (result.verboseLogging) {
                Log.level = Log.Level.VERBOSE
            }
            val executor = WorkerTaskExecutor()
            val plugin = GreenCat(executor)
            try {
                plugin.launch(
                    classpath = result.classpath.split(':').toSet(),
                    packageName = result.packageName,
                    componentName = result.componentName,
                )
            } catch (error: Throwable) {
                Utils.printTextInFrame(error.localizedMessage)
            } finally {
                plugin.release()
            }
        }
}

class GreenCat(private val executor: WorkerTaskExecutor) {

    fun launch(
        classpath: Set<String>,
        packageName: String,
        componentName: String,
    ) {
        Repository.Classpath.set(classpath)
        val totalTimeStart = System.currentTimeMillis()

        // Display plugin info
        Log.i("GreenCat v${Settings.GREENCAT_VERSION}")
        Log.i("GitHub: https://github.com/andreyfomenkov/greencat-indrive\n")

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

        // Parse library versions
        val libraryVersions = LibVersionsParser(Params.LIBRARY_VERSIONS_FILE_PATH).parse()
        Repository.LibraryVersions.setup(libraryVersions)

        // Parse settings.gradle file
        val modules = parseSettingsGradleFile()

        // Resolve module names for Kotlin compiler `-module-name` param
        resolveModuleNamesForKotlinCompiler(modules)

        // Check build.gradle or build.gradle.kts files exist for the declared modules
        checkBuildGradleFilesExist(modules)

        // Parse build.gradle files for declared modules
        val deps = parseBuildGradleFiles(modules, executor)

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
        val (branchName, gitDiffFiles) = getProjectGitDiffFiles()

        // Split supported and not supported source files
        val (supportedSourceFiles, unknownSourceFiles) = gitDiffFiles.partition(Utils::isSourceFileSupported)

        // Show details in case no supported source files found
        var isEmptyGitDiff = false

        if (supportedSourceFiles.isEmpty() && unknownSourceFiles.isEmpty()) {
            Log.d("\nNothing to compile. Please modify supported files to proceed")
            isEmptyGitDiff = true

        } else if (supportedSourceFiles.isEmpty()) {
            unknownSourceFiles.forEach { path -> Log.d(" - (NOT SUPPORTED) $path") }
            Log.d("\nNo supported source files to compile. Please modify supported files to proceed")
            isEmptyGitDiff = true
        }

        // Analyze incremental diff
        val (compileSourcePaths, removeSourcePaths) = if (supportedSourceFiles.isEmpty()) {
            emptySet<String>() to emptySet()
        } else {
            analyzeIncrementalDiff(supportedSourceFiles, executor)
        }

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

        // Print diff details
        printSourcesDiff(branchName, supportedSourceFiles, compileSourcePaths.toList(), unknownSourceFiles)

        // Check single Android device is connected
        checkAndroidDeviceConnected()

        // Compose destination DEX patch path
        val dstDexPatchPath = composePatchDestinationPath(packageName)

        // Decide what to do in case nothing to compile
        when {
            isEmptyGitDiff -> {
                Log.d("\n# Git diff is empty #")
                Log.d(" - clear intermediate build directory")
                Log.d(" - remove final build directory")
                Log.d(" - remove patch on Android device")
                Log.d(" - restart application")

                recreateIntermediateBuildDirectory()
                removeFinalBuildDirectory()
                removePatchesOnDevice()
                restartApp(packageName, componentName)

                Utils.printTextInFrame("No supported files to compile or diff is empty. Only *.kt sources are allowed")
                return
            }
            compileSourcePaths.isEmpty() && removeSourcePaths.isEmpty() -> {
                Log.d("\n# Nothing to compile and remove since the last incremental run #")
                Log.d(" - push existing patch if any to Android device")
                Log.d(" - restart application")

                if (File(Params.DEX_PATCH_SOURCE_PATH).exists()) {
                    pushPatchToDevice(dstDexPatchPath)
                }
                restartApp(packageName, componentName)

                Utils.printTextInFrame("Everything is up to date since the last incremental run")
                return
            }
            compileSourcePaths.isEmpty() -> {
                Log.d("\n# Nothing to compile, but some sources are removed #")
                Log.d(" - clear final build directory from removed classes")
                Log.d(" - recompile DEX patch")
                Log.d(" - push patch to Android device")
                Log.d(" - restart application")

                cleanupFinalBuildDirectory(removeSourcePaths)
                buildDexPatch()
                pushPatchToDevice(dstDexPatchPath)
                restartApp(packageName, componentName)

                printPatchSize()
                printTotalTimeSpent(totalTimeStart)
                return
            }
        }

        // Cleanup unnecessary .class files in the final build directory
        cleanupFinalBuildDirectory(removeSourcePaths)

        // Compose compilation rounds
        val rounds = composeCompilationRounds(compileSourcePaths)
        Log.i("Building patch...")

        // Perform compilation for all rounds
        compileRounds(rounds).let { result ->
            if (result is CompilationStrategy.Result.Failed) {
                Log.d("\n# Compilation failed #")
                Log.d(" - clear intermediate build directory")
                Log.d(" - remove final build directory")
                Log.d("")

                result.output.forEach(Log::d)
                recreateIntermediateBuildDirectory()
                removeFinalBuildDirectory()

                if (Log.level == Log.Level.INFO) {
                    Log.dumpDebugLogs()
                }
                Log.i("")
                Utils.printTextInFrame("Compilation failed")
                return
            }
        }

        // Add Greencat signature for all generated .class files
        addGreenCatSignatures()

        // Recursively copy intermediate .class files to the final build directory
        copyIntermediateClassesToFinalBuildDirectory()

        // Build DEX patch
        buildDexPatch()

        // Check single Android device is connected
        checkAndroidDeviceConnected()

        // Remove previous patch files on Android device
        removePatchesOnDevice()

        // Push DEX patch
        pushPatchToDevice(dstDexPatchPath)

        // Restart app
        restartApp(packageName, componentName)

        // Total time and patch size output
        printPatchSize()
        printTotalTimeSpent(totalTimeStart)
    }

    private fun printSourcesDiff(
        branchName: String,
        supportedFiles: List<String>,
        compiledFiles: List<String>,
        unknownFiles: List<String>,
    ) {
        if (supportedFiles.isNotEmpty()) {
            Log.i("Source files to compile on branch `$branchName`:")

            supportedFiles.forEach { path ->
                if (path in compiledFiles) {
                    Log.i(" - [*] $path")
                } else {
                    Log.i(" - [ ] $path")
                }
            }
        }
        if (unknownFiles.isNotEmpty()) {
            if (supportedFiles.isNotEmpty()) {
                Log.i("")
            }
            Log.i("Ignored files (no supported):")

            unknownFiles.forEach { path -> Log.i(" - $path") }
        }
        if (supportedFiles.isNotEmpty() || unknownFiles.isNotEmpty()) {
            Log.i("")
        }
    }

    private fun printPatchSize() {
        val dexFile = File(Params.DEX_PATCH_SOURCE_PATH)
        check(dexFile.exists()) { "No DEX file found: ${Params.DEX_PATCH_SOURCE_PATH}" }

        val patchSizeKb = dexFile.length() / 1024
        Log.i("Patch size: $patchSizeKb KB\n")
    }

    private fun printTotalTimeSpent(totalTimeStart: Long) {
        val totalTimeSec = (System.currentTimeMillis() - totalTimeStart) / 1000
        Utils.printTextInFrame("Build & deploy complete in $totalTimeSec sec")
    }

    private fun checkWorkingDirectory() {
        Log.d("Current working directory: ${File("").absolutePath}")
        Assert.assertTrue(
            "No settings.gradle file found. Incorrect working directory?",
            File(Settings.SETTINGS_GRADLE_FILE_NAME).exists()
        )
    }

    private fun checkAppPackageAndComponentName(packageName: String, componentName: String) {
        assert(packageName.isNotBlank()) { "No package name provided. Add PACKAGE_NAME env variable to run configuration" }
        assert(componentName.isNotBlank()) { "No component name provided. Add COMPONENT_NAME env variable to run configuration" }
    }

    private fun recreateIntermediateBuildDirectory() {
        exec("rm -rf ${Params.BUILD_PATH_INTERMEDIATE}; mkdir -p ${Params.BUILD_PATH_INTERMEDIATE}")
    }

    private fun removeFinalBuildDirectory() {
        exec("rm -rf ${Params.BUILD_PATH_FINAL}")
    }

    private fun createFinalBuildDirectory() {
        exec("mkdir -p ${Params.BUILD_PATH_FINAL}")
    }

    private fun parseSettingsGradleFile(): Set<Module> {
        val modules = timeMsec("Parsing settings.gradle") {
            SettingsGradleParser(Settings.SETTINGS_GRADLE_FILE_NAME).parse()
        }
        Repository.Modules.setup(modules)
        Assert.assertTrue("No Gradle modules parsed", modules.isNotEmpty())
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

    private fun parseBuildGradleFiles(
        modules: Set<Module>,
        executor: WorkerTaskExecutor,
    ): List<Pair<Module, Set<Dependency>>> {
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
        Assert.assertTrue("No modules and dependencies", deps.isNotEmpty())
        return deps
    }

    private fun setupDependencyGraph(deps: List<Pair<Module, Set<Dependency>>>) {
        val graph = deps.associate { (module, deps) -> module to deps }
        Repository.Graph.setup(graph)
    }

    private fun getProjectGitDiffFiles(): Pair<String, Set<String>> {
        val (gitDiffOutput) = exec("export LANG=en_US; git status")
        val dirtySourcesResult = GitStatusParser(gitDiffOutput).parse()
        Log.d("Current branch name: ${dirtySourcesResult.branch}\n")
        return dirtySourcesResult.branch to dirtySourcesResult.files
    }

    private fun analyzeIncrementalDiff(
        supportedSourceFiles: List<String>,
        executor: WorkerTaskExecutor,
    ) = IncrementalRunDiffer(
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
        check(compileSourcePaths.isNotEmpty()) { "No source paths to compile" }
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

    private fun<T> timeMsec(step: String, action: () -> T): T {
        val start = System.currentTimeMillis()
        val result = action()
        val end = System.currentTimeMillis()

        Log.d("# $step: ${end - start} msec\n")
        return result
    }

    fun release() {
        executor.release()
    }
}