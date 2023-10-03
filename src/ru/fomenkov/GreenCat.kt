package ru.fomenkov

import ru.fomenkov.plugin.ArgumentsParser

// Setup Android project root as a working directory for Run/Debug configuration
fun main(vararg args: String) {
    ArgumentsParser(args.toList()).parse().let { result ->
    }
}

private fun launch() {
}