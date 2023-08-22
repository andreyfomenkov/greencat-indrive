package ru.fomenkov.plugin.strategy

import ru.fomenkov.data.Repository
import ru.fomenkov.data.Round
import ru.fomenkov.plugin.compiler.CompilerPlugin
import ru.fomenkov.plugin.compiler.KotlinCompiler
import ru.fomenkov.plugin.compiler.Params
import ru.fomenkov.utils.Log

class PlainCompilationStrategy : CompilationStrategy {

    override fun perform(round: Round) {
        val compiler = KotlinCompiler(
            round = round,
            compilerPath = Params.KOTLINC,
            plugins = setOf(
                CompilerPlugin(path = Params.PARCELIZE_PLUGIN_PATH),
            ),
            classpath = Repository.Classpath.forProject,
            outputDir = Params.BUILD_PATH_INTERMEDIATE,
        )
        if (compiler.run()) {
            Log.v("\nRound compilation OK")
        } else {
            Log.v("\nRound compilation FAILED:")
            compiler.output().forEach(Log::v)
        }
    }
}