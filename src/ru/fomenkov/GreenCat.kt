package ru.fomenkov

import ru.fomenkov.analytics.Analytics
import ru.fomenkov.data.*
import ru.fomenkov.parser.*
import ru.fomenkov.plugin.compiler.Params
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.utils.*
import java.io.File
import kotlin.math.max

private const val CONFIG_JSON_PATH = "config.json"
private const val GRADLE_DAEMON_TIMEOUT = "10800000"
private const val GRADLE_MODULE_NAME = "incremental-build"
private const val KEEP_GRADLE_DAEMON_ALIVE = true
private const val GIT_DIFF_SCOPE_CHECK_ENABLED = true
private const val AGENT_ZIP_NAME = "agent.tar.gz"
private const val PACKAGE_NAME = "sinet.startup.inDriver"
private const val COMPONENT_NAME = "sinet.startup.inDriver.ui.splash.SplashActivity"
private const val MIN_ANDROID_API_LEVEL = 26 // Android 8.0

fun main(vararg args: String) {
    try {
        GreenCat().launch(*args)
    } catch (e: Throwable) {
        val message = e.localizedMessage
        val title = "* * * EXECUTION ERROR * * *"
        val line = "-".repeat(max(title.length, message.length))
        val header = " ".repeat(max(0, message.length / 2 - title.length / 2)) + title
        Analytics.failed(message)

        Log.i("\n$line")
        Log.i(header)
        Log.i("$line\n")
        Log.i(message)
        Log.i("\n$line")
    }
}

internal class GreenCat {

    fun launch(vararg args: String) {
        Analytics.launch()
        Log.level = Log.Level.INFO
        Log.i("GreenCat v${Settings.GREENCAT_VERSION}")
        Log.i("GitHub: https://github.com/andreyfomenkov/greencat-indrive")

        val config = selectInputConfiguration(*args)
        if (config == null) {
            cleanup()
            return
        }
        val deviceId = selectAndroidDevice()
        val timeStart = System.currentTimeMillis()

        if (GIT_DIFF_SCOPE_CHECK_ENABLED) {
            checkGitDiffRequirements(config)
        }
        checkAppOnDevice(PACKAGE_NAME, deviceId)

        val osVersion = getAndroidOSVersion(deviceId)
        val apiLevel = getAndroidApiLevel(deviceId)

        if (apiLevel < MIN_ANDROID_API_LEVEL) {
            error("The ART Tooling Interface is supported in Android 8.0 and higher. Your device '$deviceId' with Android $osVersion is too old for this shit")
        }
        Log.i("Running configuration '${config.name}' on device '$deviceId' (Android $osVersion, API $apiLevel)\n")

        val needCleanAndRebuild = build(config)
        Log.i("Deploying patch files...")
        deploy(config, deviceId)

        val timeEnd = System.currentTimeMillis()
        val totalTimeSec = (timeEnd - timeStart) / 1000L
        Analytics.complete(totalTimeSec, needCleanAndRebuild)

        Log.i("")
        val formattedTime = Utils.formatSecToMinSec(totalTimeSec)
        Utils.printTextInFrame("Build & deploy complete in $formattedTime")
    }

    private fun cleanup() {
        val deviceId = selectAndroidDevice()

        StringBuilder("cat << EOF | ${Params.ADB_TOOL_PATH} -s $deviceId shell\n")
            .append("run-as sinet.startup.inDriver\n")
            .append("rm -rf code_cache\n")
            .append("EOF\n")
            .toString()
            .let { cmd ->
                exec(cmd).let { result ->
                    if (!result.successful) {
                        error("Failed to do cleanup for $PACKAGE_NAME")
                    }
                }
            }
        restartApp(deviceId = deviceId, packageName = PACKAGE_NAME, componentName = COMPONENT_NAME)
        Analytics.cleanup()
        Log.i("\nCleanup complete for $PACKAGE_NAME")
    }

    private fun getAndroidOSVersion(deviceId: String): String {
        exec("${Params.ADB_TOOL_PATH} -s $deviceId shell getprop ro.build.version.release").let { result ->
            if (result.successful) {
                return result.output.first()
            } else {
                error("Failed to get Android OS version on device")
            }
        }
    }

    private fun getAndroidApiLevel(deviceId: String): Int {
        exec("${Params.ADB_TOOL_PATH} -s $deviceId shell getprop ro.build.version.sdk").let { result ->
            if (result.successful) {
                return result.output.first().toInt()
            } else {
                error("Failed to get Android API level on device")
            }
        }
    }

    /**
     * @return Selected configuration or null in case of cleanup patches on Android device
     */
    private fun selectInputConfiguration(vararg args: String): Configuration? {
        val configs = JsonConfigParser(CONFIG_JSON_PATH)
            .parse()
            .associateBy(Configuration::name)

        check(configs.isNotEmpty()) { "JSON config file is empty" }
        var firstArg = args.getOrNull(0)

        if (firstArg == null) {
            Log.i("\nSelect configuration or type empty value to clean patch on Android device:")
            val entries = configs.entries.toList()

            entries.forEachIndexed { index, entry ->
                Log.i(" $index -> ${entry.key}")
            }
            Log.i("\nEnter: ", newLine = false)
            val line = readln()

            if (line.isBlank()) {
                return null
            }
            val index = checkNotNull(line.toIntOrNull()) { "Invalid input value" }
            firstArg = checkNotNull(entries.getOrNull(index)?.key) { "Input value out of bounds [0..${entries.size - 1}]" }

        } else if (firstArg == "-c") {
            return null
        }
        return checkNotNull(configs[firstArg]) { "No configuration found: $firstArg" }
    }

    private fun selectAndroidDevice(): String {
        check(File(Params.ADB_TOOL_PATH).exists()) { "ADB not found at: ${Params.ADB_TOOL_PATH}" }
        exec("export LANG=en_US; ${Params.ADB_TOOL_PATH} devices").let { result ->
            if (result.successful) {
                val deviceIds = AdbDevicesParser(result.output).parse()
                check(deviceIds.isNotEmpty()) { "No online Android devices connected" }

                return if (deviceIds.size == 1) {
                    deviceIds.first()
                } else {
                    Log.i("\nSelect Android device:")

                    deviceIds.forEachIndexed { index, deviceId ->
                        Log.i(" $index -> $deviceId")
                    }
                    Log.i("\nEnter: ", newLine = false)
                    val index = checkNotNull(readln().toIntOrNull()) { "Invalid input value" }
                    return checkNotNull(deviceIds.getOrNull(index)) { "Input value out of bounds [0..${deviceIds.size - 1}]" }
                }
            } else {
                error("Failed to list Android devices via adb")
            }
        }
    }

    private fun checkAppOnDevice(packageName: String, deviceId: String) {
        exec("${Params.ADB_TOOL_PATH} -s $deviceId shell pm list packages | grep $packageName").let { result ->
            if (!result.successful || result.output.isEmpty()) {
                check(result.output.isNotEmpty()) { "Application $packageName is not installed on device '$deviceId'" }
            }
        }
    }

    private fun checkGitDiffRequirements(config: Configuration) {
        val (branch, paths) = getProjectGitDiffFiles()
        val filePaths = paths
            .filterNot { path -> path.startsWith(GRADLE_MODULE_NAME) }
        val modulePaths = config.modules
            .map { path ->
                path.split(':')
                    .filterNot(String::isBlank)
                    .joinToString(separator = "/")
            }
        val unscopedPaths = filePaths.filterNot { filePath -> isFileInScope(filePath, modulePaths) }

        if (unscopedPaths.isNotEmpty()) {
            Log.i("\nWarning! You can add, modify or delete files only inside modules specified by '${config.name}' configuration:")
            config.modules.forEach { moduleName -> Log.i(" - $moduleName") }

            Log.i("\n The following file(s) on branch '$branch' are out of selected configuration scope:")
            unscopedPaths.forEach { filePath -> Log.i(" - $filePath") }

            error("Some file(s) are out of configuration scope. See the detailed message above")
        }
    }

    private fun isFileInScope(filePath: String, modulePaths: List<String>): Boolean {
        modulePaths.forEach { modulePath ->
            if (filePath.startsWith(modulePath)) {
                return true
            }
        }
        return false
    }

    private fun build(config: Configuration): Boolean {
        val modulesParam = config.modules.joinToString(separator = ",")
        val cmd = StringBuilder("./gradlew ")
            .append("-Dorg.gradle.daemon=$KEEP_GRADLE_DAEMON_ALIVE ")
            .append("-Dorg.gradle.daemon.idletimeout=$GRADLE_DAEMON_TIMEOUT ")
            .append("--console=plain ")
            .append("-Pmodules=$modulesParam ")
            .append(":$GRADLE_MODULE_NAME:assembleDebug")
            .toString()

        var needCleanAndRebuild = false
        var result = exec(cmd, print = true) { _, line ->
            if (line.contains("Unexpected combination of states")) {
                needCleanAndRebuild = true
            }
        }
        if (needCleanAndRebuild) {
            Log.i("\n----------------------------------------------------------")
            Log.i("          [!] Need to make clean and rebuild [!]          ")
            Log.i("----------------------------------------------------------\n")
            result = exec("pkill java; ./gradlew clean; $cmd", print = true)
        }
        if (!result.successful) {
            error("Build failed")
        }
        return needCleanAndRebuild
    }

    private fun deploy(config: Configuration, deviceId: String) {
        // Find all classes.dex for the included modules
        val dexList = config.modules
            .map { moduleName ->
                val path = findClassesDex(moduleName)
                checkNotNull(path) { "No classes.dex found for module $moduleName" }
            }
        // Push all classes.dex to /data/local/tmp
        dexList.forEachIndexed { index, path ->
            exec("${Params.ADB_TOOL_PATH} -s $deviceId push $path /data/local/tmp/classes$index.dex").let { result ->
                if (!result.successful) {
                    error("Failed to push $path on device")
                }
            }
        }
        // Push zipped agent to /data/local/tmp
        exec("${Params.ADB_TOOL_PATH} -s $deviceId push $AGENT_ZIP_NAME /data/local/tmp").let { result ->
            if (result.successful) {
                Log.d("Push agent complete")
            } else {
                error("Failed to push agent on device")
            }
        }
        // Deploy agent with classes.dex in application private storage
        StringBuilder("cat << EOF | ${Params.ADB_TOOL_PATH} -s $deviceId shell\n")
            .append("run-as sinet.startup.inDriver\n")
            .append("rm -rf code_cache\n")
            .append("tar zxf /data/local/tmp/cc.tar.gz -C .\n")
            .apply {
                dexList.indices.forEach { index ->
                    append("cp /data/local/tmp/classes$index.dex code_cache/.overlay/base.apk\n")
                }
            }
            .append("EOF\n")
            .toString()
            .let { cmd ->
                exec(cmd).let { result ->
                    result.output.forEach(::println)

                    if (result.successful) {
                        Log.d("Deploy agent complete")
                    } else {
                        error("Failed to deploy agent on device")
                    }
                }
            }
        // Do cleanup in /data/local/tmp
        exec("${Params.ADB_TOOL_PATH} -s $deviceId shell rm /data/local/tmp/$AGENT_ZIP_NAME").let { result ->
            if (!result.successful) {
                error("Failed to remove agent from device")
            }
        }
        dexList.indices.forEach { index ->
            exec("${Params.ADB_TOOL_PATH} -s $deviceId shell rm /data/local/tmp/classes$index.dex").let { result ->
                if (!result.successful) {
                    error("Failed to remove classes.dex from device")
                }
            }
        }
        restartApp(deviceId = deviceId, packageName = PACKAGE_NAME, componentName = COMPONENT_NAME)
    }

    private fun findClassesDex(moduleName: String): String? {
        val transformDir = moduleName.split(':')
            .filterNot(String::isBlank)
            .joinToString(separator = "/")
            .let { path -> File("$path/build/.transforms") }

        if (!transformDir.exists()) {
            return null
        }
        transformDir.list()?.forEach { hash ->
            val dexFile = File("${transformDir.path}/$hash/transformed/classes/classes_dex/classes.dex")

            if (dexFile.exists()) {
                return dexFile.absolutePath
            }
        }
        return null
    }

    private fun getProjectGitDiffFiles(): Pair<String, Set<String>> {
        val (gitDiffOutput) = exec("export LANG=en_US; git status")
        val dirtySourcesResult = GitStatusParser(gitDiffOutput).parse()
        return dirtySourcesResult.branch to dirtySourcesResult.files
    }

    private fun restartApp(deviceId: String, packageName: String, componentName: String) {
        exec("${Params.ADB_TOOL_PATH} -s $deviceId shell am force-stop $packageName").let { result ->
            if (result.successful) {
                Log.d("Force stop application: $packageName")
            } else {
                error("Failed to stop process")
            }
        }
        exec("${Params.ADB_TOOL_PATH} -s $deviceId shell am start -n $packageName/$componentName -a android.intent.action.MAIN -c android.intent.category.LAUNCHER")
            .let { result ->
                if (result.successful) {
                    Log.d("Starting $componentName")
                } else {
                    error("Failed to start Activity: $componentName")
                }
            }
    }
}