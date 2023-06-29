package ru.fomenkov

import ru.fomenkov.data.Repository
import ru.fomenkov.parser.BuildGradleParser
import ru.fomenkov.parser.SettingsGradleParser
import ru.fomenkov.utils.Log
import ru.fomenkov.utils.Utils

// Setup Android project root as a working directory for Run/Debug configuration
fun main(vararg args: String) {
    val msec = Utils.timeMsec {
        launch()
    }
    Log.d("Time: $msec msec")
}

private fun launch() {
    val modules = SettingsGradleParser(Settings.SETTINGS_GRADLE_FILE_NAME).parse()
    Repository.Modules.setup(modules)

    modules.forEach { module ->
        val buildFilePath = "${module.path}/${Settings.BUILD_GRADLE_FILE}"
        BuildGradleParser(buildFilePath).parse()
    }
}