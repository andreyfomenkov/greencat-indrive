package ru.fomenkov.data

import ru.fomenkov.Settings
import ru.fomenkov.utils.Log
import ru.fomenkov.utils.Utils
import ru.fomenkov.utils.noTilda
import java.io.File

object Repository {

    object LibraryVersions {
        private val versionsMap = mutableMapOf<String, String>() // Library name -> version

        fun setup(versions: Map<String, String>) {
            check(versionsMap.isEmpty()) { "Library versions already initialized" }

            Log.v("Added ${versions.size} library version(s)")
            versionsMap += versions
        }

        fun get(libraryName: String) = versionsMap[libraryName]

        fun clear() {
            versionsMap.clear()
        }
    }

    object Modules {
        private val modulesMap = mutableMapOf<String, Module>() // Module name -> module

        fun setup(modules: Collection<Module>) {
            check(modulesMap.isEmpty()) { "Modules already initialized" }

            Log.v("Added ${modules.size} module(s)")
            modulesMap += modules.associateBy { module -> module.name }
        }

        fun get() = modulesMap.values.toSet()

        fun byName(name: String) = modulesMap[name]

        fun clear() {
            modulesMap.clear()
        }
    }

    object Graph {
        private val graph = mutableMapOf<Module, Set<Module>>() // Root module -> child modules with transitive resolution

        fun setup(deps: Map<Module, Set<Dependency>>) {
            check(graph.isEmpty()) { "Dependency graph already initialized" }
            val modulesMap = mutableMapOf<String, Set<Dependency.Module>>()

            deps.forEach { entry ->
                val root = entry.key
                val children = entry.value
                    .filterIsInstance(Dependency.Module::class.java)
                    .toSet()
                modulesMap += root.name to children
            }
            modulesMap.keys.forEach { name ->
                val root = Utils.toModule(name)
                val children = resolveChildModules(modulesMap, name)
                graph += root to children
            }
        }

        private fun resolveChildModules(modulesMap: Map<String, Set<Dependency.Module>>, name: String): Set<Module> {
            val children = checkNotNull(modulesMap[name]) { "No module for name: $name" }.toMutableSet()
            Log.v(Settings.displayResolvingChildModules, "Resolving modules for '$name'")
            Log.v(Settings.displayResolvingChildModules, " - children: $children")

            while (true) {
                val iterator = children.iterator()
                val resolvedModules = mutableSetOf<Dependency.Module>()

                while (iterator.hasNext()) {
                    val module = iterator.next()

                    if (module.isTransitive) {
                        iterator.remove()
                        resolvedModules += module.copy(isTransitive = false)
                        resolvedModules += checkNotNull(modulesMap[module.name]) { "No module for name: ${module.name}" }
                    }
                }
                if (resolvedModules.isEmpty()) {
                    break
                } else {
                    Log.v(Settings.displayResolvingChildModules, " + transitive: $resolvedModules")
                    children += resolvedModules
                    resolvedModules.clear()
                }
            }
            Log.v(Settings.displayResolvingChildModules, " > output: $children\n")
            return children.map { module -> Utils.toModule(module.name) }.toSet()
        }

        fun getChildModules(root: Module) = graph[root]

        fun isDependency(root: Module, child: Module): Boolean {
            val children = checkNotNull(graph[root]) { "No module for name: ${root.name}" }
            return child in children
        }

        fun clear() {
            graph.clear()
        }
    }

    // TODO: get classpath from IDE via main function arguments
    object Classpath {

        val forProject = File("~/Workspace/classpath.txt".noTilda())
            .readText()
            .split(':')
            .filterNot { path -> path.endsWith(".xml") ||  path.endsWith("/res") }
            .filter { path -> File(path).exists() }
            .toSet()
    }

    /**
     * Used by Kotlin compiler for `-module-name` param
     */
    object CompilerModuleNameParam {
        private val moduleNameParam = mutableMapOf<Module, String>()

        fun set(module: Module, name: String) {
            moduleNameParam[module] = name
        }

        fun get(module: Module) = moduleNameParam[module]

        fun clear() {
            moduleNameParam.clear()
        }
    }
}