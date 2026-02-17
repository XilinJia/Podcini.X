plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "${project.findProperty("kotlin_version")}" apply false
    id("io.github.xilinjia.krdb") version "${project.findProperty("krdb_version")}" apply false
}

buildscript {
    dependencies {
//        classpath("io.github.xilinjia.krdb:gradle-plugin:3.3.2")
    }
}
