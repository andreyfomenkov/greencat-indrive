package ru.fomenkov

import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import ru.fomenkov.data.Repository
import ru.fomenkov.parser.*
import ru.fomenkov.utils.*
import java.io.File

/**
 * [1] Setup Android project root as a working directory for Run/Debug configuration
 * [2] Modify files in project to get not empty `git status` command output (optional)
 * [3] Add PACKAGE_NAME and COMPONENT_NAME for Run/Debug configuration
 */
@Ignore("Playground test")
class AndroidProjectIntegrationPlayground {

    private val classpathFile = "~/Workspace/classpath.txt".noTilda()

    @Before
    fun setup() {
        with(Settings) {
            displayModuleDependencies = false
            displayResolvingChildModules = false
            displayKotlinCompilerModuleNames = false
            useIncrementalDiff = false
            usePlainCompilationStrategyOnly = false
        }
        Log.level = Log.Level.INFO
        Repository.Modules.clear()
        Repository.Graph.clear()
        Repository.CompilerModuleNameParam.clear()
        Repository.LibraryVersions.clear()
        Repository.Classpath.clear()
        Repository.Classpath.set(loadClasspathFromFile())
    }

    @Test
    fun `Test plugin workflow`() {
        val executor = WorkerTaskExecutor()
        val packageName = System.getenv()["PACKAGE_NAME"] ?: ""
        val componentName = System.getenv()["COMPONENT_NAME"] ?: ""
        val plugin = GreenCat(executor)
        try {
            plugin.launch(
                classpath = Repository.Classpath.get(),
                packageName = packageName,
                componentName = componentName,
            )
        } finally {
            plugin.release()
        }
    }

    private fun loadClasspathFromFile(): Set<String> {
        return File(classpathFile)
            .readText()
            .split(':')
            .filterNot { path -> path.endsWith(".xml") || path.endsWith("/res") }
            .filter { path -> File(path).exists() }
            .toSet()
    }
}