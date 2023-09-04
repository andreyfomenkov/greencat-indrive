package ru.fomenkov.plugin

import ru.fomenkov.data.Module
import ru.fomenkov.data.Repository
import ru.fomenkov.data.Round
import ru.fomenkov.utils.Log

class CompilationRoundsBuilder(private val sources: Set<String>) {

    fun build(): List<Round> {
        val rounds = mutableListOf<Round>()
        val allModules = mutableSetOf<Module>()
        val sourcesMap = mutableMapOf<Module, MutableSet<String>>()

        sources.forEach { sourcePath ->
            val index = sourcePath.indexOf("/src/")
            check(index != -1) { "Failed to parse source path" }

            val modulePath = sourcePath.substring(0, index)
            val module = Module(
                name = modulePath.replace('/', ':'),
                path = modulePath,
            )
            val children = Repository.Graph.getChildModules(module)

            checkNotNull(children) { "No module in graph: $module" }
            allModules += module

            if (!sourcesMap.containsKey(module)) {
                sourcesMap[module] = mutableSetOf()
            }
            val moduleSourcePaths = checkNotNull(sourcesMap[module]) { "No mapping for module '${module.name}'" }
            moduleSourcePaths += sourcePath
        }
        while (allModules.isNotEmpty()) {
            val iterator = allModules.iterator()
            val nextRoundModules = mutableSetOf<Module>()

            while (iterator.hasNext()) {
                val module = iterator.next()

                if (module.hasNoDependenciesIn(allModules)) {
                    nextRoundModules += module
                }
            }
            check(nextRoundModules.isNotEmpty()) { "No modules for the next round" }
            allModules -= nextRoundModules
            rounds += Round(
                items = nextRoundModules.associateWith { module ->
                    checkNotNull(sourcesMap[module]) { "No mapping for module '${module.name}'" }
                }
            )
        }
        return rounds
    }

    private fun Module.hasNoDependenciesIn(modules: Set<Module>): Boolean {
        modules.forEach { module ->
            if (Repository.Graph.isDependency(root = this, child = module)) {
                return false
            }
        }
        return true
    }
}