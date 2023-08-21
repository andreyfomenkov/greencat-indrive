package ru.fomenkov.shell

class CommandBuilder(command: String) {

    private val builder = StringBuilder(command)

    init {
        check(command.isNotBlank()) { "No command provided" }
    }

    fun param(vararg params: String): CommandBuilder {
        params
            .filterNot(String::isBlank)
            .forEach { param -> builder.append(' ').append(param.trim()) }
        return this
    }

    fun build() = builder.toString()

    override fun toString() = builder.toString()
}