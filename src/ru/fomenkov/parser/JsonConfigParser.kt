package ru.fomenkov.parser

import org.json.JSONArray
import org.json.JSONObject
import ru.fomenkov.data.Configuration
import java.io.File

class JsonConfigParser(private val path: String) {

    fun parse(): List<Configuration> {
        val root = JSONArray(File(path).readText())
        val names = mutableSetOf<String>()
        val modules = mutableMapOf<String, Set<String>>()
        val includes = mutableMapOf<String, Set<String>>()

        for (i in 0 until root.length()) {
            val config = root.get(i) as JSONObject
            val name = config.getString("name")

            names += name
            modules += name to getStringItems(config, "modules")
            includes += name to getStringItems(config, "include")
        }
        modules.forEach { (name, modules) ->
            modules.forEach { module ->
                check(',' !in module) { "Module name contains comma: '$module' (configuration = $name)" }
            }
        }
        return names.map { name ->
            Configuration(
                name = name,
                modules = modules.getOrDefault(name, emptySet()) +
                        includes
                            .getOrDefault(name, emptySet())
                            .flatMap { modules.getOrDefault(it, emptySet()) }
            )
        }
    }

    private fun getStringItems(config: JSONObject, name: String): Set<String> {
        val output = mutableSetOf<String>()

        if (config.has(name)) {
            val array = config.getJSONArray(name)

            for (i in 0 until array.length()) {
                output += array.getString(i)
            }
        }
        return output
    }
}