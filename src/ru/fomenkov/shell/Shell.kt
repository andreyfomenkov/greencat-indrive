package ru.fomenkov.shell

import ru.fomenkov.utils.Log
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

object Shell {

    data class Result(
        val output: List<String> = emptyList(),
        val successful: Boolean,
    )

    private const val SHELL = "/bin/zsh"
    private const val RESULT_PREFIX = "%RESULT="

    fun exec(cmd: String, print: Boolean = false, onUpdate: (Process, String) -> Unit = { _, _ -> }): Result {
        val executor = Executors.newFixedThreadPool(2)

        return try {
            val process = Runtime.getRuntime().exec(arrayOf(SHELL, "-c", "$cmd; echo $RESULT_PREFIX\$?"))
            val inputReader = process.inputStream.bufferedReader()
            val errorReader = process.errorStream.bufferedReader()
            val inputResult = handleReaderStream(executor, inputReader, print) { line -> onUpdate(process, line) }.get()
            val errorResult = handleReaderStream(executor, errorReader, print) { line -> onUpdate(process, line) }.get()
            val output = inputResult.output + errorResult.output
            val successful = inputResult.successful && errorResult.successful && isResultZero(output)
            Result(
                output.filterNot { line -> line.startsWith(RESULT_PREFIX) },
                successful,
            )

        } catch (e: Throwable) {
            Log.i("Execution failed: ${e.message}")
            Result(successful = false)

        } finally {
            if (!executor.isShutdown) {
                executor.shutdownNow()
            }
        }
    }

    private fun isResultZero(output: List<String>): Boolean {
        return output.firstOrNull { line ->
            line.startsWith(RESULT_PREFIX)
        }?.trim()?.endsWith("0") == true
    }

    private fun handleReaderStream(
        executor: ExecutorService,
        reader: BufferedReader,
        print: Boolean,
        onUpdate: (String) -> Unit,
    ): Future<Result> {
        val task = Callable {
            val output = mutableListOf<String>()
            var successful = true

            while (true) {
                try {
                    val line = reader.readLine() ?: break
                    onUpdate(line)
                    output += line

                    if (print && !line.trim().startsWith(RESULT_PREFIX)) {
                        Log.i(line)
                    }
                } catch (e: IOException) {
                    successful = false
                    break
                }
            }
            Result(output, successful)
        }
        return executor.submit(task)
    }
}