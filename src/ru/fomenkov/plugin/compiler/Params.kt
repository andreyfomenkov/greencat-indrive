package ru.fomenkov.plugin.compiler

object Params {
    // Compiler params
    const val COMPILE_JVM_TARGET = "1.8"

    // Compiler and plugins
    private const val KOTLIN_PLUGIN_PATH = "/Applications/Android Studio.app/Contents/plugins/Kotlin"
    const val KOTLINC = "$KOTLIN_PLUGIN_PATH/kotlinc/bin/kotlinc"
    const val PARCELIZE_PLUGIN_PATH = "$KOTLIN_PLUGIN_PATH/kotlinc/lib/parcelize-compiler.jar"

    // Build directories
    private const val GREENCAT_ROOT_PATH = "greencat"
    const val BUILD_PATH_FINAL = "$GREENCAT_ROOT_PATH/build/final"
    const val BUILD_PATH_INTERMEDIATE = "$GREENCAT_ROOT_PATH/build/intermediate"
}