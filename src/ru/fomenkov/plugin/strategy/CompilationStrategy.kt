package ru.fomenkov.plugin.strategy

import ru.fomenkov.data.Repository
import ru.fomenkov.data.Round
import ru.fomenkov.plugin.compiler.Params

interface CompilationStrategy {

    sealed class Result {

        data object OK : Result()

        data class Failed(val output: List<String>) : Result()
    }

    fun perform(round: Round): Result

    fun getProjectClasspath() =
        setOf(Params.BUILD_PATH_INTERMEDIATE, Params.BUILD_PATH_FINAL) + Repository.Classpath.forProject
}