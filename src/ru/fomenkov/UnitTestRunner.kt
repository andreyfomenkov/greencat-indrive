import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import java.io.File

//object Main {
//
//    fun prepareClasspath() {
//        val classLoader = ClassLoader.getSystemClassLoader()
//        val method = classLoader.javaClass.getDeclaredMethod("appendToClassPathForInstrumentation", String::class.java)
//
//        method.isAccessible = true
//
//        CLASSPATH.split(":").forEach { path ->
//            method.invoke(classLoader, path)
//        }
//    }
//
//    fun run() {
//        val clazz = Class.forName("ru.fomenkov.graph.viewmodel.StartViewModelTest")
//        val junit = JUnitCore()
//        junit.addListener(object : RunListener() {
//
//            override fun testFinished(description: Description) {
//                println("[ ] ${description.className}:${description.methodName}")
//            }
//
//            override fun testFailure(failure: Failure) {
//                println("[X] ${failure.description.className}:${failure.description.methodName}")
//            }
//        })
//
//        val result = junit.run(clazz)
//        println("Run count: ${result.runCount}, failures: ${result.failureCount}")
//    }
//}

fun main(vararg args: String) {
//    Main.prepareClasspath()
//    Main.run()

//    val paths = classpath.split(":")
//        .filterNot { it.endsWith(".xml") }
//        .filterNot { it.endsWith("/res") }
//        .filter { File(it).exists() && File(it).isDirectory }
//
//    println("count = ${paths.size}")
//    println(paths.joinToString(separator = ","))

    // java -cp /Applications/Android\ Studio.app/Contents/plugins/Kotlin/kotlinc/lib/kotlin-preloader.jar org.jetbrains.kotlin.preloading.Preloader -cp /Applications/Android\ Studio.app/Contents/plugins/Kotlin/kotlinc/lib/kotlin-compiler.jar org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -version

    for (i in 0..10) {
//        kotlinc("-Xuse-fast-jar-file-system /Users/andrey.fomenkov/Workspace/indriver/intercity/api/src/ru.fomenkov.main/java/sinet/startup/inDriver/intercity/api/ui/model/CarModelUi.kt")
    }
}

//fun kotlinc(arg: String) {
//    val start = System.currentTimeMillis()
//    val clazz = Class.forName("org.jetbrains.kotlin.preloading.Preloader")
//    val method = clazz.getMethod("ru.fomenkov.main", Array<String>::class.java)
//
//    method.invoke(null, arrayOf(
//        "-cp",
//        "/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/lib/kotlin-compiler.jar",
//        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
//        arg,
//    ))
//    val end = System.currentTimeMillis()
//    println("Time: ${end - start} msec")
//}