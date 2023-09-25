package ru.fomenkov.plugin.bytecode

import org.junit.Before
import org.junit.Test
import ru.fomenkov.Settings
import ru.fomenkov.utils.Log

class ClassFileInjectionSnapshotPlayground {

    @Before
    fun setup() {
        Settings.isVerboseMode = true
    }

    @Test
    fun `Test class file injection snapshot`() {
        val path = "/Users/andreyfomenkov/Workspace/indriver/greencat/build/final/sinet/startup/inDriver/intercity/passenger/order_form/ui/SampleInjectionClass.class"
//        val path = "/Users/andreyfomenkov/Workspace/indriver/intercity/passenger/order-form/build/tmp/kotlin-classes/debug/sinet/startup/inDriver/intercity/passenger/order_form/ui/OrderFormFragment.class"
        val startTime = System.currentTimeMillis()
        val hashValue = ClassFileInjectionSnapshotMaker.make(path)
        val endTime = System.currentTimeMillis()

        Log.d("Snapshot hash value: $hashValue")
        Log.d("# Execution time: ${endTime - startTime} ms #")
    }
}