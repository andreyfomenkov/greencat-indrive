package ru.fomenkov.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.fomenkov.Settings
import ru.fomenkov.utils.Log
import ru.fomenkov.utils.Utils

class RepositoryTest {

    @Before
    fun setup() {
        Log.level = Log.Level.VERBOSE
        Repository.Modules.clear()
        Repository.Graph.clear()
    }

    @Test
    fun `Test module path and name`() {
        assertEquals(
            Module(name = "app", path = "app"),
            Utils.toModule("app"),
        )
        assertEquals(
            Module(name = "m1:m2", path = "m1/m2"),
            Utils.toModule("m1:m2"),
        )
        assertEquals(
            Module(name = "m3:m4:m5", path = "m3/m4/m5"),
            Utils.toModule("m3:m4:m5"),
        )
    }

    @Test
    fun `Test modules storage`() {
        val inputModules = toModules("root", "m1", "m2:m3", "m4:m5:m6")
        Repository.Modules.setup(inputModules)

        val outputModules = Repository.Modules.get()

        assertEquals(4, outputModules.size)
        assertEquals(inputModules, outputModules)
        assertEquals(toModule("root"), Repository.Modules.byName("root"))
        assertEquals(toModule("m1"), Repository.Modules.byName("m1"))
        assertEquals(toModule("m2:m3"), Repository.Modules.byName("m2:m3"))
        assertEquals(toModule("m4:m5:m6"), Repository.Modules.byName("m4:m5:m6"))
        assertNull(Repository.Modules.byName("abc"))
        assertNull(Repository.Modules.byName("123"))
    }

    @Test
    fun `Test dependencies between modules`() {
        // app: m1 (transitive), m2, m3
        // m1: m4, m5
        // m2: m3
        // m3: -
        // m4: -
        // m5: -
        val graph = mapOf(
            toModule("app") to setOf(
                Dependency.Module(name = "m1", isTransitive = true),
                Dependency.Module(name = "m2", isTransitive = false),
                Dependency.Module(name = "m3", isTransitive = false),
            ),
            toModule("m1") to setOf(
                Dependency.Module(name = "m4", isTransitive = false),
                Dependency.Module(name = "m5", isTransitive = false),
            ),
            toModule("m2") to setOf(
                Dependency.Module(name = "m3", isTransitive = false),
            ),
            toModule("m3") to emptySet(),
            toModule("m4") to emptySet(),
            toModule("m5") to emptySet(),
        )

        Repository.Graph.setup(graph)

        assertDependencies("app", setOf("m1", "m2", "m3", "m4", "m5"))
        assertDependencies("m1", setOf("m4", "m5"))
        assertDependencies("m2", setOf("m3"))
        assertNotDependencies("m1", setOf("app", "m2", "m3"))
        assertNotDependencies("m2", setOf("app", "m1", "m4", "m5"))
        assertNotDependencies("m3", setOf("app", "m1", "m2", "m3", "m4", "m5"))
        assertNotDependencies("m4", setOf("app", "m1", "m2", "m3", "m4", "m5"))
        assertNotDependencies("m5", setOf("app", "m1", "m2", "m3", "m4", "m5"))
    }

    @Test
    fun `Test modules graph empty dependencies`() {
        val graph = mapOf(
            toModule("app") to emptySet<Dependency>(),
            toModule("m1:m2") to emptySet(),
            toModule("m3:m4:m5") to emptySet(),
        )

        Repository.Graph.setup(graph)

        assertEquals(
            emptySet<Module>(),
            Repository.Graph.getChildModules(toModule("app")),
        )
        assertEquals(
            emptySet<Module>(),
            Repository.Graph.getChildModules(toModule("m1:m2")),
        )
        assertEquals(
            emptySet<Module>(),
            Repository.Graph.getChildModules(toModule("m3:m4:m5")),
        )
        assertNull(
            Repository.Graph.getChildModules(toModule("abc")),
        )
        assertNull(
            Repository.Graph.getChildModules(toModule("123")),
        )
    }

    @Test
    fun `Test modules graph with non-transitive dependencies only`() {
        // app: m1, m2, m3, m4
        // m1: m3, m4
        // m2: m5, m6
        // m3: m7
        // m4: m8
        // m5: m7, m9
        // m6: -
        // m7: -
        // m8: m9
        // m9: -
        val graph = mapOf(
            toModule("app") to setOf(
                Dependency.Module(name = "m1", isTransitive = false),
                Dependency.Module(name = "m2", isTransitive = false),
                Dependency.Module(name = "m3", isTransitive = false),
                Dependency.Module(name = "m4", isTransitive = false),
            ),
            toModule("m1") to setOf(
                Dependency.Module(name = "m3", isTransitive = false),
                Dependency.Module(name = "m4", isTransitive = false),
            ),
            toModule("m2") to setOf(
                Dependency.Module(name = "m5", isTransitive = false),
                Dependency.Module(name = "m6", isTransitive = false),
            ),
            toModule("m3") to setOf(
                Dependency.Module(name = "m7", isTransitive = false),
            ),
            toModule("m4") to setOf(
                Dependency.Module(name = "m8", isTransitive = false),
            ),
            toModule("m5") to setOf(
                Dependency.Module(name = "m7", isTransitive = false),
                Dependency.Module(name = "m9", isTransitive = false),
            ),
            toModule("m6") to emptySet(),
            toModule("m7") to emptySet(),
            toModule("m8") to setOf(
                Dependency.Module(name = "m9", isTransitive = false),
            ),
            toModule("m9") to emptySet(),
        )

        Repository.Graph.setup(graph)

        assertChildModules(root = "app", "m1", "m2", "m3", "m4")
        assertChildModules(root = "m1", "m3", "m4")
        assertChildModules(root = "m2", "m5", "m6")
        assertChildModules(root = "m3", "m7")
        assertChildModules(root = "m4", "m8")
        assertChildModules(root = "m5", "m7", "m9")
        assertChildModules(root = "m6")
        assertChildModules(root = "m7")
        assertChildModules(root = "m8", "m9")
        assertChildModules(root = "m9")
    }

    @Test
    fun `Test modules graph with transitive dependencies`() {
        // app: m1, m2 (transitive), m3, m4
        // m1: m3, m4
        // m2: m5 (transitive), m6
        // m3: m7
        // m4: m8 (transitive)
        // m5: m7, m9
        // m6: -
        // m7: -
        // m8: m9
        // m9: -
        val graph = mapOf(
            toModule("app") to setOf(
                Dependency.Module(name = "m1", isTransitive = false),
                Dependency.Module(name = "m2", isTransitive = true), // [T]
                Dependency.Module(name = "m3", isTransitive = false),
                Dependency.Module(name = "m4", isTransitive = false),
            ),
            toModule("m1") to setOf(
                Dependency.Module(name = "m3", isTransitive = false),
                Dependency.Module(name = "m4", isTransitive = false),
            ),
            toModule("m2") to setOf(
                Dependency.Module(name = "m5", isTransitive = true), // [T]
                Dependency.Module(name = "m6", isTransitive = false),
            ),
            toModule("m3") to setOf(
                Dependency.Module(name = "m7", isTransitive = false),
            ),
            toModule("m4") to setOf(
                Dependency.Module(name = "m8", isTransitive = true), // [T]
            ),
            toModule("m5") to setOf(
                Dependency.Module(name = "m7", isTransitive = false),
                Dependency.Module(name = "m9", isTransitive = false),
            ),
            toModule("m6") to emptySet(),
            toModule("m7") to emptySet(),
            toModule("m8") to setOf(
                Dependency.Module(name = "m9", isTransitive = false),
            ),
            toModule("m9") to emptySet(),
        )

        Repository.Graph.setup(graph)

        assertChildModules(root = "app", "m1", "m2", "m3", "m4", "m5", "m6", "m7", "m9") // + m5, m6, m7, m9 resolved
        assertChildModules(root = "m1", "m3", "m4")
        assertChildModules(root = "m2", "m5", "m6", "m7", "m9") // + m7, m9 resolved
        assertChildModules(root = "m3", "m7")
        assertChildModules(root = "m4", "m8", "m9") // + m9 resolved
        assertChildModules(root = "m5", "m7", "m9")
        assertChildModules(root = "m6")
        assertChildModules(root = "m7")
        assertChildModules(root = "m8", "m9")
        assertChildModules(root = "m9")
    }

    private fun toModule(name: String) = Utils.toModule(name)

    private fun toModules(vararg names: String) = names.map { name -> Utils.toModule(name) }.toSet()

    private fun assertChildModules(root: String, vararg children: String) {
        assertEquals(
            "Unexpected child modules for module $root",
            children.toSet(),
            Repository.Graph.getChildModules(toModule(root))?.map { module -> module.name }?.toSet(),
        )
    }

    private fun assertDependencies(root: String, children: Set<String>) {
        children.forEach { child ->
            assertTrue(
                "Module '$root' has no '$child' module dependency",
                Repository.Graph.isDependency(root = toModule(root), child = toModule(child)),
            )
        }
    }

    private fun assertNotDependencies(root: String, children: Set<String>) {
        children.forEach { child ->
            assertFalse(
                "Module '$root' has '$child' module dependency",
                Repository.Graph.isDependency(root = toModule(root), child = toModule(child)),
            )
        }
    }
}