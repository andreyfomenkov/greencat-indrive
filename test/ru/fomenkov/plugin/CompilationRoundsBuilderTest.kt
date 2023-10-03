package ru.fomenkov.plugin

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.fomenkov.Settings
import ru.fomenkov.data.Dependency
import ru.fomenkov.data.Repository
import ru.fomenkov.data.Round
import ru.fomenkov.utils.Log
import ru.fomenkov.utils.Utils

class CompilationRoundsBuilderTest {

    @Before
    fun setup() {
        Log.level = Log.Level.VERBOSE
        Repository.Graph.clear()
    }

    @Test
    fun `Test compilation rounds for no dependencies graph`() {
        // mA: -
        // mB:sub1: -
        // mC:sub2:sub3: -
        val deps = mapOf(
            module("mA") to emptySet<Dependency>(),
            module("mB:sub1") to emptySet(),
            module("mC:sub2:sub3") to emptySet(),
        )
        val sources = setOf(
            "mA/src/main/java/ru/fomenkov/ClassA.kt",
            "mA/src/test/java/ru/fomenkov/TestClassA.kt",

            "mB/sub1/src/main/java/ru/fomenkov/ClassB.kt",
            "mB/sub1/src/test/java/ru/fomenkov/TestClassB.kt",

            "mC/sub2/sub3/src/main/java/ru/fomenkov/ClassC.kt",
            "mC/sub2/sub3/src/test/java/ru/fomenkov/TestClassC.kt",
        )
        Repository.Graph.setup(deps)

        val rounds = CompilationRoundsBuilder(sources).build()

        assertEquals(1, rounds.size)
        assertEquals(
            Round(
                items = mapOf(
                    module("mA") to setOf(
                        "mA/src/main/java/ru/fomenkov/ClassA.kt",
                        "mA/src/test/java/ru/fomenkov/TestClassA.kt",
                    ),
                    module("mB:sub1") to setOf(
                        "mB/sub1/src/main/java/ru/fomenkov/ClassB.kt",
                        "mB/sub1/src/test/java/ru/fomenkov/TestClassB.kt",
                    ),
                    module("mC:sub2:sub3") to setOf(
                        "mC/sub2/sub3/src/main/java/ru/fomenkov/ClassC.kt",
                        "mC/sub2/sub3/src/test/java/ru/fomenkov/TestClassC.kt",
                    ),
                )
            ),
            rounds.first(),
        )
    }

    @Test
    fun `Test compilation rounds for vertical dependencies graph`() {
        // mA: mB
        // mB: mC
        // mC: -
        val deps = mapOf(
            module("mA") to setOf(Dependency.Module("mB", isTransitive = false)),
            module("mB") to setOf(Dependency.Module("mC", isTransitive = false)),
            module("mC") to emptySet(),
        )
        val sources = setOf(
            "mA/src/main/java/ru/fomenkov/ClassA.kt",
            "mB/src/main/java/ru/fomenkov/ClassB.kt",
            "mC/src/main/java/ru/fomenkov/ClassC.kt",
        )
        Repository.Graph.setup(deps)

        val rounds = CompilationRoundsBuilder(sources).build()

        assertEquals(3, rounds.size)
        assertEquals(
            Round(items = mapOf(module("mC") to setOf("mC/src/main/java/ru/fomenkov/ClassC.kt"))),
            rounds[0],
        )
        assertEquals(
            Round(items = mapOf(module("mB") to setOf("mB/src/main/java/ru/fomenkov/ClassB.kt"))),
            rounds[1],
        )
        assertEquals(
            Round(items = mapOf(module("mA") to setOf("mA/src/main/java/ru/fomenkov/ClassA.kt"))),
            rounds[2],
        )
    }

    @Test
    fun `Test compilation rounds for mixed dependencies graph`() {
        // mA: mB (transitive), mC
        // mB: mD, mE
        // mC: -
        // mD: -
        // mE: -
        val deps = mapOf(
            module("mA") to setOf(
                Dependency.Module("mB", isTransitive = true),
                Dependency.Module("mC", isTransitive = false),
            ),
            module("mB") to setOf(
                Dependency.Module("mD", isTransitive = false),
                Dependency.Module("mE", isTransitive = false),
            ),
            module("mC") to emptySet(),
            module("mD") to emptySet(),
            module("mE") to emptySet(),
        )
        val sources = setOf(
            "mA/src/main/java/ru/fomenkov/ClassA.kt",
            "mB/src/main/java/ru/fomenkov/ClassB.kt",
            "mC/src/main/java/ru/fomenkov/ClassC.kt",
            "mD/src/main/java/ru/fomenkov/ClassD.kt",
            "mE/src/main/java/ru/fomenkov/ClassE.kt",
        )
        Repository.Graph.setup(deps)

        val rounds = CompilationRoundsBuilder(sources).build()

        assertEquals(3, rounds.size)
        assertEquals(
            Round(
                items = mapOf(
                    module("mC") to setOf("mC/src/main/java/ru/fomenkov/ClassC.kt"),
                    module("mD") to setOf("mD/src/main/java/ru/fomenkov/ClassD.kt"),
                    module("mE") to setOf("mE/src/main/java/ru/fomenkov/ClassE.kt"),
                )
            ),
            rounds[0],
        )
        assertEquals(
            Round(items = mapOf(module("mB") to setOf("mB/src/main/java/ru/fomenkov/ClassB.kt"))),
            rounds[1],
        )
        assertEquals(
            Round(items = mapOf(module("mA") to setOf("mA/src/main/java/ru/fomenkov/ClassA.kt"))),
            rounds[2],
        )
    }

    private fun module(name: String) = Utils.toModule(name)
}