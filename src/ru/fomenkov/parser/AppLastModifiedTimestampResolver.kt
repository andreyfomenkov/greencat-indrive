package ru.fomenkov.parser

import ru.fomenkov.plugin.compiler.Params
import ru.fomenkov.shell.Shell.exec

object AppLastModifiedTimestampResolver {

    fun resolve(packageName: String): String {
        val line = exec("${Params.ADB_TOOL_PATH} shell dumpsys package $packageName | grep -i '/base.apk'").let { result ->
            check(result.successful) { "Failed to parse dumpsys output" }

            val line = result.output.firstOrNull { line -> line.trim().endsWith("/base.apk") }
            checkNotNull(line) { "Failed to get /base.apk path" }
        }
        val index = line.indexOf("/data/")
        check(index != -1) { "Failed to parse /base.apk path" }

        val path = line.substring(index).trim()
        return exec("${Params.ADB_TOOL_PATH} shell date -r $path +%s").let { result ->
            check(result.successful) { "Failed to parse date output" }

            val timestamp = checkNotNull(result.output.firstOrNull()) { "No timestamp resolved" }
            checkNotNull(timestamp.toLongOrNull()) { "Failed to parse app timestamp" }
            timestamp
        }
    }
}