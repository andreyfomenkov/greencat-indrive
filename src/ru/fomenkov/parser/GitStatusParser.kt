package ru.fomenkov.parser

class GitStatusParser(private val input: List<String>) {

    data class Output(val branch: String, val files: Set<String>)

    fun parse(): Output {
        var branch = ""
        val paths = mutableSetOf<String>()
        var inUntrackedSection = false

        input
            .map(String::trim)
            .forEach { line ->
                when {
                    branch.isBlank() && line.startsWith("on branch", ignoreCase = true) -> {
                        branch = line.substring(9, line.length).trim()
                    }
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
                        if (line.isBlank() || line.contains('\'')) {
                            check(branch.isNotBlank()) { "No branch name parsed" }
                            return Output(branch, paths)
                        } else {
                            paths += line.trim()
                        }
                    }
                }
                if (line.startsWith("Untracked files:")) {
                    inUntrackedSection = true
                }
            }
        check(branch.isNotBlank()) { "No branch name parsed" }
        return Output(branch, paths)
    }
}