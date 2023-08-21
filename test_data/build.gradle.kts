plugins {
    id("sample-module")
    id("kotlin-kapt")
}

android {
    namespace = "sample.namespace"
}

dependencies {

    api(project(":m1:sub1"))
    api(project(":m1:sub2"))

    implementation(project(' :m2:sub1'))
    implementation(project(':m2:sub2 '))
    implementation(project('  :m2:sub3:sub4  '))

    implementation(project(" :m3:sub1"))
    implementation(project(":m3:sub2 "))
    implementation(project("  :m3:sub3:sub4  "))

    implementation(libs.sample.lib1)
    implementation(libs.sample.lib2)
    implementation(libs.sample.lib3)

    kapt(libs.sample.kapt)
}
