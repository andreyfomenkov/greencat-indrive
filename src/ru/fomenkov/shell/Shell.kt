package ru.fomenkov.shell

import ru.fomenkov.utils.Log

object Shell {

    private const val SHELL = "/bin/zsh"

    fun exec(cmd: String, print: Boolean = false): List<String> {
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
                            Log.e(line)
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
            Log.e("Failed to execute shell command: $cmd\nError: ${error.message}")
            return emptyList()
        }
    }
}