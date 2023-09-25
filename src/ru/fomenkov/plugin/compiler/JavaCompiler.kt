package ru.fomenkov.plugin.compiler

import ru.fomenkov.Settings
import ru.fomenkov.shell.CommandBuilder
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.utils.*

class JavaCompiler(
    private val sources: Set<String>,
    private val classpath: Set<String>,
    private val outputDir: String,
) : Compiler {

    private val output = mutableListOf<String>()

    override fun run(): Boolean {
        if (sources.isEmpty()) {
            Log.v("No source files provided for Java compiler")
            return true
        }
        Log.v("\nStarting javac task with ${sources.size} source(s) on thread [${Thread.currentThread().id}]")
        val startTime = System.currentTimeMillis()
        val classpathStr = classpath.joinToString(separator = ":")
        val cmd = CommandBuilder("javac")
            .param("-classpath", classpathStr)
            .param("-g")
            .param("-d", outputDir)
            .param(sources.joinToString(separator = " "))
            .build()
        val result = exec("export JAVA_OPTS=-Xmx${Settings.JAVA_OPTS_XMX}; $cmd")
        val endTime = System.currentTimeMillis()
        Log.v("Complete javac task in ${endTime - startTime} ms")

        if (!result.successful) {
            output += result.output
        }
        return result.successful
    }

    override fun output() = output
}