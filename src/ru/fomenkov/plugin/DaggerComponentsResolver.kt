package ru.fomenkov.plugin

import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.utils.WorkerTaskExecutor
import java.io.File
import java.util.concurrent.Callable

class DaggerComponentsResolver(private val modulePath: String) {

    fun resolve(): Set<String> {
        val ktClasses = exec("find $modulePath -name '*.kt'").let { result ->
            if (result.successful) {
                result.output
            } else {
                error("Failed to list *.kt classes in $modulePath")
            }
        }
        val tasks = ktClasses.map { path ->
            Callable {
                val lines = File(path).readLines()
                var result = ""

                for (line in lines) {
                    if (line.trim().startsWith("import ") && line.contains("dagger.Component")) {
                        result = path
                        break
                    }
                }
                result
            }
        }
        val executor = WorkerTaskExecutor()
        val output = try {
            executor.run(tasks)
        } catch (e: Throwable) {
            error("Execution failed: ${e.message}")
        } finally {
            executor.release()
        }
        return output.filter(String::isNotBlank).toSet()
    }
}