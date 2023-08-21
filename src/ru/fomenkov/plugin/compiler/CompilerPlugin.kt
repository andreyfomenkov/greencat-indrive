package ru.fomenkov.plugin.compiler

/**
 * Command line plugin option format:
 * -P plugin:<plugin id>:<key>=<value>
 */
data class CompilerPlugin(
    val path: String,
    val id: String = "",
    val options: Map<String, String> = emptyMap(),
)