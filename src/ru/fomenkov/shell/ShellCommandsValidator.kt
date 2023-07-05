package ru.fomenkov.shell

import ru.fomenkov.shell.Shell.exec

object ShellCommandsValidator {

    fun validate() {
        setOf("git", "rm").forEach { cmd ->
            val exists = exec("command -v $cmd").isNotEmpty()
            check(exists) { "Command '$cmd' not found" }
        }
    }
}