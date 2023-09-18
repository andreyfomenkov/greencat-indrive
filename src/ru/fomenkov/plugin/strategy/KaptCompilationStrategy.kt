package ru.fomenkov.plugin.strategy

import ru.fomenkov.data.Round
import ru.fomenkov.plugin.DaggerFactoryClassPathsResolver
import ru.fomenkov.plugin.compiler.CompilerPlugin
import ru.fomenkov.plugin.compiler.KotlinCompiler
import ru.fomenkov.plugin.compiler.Params
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.utils.Log
import java.io.File

class KaptCompilationStrategy : CompilationStrategy {

    private val tempFileSuffix = "_TEMP"

    override fun perform(round: Round): CompilationStrategy.Result {

        // TODO: rename for _Factory.class (and other Dagger classes?)
        val classpath = setOf(
            "${Params.BUILD_PATH_INTERMEDIATE}/${Params.KAPT_CLASSES_DIR}", // incrementalData
            // Jetified javapoet
            "/Users/andreyfomenkov/.gradle/caches/transforms-3/4a588e101a380150363b2667af1f7111/transformed/jetified-javapoet-1.13.0.jar",
            // Jetified KSP API
            "/Users/andreyfomenkov/.gradle/caches/transforms-3/6e9f92327f2736e2d8ae346d6165e7c6/transformed/jetified-symbol-processing-api-1.8.0-1.0.9.jar",
            // Jetified kotlinpoet
            "/Users/andreyfomenkov/.gradle/caches/transforms-3/f86b3214b4e44e3dc979dd8333364d6c/transformed/jetified-kotlinpoet-1.8.0.jar",
            // Jetified metadata
//            "/Users/andreyfomenkov/.gradle/caches/transforms-3/e15525eafe3c27b914cd3b037b3543ac/transformed/jetified-kotlinx-metadata-jvm-0.5.0.jar",
        ) + getProjectClasspath()

        // Dagger won't generate factory classes once it has been found in project classpath
        // Rename all *_Factory.class files to *_Factory.class_TEMP related to Dagger
        val tempSourcePaths = round.items.flatMap { (module, paths) ->
            DaggerFactoryClassPathsResolver(module, paths, classpath).resolve()
        }.toSet()
        renameFactoryClasses(tempSourcePaths, toTemp = true)

        // Build stubs and run annotation processing (kapt only)
        val kaptCompiler = KotlinCompiler(
            round = round,
            compilerPath = Params.KOTLINC,
            plugins = setOf(
                setupDaggerPlugin(),
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
            // Rename *_Factory.class_TEMP files back to *_Factory.class
            renameFactoryClasses(tempSourcePaths, toTemp = false)
        }

        // Remove generated NonExistentClass.java class
        removeNonExistentGeneratedJavaFile()

        // Run javac for KAPT generated Java classes and kotlinc for Kotlin files
        val kotlinCompiler = KotlinCompiler(
            round = round, // TODO: find and add Component classes from modules declared in the current round
            compilerPath = Params.KOTLINC,
            plugins = setOf(
                CompilerPlugin(path = Params.PARCELIZE_PLUGIN_PATH),
            ),
            classpath = classpath,
            outputDir = Params.BUILD_PATH_INTERMEDIATE,
        )
        if (kotlinCompiler.run()) {
            Log.v("Kotlinc successful")
        } else {
            Log.v("Failed to run kotlinc")
            kotlinCompiler.output().forEach(Log::v)
            return CompilationStrategy.Result.Failed(kotlinCompiler.output())
        }

        // Remove Dagger related KAPT directories to exclude *.class files from the result DEX patch
        removeDaggerKaptDirectories()

        return CompilationStrategy.Result.Failed(kaptCompiler.output()) // TODO: + Result.OK
    }

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

    private fun setupDaggerPlugin(): CompilerPlugin {
        val kaptClasspath = setOf(
            // TODO: find JAR for dagger-spi
            "/Users/andreyfomenkov/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger-spi/2.47/2505d4c1dc4765c99944c28381558fcb1c59212d/dagger-spi-2.47.jar",
            // TODO: find JAR for dagger-compiler
            "/Users/andreyfomenkov/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger-compiler/2.47/72c204d7b3593713c8e390f179bad15b596596c2/dagger-compiler-2.47.jar",
        )
        kaptClasspath.forEach { path -> // TODO: for debugging, remove
            check(File(path).exists()) { "File doesn't exist: $path" }
        }
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