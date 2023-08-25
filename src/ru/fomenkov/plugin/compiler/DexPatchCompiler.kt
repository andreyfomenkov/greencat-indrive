package ru.fomenkov.plugin.compiler

import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.utils.Log
import java.io.File

object DexPatchCompiler {
    private val output = mutableListOf<String>()

    fun run(classFilePaths: Set<String>): Boolean {
        output.clear()

        Params.ANDROID_SDK_ROOT_PATH.let { path ->
            if (!File(path).exists()) {
                output += "Path doesn't exist: $path"
                return false
            }
        }
        Params.BUILD_TOOLS_PATH.let { path ->
            if (!File(path).exists()) {
                output += "Path doesn't exist: $path"
                return false
            }
        }
        val versions = File(Params.BUILD_TOOLS_PATH)
            .list { file, _ -> file.isDirectory } ?: emptyArray<String?>()
        versions.sort()

        if (versions.isEmpty()) {
            output += "No build tools installed at ${Params.BUILD_TOOLS_PATH}"
            return false
        } else {
            Log.v("Build tools versions installed: ${versions.joinToString(separator = ", ")} -> using v${versions.last()}")
        }
        val d8Path = "${Params.BUILD_TOOLS_PATH}/${versions.last()}/d8"

        if (!File(d8Path).exists()) {
            output += "D8 tool not found: $d8Path"
            return false
        }
        val classFilesParam = classFilePaths.joinToString(separator = " ") { path ->
            path.replace("$", "\\$")
        }
        val result = exec("$d8Path $classFilesParam --output ${Params.DEX_FILE_PATH}")
        output += result.output
        return result.successful
    }

    fun output(): List<String> = output
}