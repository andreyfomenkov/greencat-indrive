package ru.fomenkov.plugin.bytecode

import org.apache.bcel.classfile.AnnotationEntry
import org.apache.bcel.classfile.ClassParser
import org.apache.bcel.classfile.Field
import org.apache.bcel.classfile.FieldOrMethod
import org.apache.bcel.classfile.Method
import ru.fomenkov.utils.Log

/**
 * Create snapshot for:
 * - @Inject constructor params with type
 * - @Inject lateinit fields with type
 *
 * Used to compare two versions of the same compiled *.class file and determine whether
 * to run Dagger annotation processing or proceed with a plan compilation strategy
 *
 * Notes:
 * - @Inject constructor is a method with <init> name
 * - @Inject lateinit field is a setter + getter with no @Inject annotation + field with @Inject annotation
 */
object ClassFileInjectionSnapshotMaker {

    private const val INJECT_ANNOTATION_TYPE = "Ljavax/inject/Inject;"
    private const val ASSISTED_INJECT_ANNOTATION_TYPE = "Ldagger/assisted/AssistedInject;"

    /**
     * @return Snapshot hash value
     */
    fun make(path: String): Int {
        val clazz = ClassParser(path).parse()
        val methods = clazz.methods.filter(::hasInjectionAnnotation)
        val fields = clazz.fields.filter(::hasInjectionAnnotation)
        val snapshot = (methods + fields)
            .map { entity ->
                when (entity) {
                    is Method -> getMethodSnapshot(entity)
                    is Field -> getFieldSnapshot(entity)
                    else -> error("Unknown entity type: ${entity.javaClass.canonicalName}")
                }
            }
            .sorted()
            .joinToString(separator = "\n")
            .also { value -> Log.v("Snapshot for $path:\n$value\n") }
        return snapshot.hashCode()
    }

    private fun getMethodSnapshot(method: Method) = "${method.name}, ${method.signature}"

    private fun getFieldSnapshot(field: Field) = "${field.name}, ${field.signature}"

    private fun hasInjectionAnnotation(entity: FieldOrMethod): Boolean {
        val types = entity.annotationEntries.map(AnnotationEntry::getAnnotationType)
        return types.contains(INJECT_ANNOTATION_TYPE) || types.contains(ASSISTED_INJECT_ANNOTATION_TYPE)
    }
}