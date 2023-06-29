package ru.fomenkov

import java.io.File

object Settings {
    // Used for integration test
    var isVerboseMode = false
    var displayModuleDependencies = false

    val currentDir = File("").absolutePath
    const val SETTINGS_GRADLE_FILE_NAME = "settings.gradle"
    const val BUILD_GRADLE_FILE = "build.gradle"
}