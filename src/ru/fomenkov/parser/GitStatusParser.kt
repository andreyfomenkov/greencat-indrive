package ru.fomenkov.parser

class GitStatusParser(private val input: List<String>) {

    fun parse(): Set<String> {
        val paths = mutableSetOf<String>()
        var inUntrackedSection = false

        input
            .filter(String::isNotBlank)
            .map(String::trim)
            .forEach { line ->
                when {
                    line.startsWith("modified: ") -> {
                        val index = line.indexOf("modified:")
                        check(index != -1) { "Failed to parse line: $line" }
                        paths += line.substring(index + 9, line.length).trim()
                    }
                    line.startsWith("renamed: ") -> {
                        val index = line.indexOf("->")
                        check(index != -1) { "Failed to parse line: $line" }
                        paths += line.substring(index + 3, line.length).trim()
                    }
                    inUntrackedSection && !line.startsWith('(') -> {
                        paths += line.trim()
                    }
                }
                if (line.startsWith("Untracked files:")) {
                    inUntrackedSection = true
                }
            }
        return paths.toSet()
    }
}