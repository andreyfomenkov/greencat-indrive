package ru.fomenkov.plugin.strategy

import ru.fomenkov.data.Round
import ru.fomenkov.plugin.compiler.CompilerPlugin
import ru.fomenkov.plugin.compiler.KotlinCompiler
import ru.fomenkov.plugin.compiler.Params

class PlainCompilationStrategy : CompilationStrategy {

    override fun perform(round: Round): CompilationStrategy.Result {
        val compiler = KotlinCompiler(
            round = round,
            compilerPath = Params.KOTLINC,
            plugins = setOf(
                CompilerPlugin(path = Params.PARCELIZE_PLUGIN_PATH),
            ),
            classpath = getProjectClasspath(),
            outputDir = Params.BUILD_PATH_INTERMEDIATE,
        )
        return if (compiler.run()) {
            CompilationStrategy.Result.OK
        } else {
            CompilationStrategy.Result.Failed(compiler.output())
        }
    }
}