package ru.fomenkov.plugin.compiler

import ru.fomenkov.data.Round
import ru.fomenkov.shell.CommandBuilder
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.utils.escapeSpaces
import ru.fomenkov.utils.pathExists
import java.io.File

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

        val allSourcePaths = extractAllSourcePaths(round).joinToString(separator = " ")
        val classpathStr = classpath.joinToString(separator = ":")
        val cmd = CommandBuilder(compilerPath.escapeSpaces())
            .param("-classpath", classpathStr)
//            .param("-Xjvm-default=all-compatibility")
//            .param("-Xuse-fast-jar-file-system")
//            .param("-module-name", getModuleName())
            .withPlugins(plugins)
            .param(composeFriendModulesParam())
            .param("-d", outputDir)
            .param(allSourcePaths)
            .build()
        val result = exec(cmd)
        output.clear()
        output += result.output

        return result.successful
    }

    override fun output() = output

    private fun extractAllSourcePaths(round: Round): Set<String> {
        val paths = mutableSetOf<String>()
        round.items.values.forEach { paths += it }
        return paths
    }

    private fun getModuleName(): String {
        return "main_debug" // TODO: or app_gmsInDriverDebug? (see build logs)
    }

    private fun composeFriendModulesParam(): String {
        val classpathDirs = classpath.filter { path -> File(path).isDirectory }
        val friendDirs = mutableSetOf<String>()

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