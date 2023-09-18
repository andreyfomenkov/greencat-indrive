package ru.fomenkov.plugin.compiler

import ru.fomenkov.utils.noTilda

object Params {
    // Compiler and plugins
    private val KOTLIN_PLUGIN_PATH = "~/Downloads".noTilda() // TODO: download relaxed compiler
    val KOTLINC = "$KOTLIN_PLUGIN_PATH/kotlinc/bin/kotlinc"
    val PARCELIZE_PLUGIN_PATH = "$KOTLIN_PLUGIN_PATH/kotlinc/lib/parcelize-compiler.jar"
    val KAPT_PLUGIN_PATH = "$KOTLIN_PLUGIN_PATH/kotlinc/lib/kotlin-annotation-processing.jar"
    const val KAPT_CLASSES_DIR = "incrementalData"
    const val KAPT_SOURCES_DIR = "incrementalData"
    const val KAPT_INCREMENTAL_DATA_DIR = "incrementalData"
    const val KAPT_STUBS_DIR = "stubs"

    // Build directories
    private const val GREENCAT_ROOT_PATH = "greencat"
    const val BUILD_PATH_FINAL = "$GREENCAT_ROOT_PATH/build/final"
    const val BUILD_PATH_INTERMEDIATE = "$GREENCAT_ROOT_PATH/build/intermediate"
    const val DEX_FILE_PATH = "$GREENCAT_ROOT_PATH/build"
    const val DEX_PATCH_SOURCE_PATH = "$DEX_FILE_PATH/classes.dex"

    // Android SDK and tools paths
    val ANDROID_SDK_ROOT_PATH = "~/Library/Android/sdk".noTilda()
    val BUILD_TOOLS_PATH = "$ANDROID_SDK_ROOT_PATH/build-tools"
    val ADB_TOOL_PATH = "$ANDROID_SDK_ROOT_PATH/platform-tools/adb"

    // Android device DEX patch path prefix
    const val DEX_PATCH_DEST_PATH_PREFIX = "/data/local/tmp/patch-"
}