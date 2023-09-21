package ru.fomenkov.plugin

import org.junit.Before
import org.junit.Test
import ru.fomenkov.Settings
import ru.fomenkov.parser.MetadataDescriptorParser
import ru.fomenkov.utils.Log

class LibDependenciesResolverTest {

    @Before
    fun setup() {
        Settings.isVerboseMode = true
    }

    @Test
    fun `Test output for resolving Dagger dependencies`() {
        val version = "2.47" // Can be absent in cache while testing. Find another version in this case
        val startTime = System.currentTimeMillis()

        LibDependenciesResolver(
            groupId = "com.google.dagger",
            artifactId = "dagger-compiler",
            version = version,
            metadataDescriptorParser = MetadataDescriptorParser(),
        )
            .resolveLib()
            .let { path -> Log.d("JAR library path: $path\n") }

        LibDependenciesResolver(
            groupId = "com.google.dagger",
            artifactId = "dagger-spi",
            version = version,
            metadataDescriptorParser = MetadataDescriptorParser(),
        )
            .resolveLib()
            .let { path -> Log.d("JAR library path: $path\n") }

        LibDependenciesResolver(
            groupId = "com.google.dagger",
            artifactId = "dagger-compiler",
            version = version,
            metadataDescriptorParser = MetadataDescriptorParser(),
        )
            .resolveDependencies(transitive = false)
            .forEach { path -> Log.d(" - $path") }

        Log.d("")
        LibDependenciesResolver(
            groupId = "com.google.dagger",
            artifactId = "dagger-spi",
            version = version,
            metadataDescriptorParser = MetadataDescriptorParser(),
        )
            .resolveDependencies(transitive = false)
            .forEach { path -> Log.d(" - $path") }

        val endTime = System.currentTimeMillis()
        Log.d("\n# Resolver execution time: ${endTime - startTime} ms #")
    }
}