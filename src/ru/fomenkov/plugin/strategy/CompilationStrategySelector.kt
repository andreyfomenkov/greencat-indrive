package ru.fomenkov.plugin.strategy

import ru.fomenkov.data.Module
import ru.fomenkov.data.Round
import ru.fomenkov.utils.Log
import java.io.File

object CompilationStrategySelector {

    private const val DAGGER_IMPORT_PREFIX = "dagger."
    private const val INJECT_ANNOTATION = "javax.inject.Inject"

    fun select(round: Round): CompilationStrategy {
        val daggerGraphSources = mutableMapOf<Module, MutableSet<String>>()
        val plainSources = mutableMapOf<Module, MutableSet<String>>()

        round.items.forEach { (module, sourcePaths) ->
            sourcePaths.forEach { sourcePath ->
                if (isInvolvedInDaggerGraph(sourcePath)) {
                    addSourcePath(daggerGraphSources, module, sourcePath)
                } else {
                    addSourcePath(plainSources, module, sourcePath)
                }
            }
        }
        val sourcesCount = daggerGraphSources.size + plainSources.size
        val strategy = when {
            daggerGraphSources.isNotEmpty() -> {
                KaptCompilationStrategy()
            }
            plainSources.isNotEmpty() -> {
                PlainCompilationStrategy()
            }
            else -> error("No sources found")
        }
        Log.v("[Round] $sourcesCount source file(s) -> performing ${strategy.javaClass.simpleName}")
        return strategy
    }

    private fun isInvolvedInDaggerGraph(sourcePath: String): Boolean {
        var found = false

        File(sourcePath).forEachLine { line ->
            with (line.trim()) {
                if (startsWith("import") && (contains(DAGGER_IMPORT_PREFIX) || contains(INJECT_ANNOTATION))) {
                    found = true
                    return@forEachLine
                }
            }
        }
        return found
    }

    private fun addSourcePath(map: MutableMap<Module, MutableSet<String>>, module: Module, sourcePath: String) {
        val sourceSet = map[module]

        if (sourceSet == null) {
            map[module] = mutableSetOf(sourcePath)
        } else {
            sourceSet += sourcePath
        }
    }
}