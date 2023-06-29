package ru.fomenkov.data

sealed class Dependency {

    data class Module(val name: String, val isTransitive: Boolean) : Dependency() {

        override fun toString(): String {
            return if (isTransitive) {
                "[M] $name -> TRANSITIVE"
            } else {
                "[M] $name"
            }
        }
    }
}