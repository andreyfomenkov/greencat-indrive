package ru.fomenkov.plugin.compiler

interface Compiler {

    fun run(): Boolean

    fun output(): List<String>
}