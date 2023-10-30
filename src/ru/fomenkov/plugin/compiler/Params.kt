package ru.fomenkov.plugin.compiler

import ru.fomenkov.utils.noTilda

object Params {
    const val LIBRARY_VERSIONS_FILE_PATH = "gradle/libs.versions.toml"

    // Gradle caches
    val ROOT_GRADLE_CACHE_PATH = "~/.gradle/caches".noTilda()
    val GRADLE_CACHE_MODULES_PATH = "$ROOT_GRADLE_CACHE_PATH/modules-2"
    val GRADLE_CACHE_TRANSFORMS_PATH = "$ROOT_GRADLE_CACHE_PATH/transforms-3"

    // Compiler and plugins
    private val KOTLIN_PLUGIN_PATH = "/Applications/Android Studio.app/Contents/plugins/Kotlin".noTilda() // TODO: download relaxed compiler
    val KOTLINC = "$KOTLIN_PLUGIN_PATH/kotlinc/bin/kotlinc"
    val PARCELIZE_PLUGIN_PATH = "$KOTLIN_PLUGIN_PATH/kotlinc/lib/parcelize-compiler.jar"
    val KAPT_PLUGIN_PATH = "$KOTLIN_PLUGIN_PATH/kotlinc/lib/kotlin-annotation-processing.jar"
    const val KAPT_CLASSES_DIR = "incrementalData"
    const val KAPT_SOURCES_DIR = "incrementalData"
    const val KAPT_INCREMENTAL_DATA_DIR = "incrementalData"
    const val KAPT_STUBS_DIR = "stubs"

    // Build directories and data files
    const val GREENCAT_ROOT_PATH = "greencat"
    const val BUILD_PATH_FINAL = "$GREENCAT_ROOT_PATH/build/final"
    const val BUILD_PATH_INTERMEDIATE = "$GREENCAT_ROOT_PATH/build/intermediate"
    const val BUILD_DIRECTORY = "$GREENCAT_ROOT_PATH/build"
    const val DEX_PATCH_SOURCE_PATH = "$BUILD_DIRECTORY/classes.dex"
    const val BUILD_LOG_FILE_PATH = "$BUILD_DIRECTORY/build.log"
    const val METADATA_DIFF_FILE_NAME = "metadata.diff"

    // Android SDK and tools paths
    val ANDROID_SDK_ROOT_PATH = "~/Library/Android/sdk".noTilda()
    val BUILD_TOOLS_PATH = "$ANDROID_SDK_ROOT_PATH/build-tools"
    val ADB_TOOL_PATH = "$ANDROID_SDK_ROOT_PATH/platform-tools/adb"

    // Android device DEX patch path prefix
    const val DEX_PATCH_DEST_PATH_PREFIX = "/data/local/tmp/patch-"
}