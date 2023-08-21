package ru.fomenkov

import java.io.File

object Settings {
    var isVerboseMode = false // Used for testing
    var displayModuleDependencies = false

    val currentDir: String = File("").absolutePath
    const val SETTINGS_GRADLE_FILE_NAME = "settings.gradle"
    const val BUILD_GRADLE_FILE = "build.gradle"
    const val BUILD_GRADLE_KTS_FILE = "build.gradle.kts"
}