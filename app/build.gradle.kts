
plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.xilinjia.krdb")
    id("kotlin-parcelize")
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
//    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability_config.conf")
}

android {
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        minSdk = 26
        targetSdk = 36

        vectorDrawables.useSupportLibrary = false
        vectorDrawables.generatedDensities?.clear()

        versionCode = 237
        versionName = "10.4.3"

        ndkVersion = "29.0.14206865"

        applicationId = "ac.mdiq.podcini.X"

        val apiKey = project.findProperty("podcastindexApiKey") as? String ?: ""
        val apiSecret = project.findProperty("podcastindexApiSecret") as? String ?: ""
        if (apiKey.isNotEmpty()) {
            buildConfigField("String", "PODCASTINDEX_API_KEY", "\"$apiKey\"")
            buildConfigField("String", "PODCASTINDEX_API_SECRET", "\"$apiSecret\"")
        } else {
            buildConfigField("String", "PODCASTINDEX_API_KEY", "\"QT2RYHSUZ3UC9GDJ5MFY\"")
            buildConfigField("String", "PODCASTINDEX_API_SECRET", "\"Zw2NL74ht5aCtx5zFL$#MY$##qdVCX7x37jq95Sz\"")
        }
        vectorDrawables.useSupportLibrary = true
    }

    kotlin {
        jvmToolchain(17)
    }

    val metaInfExcludes = listOf("DEPENDENCIES", "LICENSE", "NOTICE", "CHANGES", "README.md", "NOTICE.txt", "LICENSE.txt", "MANIFEST.MF").map { "/META-INF/$it" }
    packaging {
        resources {
            excludes.addAll(metaInfExcludes)
            pickFirsts.add("/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }

    flavorDimensions += "market"
    productFlavors {
        create("free") {
            dimension = "market"
        }
        create("play") {
            dimension = "market"
        }
    }

    namespace = "ac.mdiq.podcini"
    lint {
        lintConfig = file("lint.xml")
        checkReleaseBuilds = false
        checkDependencies = true
        warningsAsErrors = true
        abortOnError = true
        checkGeneratedSources = true
        disable += listOf("TypographyDashes", "TypographyQuotes", "ObsoleteLintCustomCheck", "BatteryLife",
            "ExportedReceiver", "VectorDrawableCompat", "NestedWeights", "Overdraw", "TextFields",
            "AlwaysShowAction", "Autofill", "ClickableViewAccessibility", "ContentDescription",
            "KeyboardInaccessibleWidget", "LabelFor", "RelativeOverlap", "SetTextI18n",
            "RtlCompat", "RtlHardcoded", "VectorPath", "RtlEnabled")
    }

    signingConfigs {
        create("releaseConfig") {
            enableV1Signing = true
            enableV2Signing = true
            storeFile = file(project.findProperty("releaseStoreFile") as? String ?: "keystore")
            storePassword = project.findProperty("releaseStorePassword") as? String ?: "password"
            keyAlias = project.findProperty("releaseKeyAlias") as? String ?:  "alias"
            keyPassword = project.findProperty("releaseKeyPassword") as? String ?:  "password"
        }
    }

    buildTypes {
        getByName("release") {
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            resValue("string", "app_name", "Podcini.X")
            resValue("string", "provider_authority", "ac.mdiq.podcini.X.provider")
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs["releaseConfig"]
        }
        getByName("debug") {
            resValue("string", "app_name", "Podcini.X Debug")
            applicationIdSuffix = ".debug"
            resValue("string", "provider_authority", "ac.mdiq.podcini.X.debug.provider")
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val applicationName = "Podcini.X"
            val buildType = variant.buildType.name
            val versionName = variant.versionName
            val flavorName = variant.flavorName ?: ""
            val abiName = output.getFilter(com.android.build.OutputFile.ABI) ?: ""
            outputFileName = "${applicationName}_${buildType}_${flavorName}_${versionName}_${abiName}.apk"
        }
    }
    androidResources {
//        additionalParameters "--no-version-vectors"
        generateLocaleConfig = true
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")

    implementation("androidx.paging:paging-compose:3.3.6")

    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.1")
    implementation("androidx.navigation:navigation-compose:2.9.6")

    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.webkit:webkit:1.15.0")
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.work:work-runtime:2.11.0")

    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.9.0")
    implementation("androidx.media3:media3-ui:1.9.0")
    implementation("androidx.media3:media3-common:1.9.0")
    implementation("androidx.media3:media3-session:1.9.0")

    implementation("com.google.android.material:material:1.13.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${project.property("kotlin_version")}")

    implementation("io.github.xilinjia.krdb:library-base:3.2.9")

    implementation("com.github.TeamNewPipe:nanojson:1d9e1aea9049fc9f85e68b43ba39fe7be1c1f751")
    implementation("io.reactivex.rxjava3:rxjava:3.1.12")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")

    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("commons-io:commons-io:2.21.0")       // 20030203.000550 is not the lastest
    implementation("org.apache.commons:commons-lang3:3.20.0")

    implementation("org.jsoup:jsoup:1.22.1")

    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okio:okio:3.16.4")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:5.3.2")
    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("com.github.ByteHamster:SearchPreference:v2.5.0")

    compileOnly("com.google.android.wearable:wearable:2.9.0")

    "freeImplementation"("org.conscrypt:conscrypt-android:2.5.3")
    debugImplementation("androidx.compose.ui:ui-tooling:1.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling-preview:1.10.0")

    "playImplementation"("com.google.android.play:core-ktx:1.8.1")
    "playImplementation"("com.google.android.gms:play-services-base:18.9.0")
    "playApi"("androidx.mediarouter:mediarouter:1.8.1")
    "playApi"("com.google.android.gms:play-services-cast-framework:22.2.0")
}

val copyLicenseTask = tasks.register<Copy>("copyLicense") {
    from("../LICENSE")
    into("src/main/assets/")
    rename { "$it.txt" }
}

tasks.named("preBuild") {
    dependsOn(copyLicenseTask)
}
