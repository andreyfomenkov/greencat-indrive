package ru.fomenkov.plugin

import ru.fomenkov.data.Artifact
import ru.fomenkov.parser.MetadataDescriptorParser
import ru.fomenkov.plugin.compiler.Params
import ru.fomenkov.shell.Shell.exec
import ru.fomenkov.utils.Log
import java.io.File

class LibDependenciesResolver(
    private val groupId: String,
    private val artifactId: String,
    private val version: String,
    private val metadataDescriptorParser: MetadataDescriptorParser,
) {

    /**
     * @return Path to JAR library in Gradle cache
     */
    fun resolveLib(): String {
        performChecks()
        Log.v("Resolving library JAR for $groupId:$artifactId:$version")

        val filesPaths = File(Params.GRADLE_CACHE_MODULES_PATH)
            .list { dir, name -> dir.isDirectory && name.startsWith("files-2.") }?.toSet() ?: emptySet()
        check(filesPaths.isNotEmpty()) { "No files-* directories found" }

        for (path in filesPaths) {
            val versionPath = "${Params.GRADLE_CACHE_MODULES_PATH}/$path/$groupId/$artifactId/$version"
            val jarPaths = exec("find $versionPath -name '$artifactId-$version.jar'").let { result ->
                if (result.successful) {
                    result.output
                } else {
                    error("Failed to list library JAR files")
                }
            }
            if (jarPaths.isNotEmpty()) {
                return jarPaths.first()
            }
        }
        error("Failed to resolve library JAR path")
    }

    /**
     * @return A set of dependencies paths to legacy/jetified JARs in Gradle cache
     */
    fun resolveDependencies(transitive: Boolean): Set<String> {
        if (transitive) {
            error("Resolving is not implemented for transitive dependencies") // TODO: need to resolve?
        }
        performChecks()
        Log.v("Resolving dependencies for $groupId:$artifactId:$version")

        val metadataDirs = listMetadataDirs()
        var descriptorPath: String? = null
        check(metadataDirs.isNotEmpty()) { "No metadata-* directories found" }

        for (path in metadataDirs) {
            descriptorPath = findArtifactDescriptorFilePath(path)

            if (descriptorPath != null) {
                Log.v("Descriptor file: $descriptorPath")
                break
            }
        }
        checkNotNull(descriptorPath) { "No descriptor file found" }
        val artifacts = metadataDescriptorParser.parse(descriptorPath)
        val jetifiedHashDirs = File(Params.GRADLE_CACHE_TRANSFORMS_PATH)
            .list { dir, _ -> dir.isDirectory }?.toSet() ?: emptySet()

        check(artifacts.isNotEmpty()) { "No artifacts parsed from metadata descriptor" }
        check(jetifiedHashDirs.isNotEmpty()) { "No hash directories found for jetified resources" }
        val paths = mutableSetOf<String>()
        val resolved = mutableSetOf<Artifact>()

        jetifiedHashDirs.forEach { path ->
            artifacts.forEach { artifact ->
                if (artifact.key !in resolved) {
                    val jarPath = "${Params.GRADLE_CACHE_TRANSFORMS_PATH}/$path/transformed/jetified-${artifact.key.artifactId}-${artifact.key.version}.jar"

                    if (File(jarPath).exists()) {
                        resolved += artifact.key
                        paths += jarPath
                    }
                }
            }
        }
        return paths
    }

    private fun findArtifactDescriptorFilePath(metadataPath: String): String? {
        val versionPath = "$metadataPath/descriptors/$groupId/$artifactId/$version"

        if (!File(versionPath).exists()) {
            Log.v("No version metadata directory: $versionPath")
            return null
        }
        val descriptorPaths = exec("find $versionPath -name 'descriptor.bin'").let { result ->
            if (result.successful) {
                result.output
            } else {
                error("Failed to list descriptor files")
            }
        }
        return descriptorPaths.firstOrNull()
    }

    private fun listMetadataDirs() = File(Params.GRADLE_CACHE_MODULES_PATH)
        .list { dir, name -> dir.isDirectory && name.startsWith("metadata-2.") }
        ?.toSet()
        ?.map { dirName -> "${Params.GRADLE_CACHE_MODULES_PATH}/$dirName" } ?: emptySet()

    private fun performChecks() {
        check(groupId.isNotBlank()) { "No group ID provided" }
        check(artifactId.isNotBlank()) { "No artifact ID provided" }
        check(version.isNotBlank()) { "No version provided" }

        listOf(
            Params.ROOT_GRADLE_CACHE_PATH,
            Params.GRADLE_CACHE_MODULES_PATH,
            Params.GRADLE_CACHE_TRANSFORMS_PATH,
        )
            .forEach { path ->
                check(File(path).exists()) { "Path doesn't exist: $path" }
            }
    }
}