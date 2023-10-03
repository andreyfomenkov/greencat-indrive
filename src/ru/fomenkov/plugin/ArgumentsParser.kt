package ru.fomenkov.plugin

import ru.fomenkov.Settings

class ArgumentsParser(private val args: List<String>) {

    fun parse(): Result? {
        val allArgs = Arg.entries.map(Arg::value).toSet()
        var prevArg = ""

        var classpath = ""
        var packageName = ""
        var componentName = ""
        var focusedFilePath = ""

        args.forEach { arg ->
            if (arg in allArgs) {
                prevArg = arg
            } else {
                when (prevArg) {
                    Arg.CLASSPATH.value -> classpath = arg
                    Arg.PACKAGE.value -> packageName = arg
                    Arg.COMPONENT.value -> componentName = arg
                    Arg.FOCUS.value -> focusedFilePath = arg
                }
            }
        }
        if (classpath.isBlank() || packageName.isBlank() || componentName.isBlank() || focusedFilePath.isBlank()) {
            println("Some arguments missing")
            printHelp()
            return null
        }
        return Result(
            classpath = classpath,
            packageName = packageName,
            componentName = componentName,
            focusedFilePath = focusedFilePath,
        )
    }

    private fun printHelp() {
        println("GreenCat v${Settings.GREENCAT_VERSION}\n")
        println("Available input arguments:")
        println(" ${Arg.CLASSPATH.value}   project's classpath")
        println(" ${Arg.PACKAGE.value}     application package name")
        println(" ${Arg.COMPONENT.value}   application launcher Activity")
        println(" ${Arg.FOCUS.value}       focused file path in Android Studio\n")
    }

    data class Result(
        val classpath: String, // $Classpath variable from Android Studio (project's classpath)
        val packageName: String, // Application package name
        val componentName: String, // Application launcher Activity
        val focusedFilePath: String, // $FilePath variable from Android Studio (current focused file)
    )

    private enum class Arg(val value: String) {
        CLASSPATH("-classpath"),
        PACKAGE("-package"),
        COMPONENT("-component"),
        FOCUS("-focus"),
    }
}