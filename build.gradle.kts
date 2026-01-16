
plugins {
    val kotlin_version: String by project
    id("com.android.application") version "9.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version kotlin_version apply false
    id("io.github.xilinjia.krdb") version "3.3.0" apply false
}

buildscript {
    dependencies {
        classpath("org.codehaus.groovy:groovy-xml:3.0.25")
        classpath("io.github.xilinjia.krdb:gradle-plugin:3.3.0")
    }
}
