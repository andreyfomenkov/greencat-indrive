package ru.fomenkov.shell

import ru.fomenkov.utils.Log

object Shell {

    data class Result(val output: List<String>, val successful: Boolean)

    private const val SHELL = "/bin/zsh"
    private const val RESULT_PREFIX = "%RESULT="

    @Synchronized
    fun exec(cmd: String, print: Boolean = false): Result {
        val output = execWithoutResult("$cmd; echo $RESULT_PREFIX$?", print)

        return if (output == null) {
            Result(output = emptyList(), successful = false)
        } else {
            val successful = output.firstOrNull { line ->
                line.startsWith(RESULT_PREFIX)
            }?.trim()?.endsWith("0") == true

            Result(
                output = output.filterNot { line -> line.startsWith(RESULT_PREFIX) },
                successful = successful,
            )
        }
    }

    private fun execWithoutResult(cmd: String, print: Boolean = false): List<String>? {
        try {
            val output = mutableListOf<String>()
            Runtime.getRuntime().exec(arrayOf(SHELL, "-c", cmd)).apply {
                val inputReader = inputStream.bufferedReader()
                val errorReader = errorStream.bufferedReader()

                when (print) {
                    true -> {
                        var line: String?
                        do {
                            line = errorReader.readLine() ?: break
                            output += line
                            Log.d(line)
                        } while (line != null)
                        do {
                            line = inputReader.readLine() ?: break
                            output += line
                            Log.d(line)
                        } while (line != null)
                    }
                    else -> {
                        output += inputReader.readLines() + errorReader.readLines()
                    }
                }
            }
            return output

        } catch (error: Throwable) {
            Log.d("Failed to execute shell command: $cmd\nError: ${error.message}")
            return null
        }
    }
}