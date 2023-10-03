package ru.fomenkov.plugin

import ru.fomenkov.Settings
import ru.fomenkov.utils.Log

class ArgumentsParser(private val args: List<String>) {

    fun parse(): Result? {
        val allArgs = Arg.entries.map(Arg::value).toSet()
        var prevArg = ""

        var classpath = ""
        var packageName = ""
        var componentName = ""
        var focusedFilePath = ""
        var verboseLogging = false

        args.forEach { arg ->
            when (arg) {
                Arg.VERBOSE.value -> verboseLogging = true
                in allArgs -> prevArg = arg
                else -> when (prevArg) {
                    Arg.CLASSPATH.value -> classpath = arg
                    Arg.PACKAGE.value -> packageName = arg
                    Arg.COMPONENT.value -> componentName = arg
                    Arg.FOCUS.value -> focusedFilePath = arg
                }
            }
        }
        if (classpath.isBlank() || packageName.isBlank() || componentName.isBlank() || focusedFilePath.isBlank()) {
            Log.i("Some arguments missing")
            printHelp()
            return null
        }
        return Result(
            classpath = classpath,
            packageName = packageName,
            componentName = componentName,
            focusedFilePath = focusedFilePath,
            verboseLogging = verboseLogging,
        )
    }

    private fun printHelp() {
        Log.i("GreenCat v${Settings.GREENCAT_VERSION}\n")
        Log.i("Available input arguments:")
        Log.i(" ${Arg.CLASSPATH.value}   project's classpath")
        Log.i(" ${Arg.PACKAGE.value}     application package name")
        Log.i(" ${Arg.COMPONENT.value}   application launcher Activity")
        Log.i(" ${Arg.FOCUS.value}       focused file path in Android Studio")
        Log.i(" ${Arg.VERBOSE.value}     verbose logging (optional)\n")
    }

    data class Result(
        val classpath: String, // $Classpath variable from Android Studio (project's classpath)
        val packageName: String, // Application package name
        val componentName: String, // Application launcher Activity
        val focusedFilePath: String, // $FilePath variable from Android Studio (current focused file)
        val verboseLogging: Boolean,
    )

    private enum class Arg(val value: String) {
        CLASSPATH("-classpath"),
        PACKAGE("-package"),
        COMPONENT("-component"),
        FOCUS("-focus"),
        VERBOSE("-verbose"),
    }
}