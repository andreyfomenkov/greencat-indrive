package ru.fomenkov.plugin.strategy

import ru.fomenkov.data.Round

interface CompilationStrategy {

    fun perform(round: Round)
}