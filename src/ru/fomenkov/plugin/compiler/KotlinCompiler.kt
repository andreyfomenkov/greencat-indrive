package ru.fomenkov.plugin.compiler

import ru.fomenkov.Settings
import ru.fomenkov.data.Round
import ru.fomenkov.shell.CommandBuilder
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.utils.*
import java.io.File
import java.util.concurrent.Callable

class KotlinCompiler(
    private val round: Round,
    private val compilerPath: String,
    private val plugins: Set<CompilerPlugin> = emptySet(),
    private val classpath: Set<String>,
    private val outputDir: String,
) : Compiler {

    private val output = mutableListOf<String>()

    override fun run(): Boolean {
        check(compilerPath.pathExists()) { "No compiler found: $compilerPath" }
        Log.v("")

        val tasks = round.items.map { (module, sources) ->
            val buildPaths = CompilerModuleNameResolver.getPossibleBuildPaths(module)
            val moduleNameParam = CompilerModuleNameResolver.resolve(buildPaths)

            Log.v("Task `$moduleNameParam` has ${sources.size} source(s)")
            createCompilerTask(moduleNameParam, sources)
        }
        val executor = WorkerTaskExecutor()
        val results = try {
            executor.run(tasks)
        } catch (error: Throwable) {
            Log.v("Failed to run compiler task(s): ${error.localizedMessage}")
            return false

        } finally {
            executor.release()
        }
        results.forEach { result ->
            if (!result.successful) {
                output += result.output
            }
        }
        return results.find { result -> !result.successful } == null
    }

    override fun output() = output

    private fun createCompilerTask(moduleNameParam: String, sources: Set<String>) = Callable {
        Log.v("Starting task `$moduleNameParam` with ${sources.size} source(s) on thread [${Thread.currentThread().id}]")
        val startTime = System.currentTimeMillis()
        val classpathStr = classpath.joinToString(separator = ":")
        val cmd = CommandBuilder(compilerPath.escapeSpaces())
            .param("-classpath", classpathStr)
            .param("-Xjvm-default=all-compatibility")
            .param("-Xuse-fast-jar-file-system")
            .param("-jvm-target", Settings.JVM_TARGET)
            .param("-module-name", moduleNameParam)
            .withPlugins(plugins)
            .param(composeFriendModulesParam())
            .param("-d", outputDir)
            .param(sources.joinToString(separator = " "))
            .build()
        val result = exec("export JAVA_OPTS=-Xmx${Settings.JAVA_OPTS_XMX}; $cmd")
        val endTime = System.currentTimeMillis()
        Log.v("Complete task `$moduleNameParam` in ${(endTime - startTime) / 1000} sec")
        result
    }

    private fun composeFriendModulesParam(): String {
        val classpathDirs = classpath.filter { path -> File(path).isDirectory }
        val friendDirs = mutableSetOf<String>()
        friendDirs += Params.BUILD_PATH_INTERMEDIATE
        friendDirs += Params.BUILD_PATH_FINAL

        round.items.keys.forEach { module ->
            val moduleBuildPath = "/${module.path}/build/"

            classpathDirs.forEach { directory ->
                if (moduleBuildPath in directory) {
                    friendDirs += directory
                }
            }
        }
        return "-Xfriend-paths=" + friendDirs.joinToString(separator = ",")
    }

    private fun CommandBuilder.withPlugins(plugins: Set<CompilerPlugin>): CommandBuilder {
        plugins.forEach { plugin ->
            param("-Xplugin=${plugin.path.escapeSpaces()}")
            plugin.options.forEach { (key, value) -> param("-P plugin:${plugin.id}:$key=$value") }
        }
        return this
    }
}