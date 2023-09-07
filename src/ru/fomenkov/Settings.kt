package ru.fomenkov

import java.io.File

object Settings {
    // User for testing
    var isVerboseMode = false
    var displayModuleDependencies = false
    var displayResolvingChildModules = false
    var displayKotlinCompilerModuleNames = false
    var useIncrementalDiff = true

    val currentDir: String = File("").absolutePath
    const val SETTINGS_GRADLE_FILE_NAME = "settings.gradle"
    const val BUILD_GRADLE_FILE = "build.gradle"
    const val BUILD_GRADLE_KTS_FILE = "build.gradle.kts"
}