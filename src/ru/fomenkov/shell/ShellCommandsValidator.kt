package ru.fomenkov.shell

import ru.fomenkov.shell.Shell.exec

object ShellCommandsValidator {

    fun validate() {
        setOf("git", "rm", "javac").forEach { cmd ->
            val (exists) = exec("command -v $cmd")
            check(exists.isNotEmpty()) { "Command '$cmd' not found" }
        }
    }
}