package ru.fomenkov.plugin.bytecode

import org.apache.bcel.Const
import org.apache.bcel.classfile.ClassParser
import org.apache.bcel.generic.ClassGen
import org.apache.bcel.generic.FieldGen
import org.apache.bcel.generic.Type
import ru.fomenkov.utils.Log

object ClassFileSignatureSupplier {

    private const val FIELD_NAME = "\$GREENCAT_SIGNATURE_SYNTHETIC"
    private const val FIELD_VALUE = "^._.^"

    fun run(
        srcClassFilePath: String,
        dstClassFilePath: String,
    ): Boolean {
        try {
            val clazz = ClassParser(srcClassFilePath).parse()
            val classGen = ClassGen(clazz)
            val fieldGen = FieldGen(
                Const.ACC_PUBLIC.toInt() or Const.ACC_STATIC.toInt() or Const.ACC_FINAL.toInt(),
                Type.getType(String::class.java),
                FIELD_NAME,
                classGen.constantPool,
            )
            fieldGen.initValue = FIELD_VALUE
            classGen.addField(fieldGen.field)
            classGen.update()
            classGen.javaClass.dump(dstClassFilePath)

        } catch (error: Throwable) {
            Log.v("Failed to add signature into class file: ${error.localizedMessage}")
            Log.v(error.stackTraceToString())
            return false
        }
        return true
    }
}