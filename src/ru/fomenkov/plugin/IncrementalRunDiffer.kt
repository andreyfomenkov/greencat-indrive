package ru.fomenkov.plugin

import ru.fomenkov.plugin.compiler.Params
import ru.fomenkov.utils.Log
import ru.fomenkov.utils.WorkerTaskExecutor
import java.io.File
import java.util.concurrent.Callable

class IncrementalRunDiffer(
    private val executor: WorkerTaskExecutor,
    private val intermediateBuildPath: String,
    private val finalBuildPath: String,
) {
    data class Result(
        val compileSourcePaths: Set<String>,
        val removeSourcePaths: Set<String>,
    )

    /**
     * Get all dirty source files and return file subsets to compile
     * and remove based on the previous incremental run
     */
    fun run(gitDiffPaths: Set<String>): Result {
        require(gitDiffPaths.isNotEmpty()) { "No files to process" }
        check(File(intermediateBuildPath).exists()) { "Path doesn't exist: $intermediateBuildPath" }
        check(File(finalBuildPath).exists()) { "Path doesn't exist: $finalBuildPath" }

        val prevHashTable = readHashTable()
        val calcStartTime = System.currentTimeMillis()
        val currHashTable = computeHashes(gitDiffPaths)
        val calcEndTime = System.currentTimeMillis()

        Log.v("# Hash tables comparison (calc time = ${calcEndTime - calcStartTime} ms) #")

        (prevHashTable.keys + currHashTable.keys).forEach { path ->
            val prevHashValue = prevHashTable[path]
            val currHashValue = currHashTable[path]
            val mark = when {
                currHashValue != null && currHashValue != prevHashValue -> "[COMPILE]"
                currHashValue == null && prevHashValue != null -> "[REMOVE]"
                else -> ""
            }
            Log.v("[HASH] $path: BEFORE(${prevHashValue ?: "---"}) -> AFTER(${currHashValue ?: "---"}) $mark")
        }
        Log.v("")
        writeHashTable(currHashTable)
        val compileSourcePaths = mutableSetOf<String>()
        val removeSourcePaths = mutableSetOf<String>()

        (prevHashTable.keys + currHashTable.keys).forEach { path ->
            val prevHashValue = prevHashTable[path]
            val currHashValue = currHashTable[path]

            if (currHashValue != null && currHashValue != prevHashValue) {
                compileSourcePaths += path
            } else if (currHashValue == null && prevHashValue != null) {
                removeSourcePaths += path
            }
        }
        return Result(
            compileSourcePaths = compileSourcePaths,
            removeSourcePaths = removeSourcePaths,
        )
    }

    private fun computeHashes(paths: Set<String>) = paths.map { path ->
        Callable {
            val file = File(path)
            check(file.exists()) { "Source file doesn't exist: $path" }
            path to file.readText().hashCode()
        }
    }
        .let(executor::run)
        .associate { (path, hash) -> path to hash }

    /**
     * Create current diff hashtable
     * Format: `<path>#<hash>`
     */
    private fun writeHashTable(hashTable: Map<String, Int>) {
        val text = hashTable.entries.joinToString(separator = "\n") { (path, hash) -> "$path#$hash" }
        File("$finalBuildPath/${Params.METADATA_DIFF_FILE_NAME}").writeText(text)
    }

    private fun readHashTable(): Map<String, Int> {
        val file = File("$finalBuildPath/${Params.METADATA_DIFF_FILE_NAME}")

        if (!file.exists()) {
            return emptyMap()
        }
        return file.readLines().associate { line ->
            val parts = line.split('#')
            check(parts.size == 2) { "Failed to parse line from hashtable file: $line" }
            val path = parts[0]
            val hash = parts[1].toInt()
            path to hash
        }
    }
}