package ru.fomenkov.plugin

import org.junit.Test
import ru.fomenkov.plugin.dagger.DaggerComponentsResolver
import ru.fomenkov.utils.Log

class DaggerComponentsResolverPlayground {

    @Test
    fun `Test find all Dagger Component classes`() {
        val modulePath = System.getenv()["MODULE_PATH"] ?: ""
        check(modulePath.isNotBlank()) { "No module path provided. Add MODULE_PATH env variable to run configuration" }
        Log.d("Searching @Component classes in $modulePath")
        val startTime = System.currentTimeMillis()

        DaggerComponentsResolver(modulePath)
            .resolve()
            .forEach { path ->
                Log.d(" - $path")
            }
        val endTime = System.currentTimeMillis()
        Log.d("# Execution time: ${endTime - startTime} ms #")
    }
}