package ru.fomenkov.plugin.strategy

import ru.fomenkov.data.Module
import ru.fomenkov.data.Repository
import ru.fomenkov.data.Round
import ru.fomenkov.parser.MetadataDescriptorParser
import ru.fomenkov.plugin.dagger.DaggerComponentsResolver
import ru.fomenkov.plugin.dagger.DaggerGeneratedClassesResolver
import ru.fomenkov.plugin.LibDependenciesResolver
import ru.fomenkov.plugin.compiler.CompilerPlugin
import ru.fomenkov.plugin.compiler.JavaCompiler
import ru.fomenkov.plugin.compiler.KotlinCompiler
import ru.fomenkov.plugin.compiler.Params
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.utils.Log
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
            round = round,
            compilerPath = Params.KOTLINC,
            plugins = setOf(
                setupDaggerPlugin(kaptClasspath = jarPaths),
            ),
            classpath = classpath,
            outputDir = Params.BUILD_PATH_INTERMEDIATE,
        )

        // Run KAPT and generate stubs
        try {
            kaptCompiler.run()
            Log.v("KAPT successful")

        } catch (error: Throwable) {
            Log.v("Failed to run KAPT: ${error.message}")
            return CompilationStrategy.Result.Failed(kaptCompiler.output())

        } finally {
            // Rename *_Factory.class_TEMP and *_MembersInjector.class_TEMP files back
            renameFactoryClasses(daggerClassesToRename, toTemp = false)
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

        // Create compiler for Kotlin files
        val kotlinCompiler = KotlinCompiler(
            // Find all @Component classes and add to the sources for the related modules
            // Necessary to compile when *_Factory or *_MembersInjector classes are changed
            round = withModuleComponentSources(round),
            compilerPath = Params.KOTLINC,
            plugins = setOf(
                CompilerPlugin(path = Params.PARCELIZE_PLUGIN_PATH),
            ),
            classpath = classpath,
            outputDir = Params.BUILD_PATH_INTERMEDIATE,
        )

        // Create worker tasks
        val compilerTasks = listOf(
            Callable {
                javaCompiler.run().also { result -> Log.v("Running javac ${if (result) "OK" else "FAILED"}") }
            },
            Callable {
                kotlinCompiler.run().also { result -> Log.v("Running kotlinc ${if (result) "OK" else "FAILED"}") }
            }
        )

        // Run kotlinc and javac concurrently
        val compilerResults = WorkerTaskExecutor().let { executor ->
            try {
                executor.run(compilerTasks)

            } catch (error: Throwable) {
                Log.v("Failed to run javac or/and kotlinc: ${error.message}")
                val javacOutput = javaCompiler.output()
                val kotlincOutput = kotlinCompiler.output()
                return CompilationStrategy.Result.Failed(javacOutput + kotlincOutput)

            } finally {
                executor.release()
            }
        }

        // Remove Dagger related KAPT directories to exclude *.class files from the resulting DEX patch
        removeDaggerKaptDirectories()

        return if (compilerResults.contains(false)) {
            CompilationStrategy.Result.Failed(javaCompiler.output() + kotlinCompiler.output())
        } else {
            CompilationStrategy.Result.OK
        }
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