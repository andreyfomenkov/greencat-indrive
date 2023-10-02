package ru.fomenkov.plugin.strategy

import ru.fomenkov.data.Module
import ru.fomenkov.data.Repository
import ru.fomenkov.data.Round
import ru.fomenkov.parser.MetadataDescriptorParser
import ru.fomenkov.plugin.dagger.DaggerComponentsResolver
import ru.fomenkov.plugin.dagger.DaggerGeneratedClassesResolver
import ru.fomenkov.plugin.LibDependenciesResolver
import ru.fomenkov.plugin.bytecode.ClassFileInjectionSnapshotMaker
import ru.fomenkov.plugin.compiler.CompilerPlugin
import ru.fomenkov.plugin.compiler.JavaCompiler
import ru.fomenkov.plugin.compiler.KotlinCompiler
import ru.fomenkov.plugin.compiler.Params
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.utils.Log
import ru.fomenkov.utils.Utils
import ru.fomenkov.utils.WorkerTaskExecutor
import java.io.File
import java.util.concurrent.Callable

class KaptCompilationStrategy : CompilationStrategy {

    private val tempFileSuffix = "_TEMP"
    private val daggerGroupId = "com.google.dagger"
    private val daggerCompilerArtifactId = "dagger-compiler"
    private val daggerSpiArtifactId = "dagger-spi"
    private val metadataDescriptorParser = MetadataDescriptorParser()

    override fun perform(round: Round): CompilationStrategy.Result {
        // Get current Dagger version
        val daggerVersion = Repository.LibraryVersions.get("dagger")

        if (daggerVersion == null) {
            error("No Dagger version found")
        } else {
            Log.v("Dagger version: $daggerVersion")
        }

        // Resolver for `dagger-compiler` library and dependencies
        val daggerCompilerResolver = LibDependenciesResolver(
            groupId = daggerGroupId,
            artifactId = daggerCompilerArtifactId,
            version = daggerVersion,
            metadataDescriptorParser = metadataDescriptorParser,
        )

        // Resolver for `dagger-spi` library and dependencies
        val daggerSpiResolver = LibDependenciesResolver(
            groupId = daggerGroupId,
            artifactId = daggerSpiArtifactId,
            version = daggerVersion,
            metadataDescriptorParser = metadataDescriptorParser,
        )

        // Resolve Dagger JAR paths with dependencies
        val resolverResults = WorkerTaskExecutor().let { executor ->
            try {
                executor.run(
                    listOf(
                        Callable { daggerCompilerResolver.resolveLib() to daggerCompilerResolver.resolveDependencies(transitive = false) },
                        Callable { daggerSpiResolver.resolveLib() to daggerSpiResolver.resolveDependencies(transitive = false) },
                    )
                )
            } catch (error: Throwable) {
                val output = listOf(error.stackTraceToString())
                return CompilationStrategy.Result.Failed(output)

            } finally {
                executor.release()
            }
        }

        // Resolved Dagger JAR paths
        val jarPaths = resolverResults.map { (jarPath, _) -> jarPath }.toSet()

        // Resolved Dagger dependencies paths
        val dependenciesPaths = resolverResults.flatMap { (_, deps) -> deps }.toSet()

        // Resulting classpath
        val classpath = setOf(
            "${Params.BUILD_PATH_INTERMEDIATE}/${Params.KAPT_CLASSES_DIR}", // incrementalData
        ) + dependenciesPaths + getProjectClasspath()

        // Create compiler for Kotlin files
        val kotlinCompiler = KotlinCompiler(
            round = round,
            compilerPath = Params.KOTLINC,
            plugins = setOf(
                CompilerPlugin(path = Params.PARCELIZE_PLUGIN_PATH),
            ),
            classpath = classpath,
            outputDir = Params.BUILD_PATH_INTERMEDIATE,
        )

        // Run Kotlin compiler
        val kotlinCompilerResult = try {
            kotlinCompiler.run().also { Log.v("Kotlin compiler execution successful") }
        } catch (error: Throwable) {
            Log.v("Kotlin compiler execution failed: ${error.message}")
            false
        }

        // Check out Kotlin compiler result
        if (!kotlinCompilerResult) {
            Log.v("Failed to run Kotlin compiler\n")
            return CompilationStrategy.Result.Failed(kotlinCompiler.output())
        }

        // Check whether to run KAPT or not
        if (!isKaptRunNeeded(round, classpath)) {
            Log.v("No KAPT running needed")
            return CompilationStrategy.Result.OK
        }
        Log.v("Running KAPT...")

        // Dagger won't generate factory and members injector classes once it has been found in project classpath
        // Add to Dagger related *_Factory.class and *_MembersInjector.class files temporary suffix
        val daggerClassesToRename = round.items
            .flatMap { (module, paths) ->
                DaggerGeneratedClassesResolver(module, paths, classpath).resolve()
            }
            .toSet() + listDaggerClassFilesToRename()

        daggerClassesToRename.forEach { path -> Log.v("[RENAME] $path") }
        renameFactoryClasses(daggerClassesToRename, toTemp = true)

        // Create compiler for Dagger KAPT and generating stubs
        val kaptCompiler = KotlinCompiler(
            // Find all @Component classes and add to the sources for the related modules
            // Necessary to compile when *_Factory or *_MembersInjector classes are changed
            round = withModuleComponentSources(round),
            compilerPath = Params.KOTLINC,
            plugins = setOf(
                setupDaggerPlugin(kaptClasspath = jarPaths),
            ),
            classpath = classpath,
            outputDir = Params.BUILD_PATH_INTERMEDIATE,
        )

        // Run KAPT and generate stubs
        val kaptResult = try {
            kaptCompiler.run().also { Log.v("KAPT execution successful") }
        } catch (error: Throwable) {
            Log.v("KAPT execution failed: ${error.message}")
            false

        } finally {
            // Rename *_Factory.class_TEMP and *_MembersInjector.class_TEMP files back
            renameFactoryClasses(daggerClassesToRename, toTemp = false)
        }

        // Check out KAPT result
        if (!kaptResult) {
            Log.v("Failed to run KAPT\n")
            return CompilationStrategy.Result.Failed(kaptCompiler.output())
        }

        // Remove generated NonExistentClass.java class
        removeNonExistentGeneratedJavaFile()

        // Find all Dagger generated *.java classes to compile
        val javaSources = listDaggerJavaClassesToCompile()

        // List generated *.java files to compile
        javaSources.forEach { path -> Log.v("Java class found: $path") }

        // Create compiler for KAPT generated Java classes
        val javaCompiler = JavaCompiler(
            sources = javaSources,
            classpath = classpath,
            outputDir = Params.BUILD_PATH_INTERMEDIATE,
        )

        // Run Java compiler to compile KAPT generated classes
        val javaCompilerResult = try {
            javaCompiler.run().also { Log.v("Java compiler execution successful") }
        } catch (error: Throwable) {
            Log.v("Java compiler execution failed: ${error.message}")
            false
        }

        // Check out Java compiler result
        if (!javaCompilerResult) {
            Log.v("Failed to run Java compiler\n")
            return CompilationStrategy.Result.Failed(javaCompiler.output())
        }

        // Remove Dagger related KAPT directories to exclude *.class files from the resulting DEX patch
        removeDaggerKaptDirectories()

        // Compilation successful
        return CompilationStrategy.Result.OK
    }

    private fun isKaptRunNeeded(round: Round, classpath: Set<String>): Boolean {
        Log.v("\nInjection snapshot hash values:")
        val sources = round.items
            .flatMap { entry -> entry.value }
            .map { path -> Utils.extractSourceFilePathInModule(path, removeExtension = true) }

        sources.forEach { reference ->
            // "Previous" class file for path in final directory (if exists)...
            var classInFinalDir = File("${Params.BUILD_PATH_FINAL}/$reference.class")

            // ... or if doesn't exist, try to find in project classpath, excluding greencat build directories
            if (!classInFinalDir.exists()) {
                val modulePaths = round.items.map { (module, _) -> module.path }
                classpath
                    .asSequence()
                    .filterNot { path -> Params.BUILD_PATH_FINAL in path }
                    .filterNot { path -> Params.BUILD_PATH_INTERMEDIATE in path }
                    .filterNot { path -> "/tmp/kapt3/" in path } // Exclude classes from KAPT directory
                    .filter { path ->
                        modulePaths.find { modulePath -> "$modulePath/" in path } != null
                    }
                    .map { path -> File("$path/$reference.class") }
                    .firstOrNull(File::exists)
                    ?.let { file -> classInFinalDir = file }
            }
            // "Current" class file for path in intermediate directory
            val classInIntermediateDir = File("${Params.BUILD_PATH_INTERMEDIATE}/$reference.class")
            val finalHashValue = classInFinalDir
                .takeIf(File::exists)
                ?.let { file -> ClassFileInjectionSnapshotMaker.make(file.absolutePath) }

            val intermediateHashValue = classInIntermediateDir
                .takeIf(File::exists)
                ?.let { file -> ClassFileInjectionSnapshotMaker.make(file.absolutePath) }

            Log.v("- [CLASS BEFORE] ${classInFinalDir.absolutePath}, #: $finalHashValue")
            Log.v("- [CLASS AFTER]  ${classInIntermediateDir.absolutePath}, #: $intermediateHashValue")

            // Compare Dagger injection snapshots for both class files
            if (intermediateHashValue != finalHashValue) {
                Log.v(" - [INJECTION SNAPSHOT] $reference [CHANGED] => run KAPT")
                return true
            } else {
                Log.v(" - [INJECTION SNAPSHOT] $reference [-]")
            }
        }
        return false
    }

    private fun withModuleComponentSources(round: Round): Round {
        val items = mutableMapOf<Module, Set<String>>()

        round.items.forEach { (module, sources) ->
            val componentKtSources = DaggerComponentsResolver(module.path).resolve()
            items += module to sources + componentKtSources
            componentKtSources.forEach { path -> Log.v("[@Component] module = ${module.name}, $path") }
        }
        return Round(items)
    }

    private fun listDaggerClassFilesToRename() = listOf(
        "*_Factory.class",
        "*_MembersInjector.class",
    )
        .flatMap { fileMask ->
            exec("find ${Params.BUILD_PATH_FINAL} -name '$fileMask'").let { result ->
                if (result.successful) {
                    result.output
                } else {
                    error("Failed to list Dagger *.java files for mask: $fileMask")
                }
            }
        }.toSet()

    private fun listDaggerJavaClassesToCompile() = listOf(
        "*_Factory.java",
        "*_MembersInjector.java",
        "Dagger*.java", // Component Java classes
    )
        .flatMap { fileMask ->
            exec("find ${Params.BUILD_PATH_INTERMEDIATE} -name '$fileMask'").let { result ->
                if (result.successful) {
                    result.output
                } else {
                    error("Failed to list Dagger *.java files for mask: $fileMask")
                }
            }
        }.toSet()

    private fun renameFactoryClasses(paths: Set<String>, toTemp: Boolean) {
        paths.forEach { path ->
            val src = File(path)
            val dst = File(path + tempFileSuffix)

            if (toTemp) {
                check(src.renameTo(dst)) { "Failed to rename factory class: $path" }
                Log.v("Factory class renamed: ${src.name} -> ${dst.name}")
            } else {
                check(dst.renameTo(src)) { "Failed to rename factory class back: $path" }
                Log.v("Factory class renamed back: ${dst.name} -> ${src.name}")
            }
        }
    }

    private fun removeDaggerKaptDirectories() {
        exec("rm -rf ${Params.BUILD_PATH_INTERMEDIATE}/${Params.KAPT_STUBS_DIR}")
        exec("rm -rf ${Params.BUILD_PATH_INTERMEDIATE}/${Params.KAPT_CLASSES_DIR}")
        exec("rm -rf ${Params.BUILD_PATH_INTERMEDIATE}/${Params.KAPT_SOURCES_DIR}")
        exec("rm -rf ${Params.BUILD_PATH_INTERMEDIATE}/${Params.KAPT_INCREMENTAL_DATA_DIR}")
    }

    private fun removeNonExistentGeneratedJavaFile() {
        val paths = exec("find ${Params.BUILD_PATH_INTERMEDIATE} -name '*.java'").let { result ->
            check(result.successful) { "Failed to list *.java files in ${Params.BUILD_PATH_INTERMEDIATE} directory" }
            result.output
        }
        paths.forEach { path ->
            if (path.endsWith("error/NonExistentClass.java")) {
                check(exec("rm $path").successful) { "Failed to remove file: $path" }
            }
        }
    }

    private fun setupDaggerPlugin(kaptClasspath: Set<String>): CompilerPlugin {
        val options = mutableListOf(
            "aptMode" to "stubsAndApt",
            "javacArguments" to "rO0ABXcEAAAAAA",
            "correctErrorTypes" to "true",
            "useLightAnalysis" to "true",
            "includeCompileClasspath" to "true",
            "dumpDefaultParameterValues" to "false",
            "mapDiagnosticLocations" to "false",
            "stripMetadata" to "true",
            "keepKdocCommentsInStubs" to "true",
            "detectMemoryLeaks" to "default",
            "infoAsWarnings" to "false",
            "processIncrementally" to "false",
            "verbose" to "false",
            "classes" to Params.BUILD_PATH_INTERMEDIATE + "/" + Params.KAPT_CLASSES_DIR,
            "sources" to Params.BUILD_PATH_INTERMEDIATE + "/" + Params.KAPT_SOURCES_DIR,
            "stubs" to Params.BUILD_PATH_INTERMEDIATE + "/" + Params.KAPT_STUBS_DIR,
            "incrementalData" to Params.BUILD_PATH_INTERMEDIATE + "/" + Params.KAPT_INCREMENTAL_DATA_DIR,
        )
        kaptClasspath.forEach { path -> options += "apclasspath" to path }

        return CompilerPlugin(
            id = "org.jetbrains.kotlin.kapt3",
            path = Params.KAPT_PLUGIN_PATH,
            options = options,
        )
    }
}