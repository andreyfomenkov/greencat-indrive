package plugin

import ru.fomenkov.plugin.Round
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.utils.Log
import java.io.File

class KotlinCompiler(
    private val rounds: List<Round>,
    private val compilerPath: String,
    private val classpath: String,
    private val outputDir: String,
) {

    // Plugin consumes project's classpath from IDE environment variable -> we can
    // combine sources from different modules during the same compilation round
    fun run() {
        check(rounds.isNotEmpty()) { "No compilation rounds provided" }
        check(File(compilerPath).exists()) { "No compiler found: $compilerPath" }
        check(classpath.isNotBlank()) { "Classpath is not provided" }

        rounds.forEachIndexed { index, round ->
            val sources = mutableSetOf<String>()
            round.items.values.forEach(sources::addAll)
            check(sources.isNotEmpty()) { "No sources for compilation round $index" }

            // TODO: friend modules and module name argument
            val sourcesArg = sources.joinToString(separator = " ")
            val lines = exec("$compilerPath -Xjvm-default=all-compatibility -d $outputDir $sourcesArg")

            lines.forEach { line ->
                Log.d(line)
            }
        }
    }
}