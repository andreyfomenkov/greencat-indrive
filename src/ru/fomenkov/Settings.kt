package ru.fomenkov

object Settings {
    // User for testing
    var displayModuleDependencies = false
    var displayResolvingChildModules = false
    var displayKotlinCompilerModuleNames = false
    var useIncrementalDiff = true
    var usePlainCompilationStrategyOnly = false

    const val GREENCAT_VERSION = "3.6"
    const val SETTINGS_GRADLE_FILE_NAME = "settings.gradle"
    const val BUILD_GRADLE_FILE = "build.gradle"
    const val BUILD_GRADLE_KTS_FILE = "build.gradle.kts"
    const val JAVA_OPTS_XMX = "2g"
    const val JVM_TARGET = "17"
}