package ru.fomenkov

import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.plugin.compiler.Params
import ru.fomenkov.prefs.Prefs
import ru.fomenkov.utils.Log
import java.util.concurrent.TimeUnit

fun main(vararg args: String) {
    Updater.run(dstDir = Params.GREENCAT_ROOT_PATH)
}

object Updater {

    // TODO: replace with greencat-indrive repository
    private const val PLUGIN_VERSION_INFO_URL = "https://raw.githubusercontent.com/andreyfomenkov/green-cat/master/artifacts/version-info"
    private const val COMPILER_VERSION_INFO_URL = "https://raw.githubusercontent.com/andreyfomenkov/kotlin-relaxed/relaxed-restrictions/artifact/date"
    private const val PLUGIN_FILE_NAME = "greencat.jar"
    private const val COMPILER_DIR = "kotlinc"
    private const val COMPILER_FILE_NAME = "$COMPILER_DIR.zip"
    private const val PREFS_FILE_NAME = "versions"
    private const val KEY_PLUGIN_VERSION = "plugin_version"
    private const val KEY_COMPILER_VERSION = "compiler_version"
    private const val KEY_LAST_UPDATE_TIMESTAMP = "last_update_timestamp"
    private val CHECK_INTERVAL_MSEC = TimeUnit.HOURS.toMillis(2) // Check for updates every 2 hours

    fun run(dstDir: String) {
        if (!needToCheckForUpdates(dstDir)) {
            return
        }
        Log.i("Checking for updates...")
        val (pluginVersion, pluginUrl) = checkVersionInfo(PLUGIN_VERSION_INFO_URL) ?: return
        val (compilerVersion, compilerUrl) = checkVersionInfo(COMPILER_VERSION_INFO_URL) ?: return
        val needToUpdatePlugin = needToUpdatePlugin(dstDir, pluginVersion)
        val needToUpdateCompiler = needToUpdateCompiler(dstDir, compilerVersion)

        if (needToUpdatePlugin || needToUpdateCompiler) {
            Log.i("Downloading...")
        } else {
            Log.i("Everything is up to date")
        }
        if (needToUpdatePlugin) {
            if (!downloadArtifact(dstDir, pluginUrl, PLUGIN_FILE_NAME)) {
                return
            }
            markPluginUpdated(dstDir, pluginVersion)
            Log.i("Plugin updated to version $pluginVersion")
        }
        if (needToUpdateCompiler) {
            if (!downloadArtifact(dstDir, compilerUrl, COMPILER_FILE_NAME)) {
                return
            }
            if (!unzipCompiler(dstDir)) {
                return
            }
            markCompilerUpdated(dstDir, compilerVersion)
            Log.i("Compiler updated to version $compilerVersion")
        }
        markUpdatesChecked(dstDir)
    }

    private fun needToCheckForUpdates(dstDir: String): Boolean {
        val prefs = Prefs("$dstDir/$PREFS_FILE_NAME")
        val timeNow = System.currentTimeMillis()
        val timeLastUpdate = prefs.get(KEY_LAST_UPDATE_TIMESTAMP).toLongOrNull() ?: 0L

        if (timeNow < timeLastUpdate) {
            prefs.put(KEY_LAST_UPDATE_TIMESTAMP, System.currentTimeMillis().toString())
            return true
        }
        return timeNow - timeLastUpdate > CHECK_INTERVAL_MSEC
    }

    private fun needToUpdatePlugin(dstDir: String, currentPluginVersion: String): Boolean {
        val lastPluginVersion = Prefs("$dstDir/$PREFS_FILE_NAME").get(KEY_PLUGIN_VERSION)
        return lastPluginVersion != currentPluginVersion
    }

    private fun needToUpdateCompiler(dstDir: String, currentCompilerVersion: String): Boolean {
        val lastCompilerVersion = Prefs("$dstDir/$PREFS_FILE_NAME").get(KEY_COMPILER_VERSION)
        return lastCompilerVersion != currentCompilerVersion
    }

    private fun markPluginUpdated(dstDir: String, version: String) {
        Prefs("$dstDir/$PREFS_FILE_NAME").put(KEY_PLUGIN_VERSION, version)
    }

    private fun markCompilerUpdated(dstDir: String, version: String) {
        Prefs("$dstDir/$PREFS_FILE_NAME").put(KEY_COMPILER_VERSION, version)
    }

    private fun markUpdatesChecked(dstDir: String) {
        Prefs("$dstDir/$PREFS_FILE_NAME").put(KEY_LAST_UPDATE_TIMESTAMP, System.currentTimeMillis().toString())
    }

    private fun checkVersionInfo(url: String) =
        exec("curl -s $url").let { result ->
            if (result.successful) {
                if (result.output.size != 2) {
                    Log.i("Unexpected format for $url. Output size: ${result.output.size}")
                    null
                } else {
                    val version = result.output[0]
                    val artifactUrl = result.output[1]
                    version to artifactUrl
                }
            } else {
                Log.i("Failed to check version info for $url:")
                result.output.forEach(Log::i)
                null
            }
        }

    private fun downloadArtifact(dstDir: String, url: String, fileName: String) =
        exec("curl -s $url > $dstDir/$fileName").let { result ->
            if (!result.successful) {
                Log.i("Failed to download artifact $url:")
                result.output.forEach(Log::i)
            }
            result.successful
        }

    private fun unzipCompiler(dstDir: String, ): Boolean {
        exec("rm -rf $dstDir/$COMPILER_DIR")
        return exec("unzip $dstDir/$COMPILER_FILE_NAME -d $dstDir").let { result ->
            exec("rm $dstDir/$COMPILER_FILE_NAME")

            if (!result.successful) {
                Log.i("Failed to unzip compiler:")
                result.output.forEach(Log::i)
            }
            result.successful
        }
    }
}
