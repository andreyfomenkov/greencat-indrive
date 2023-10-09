package ru.fomenkov.utils

import ru.fomenkov.shell.Shell.exec
import java.io.File

private val homeDir = checkNotNull(exec("echo ~").output.firstOrNull()) { "Failed to get home directory" }
val distinctId = homeDir.split("/")[2]

fun String.noTilda() = replace("~", homeDir)

fun String.escapeSpaces() = replace(" ", "\\ ")

fun String.pathExists() = File(replace("\\", "")).exists()