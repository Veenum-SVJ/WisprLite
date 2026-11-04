import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from keystore.properties (gitignored locally; CI writes it
// from GitHub secrets). Without it, release builds are left unsigned.
val releaseProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Licensed brand fonts stay out of the public source tree. CI restores them from
// a repository secret; public-source builds remain usable with system typography.
val licensedFonts = rootProject.file(".tools/licensed-fonts")
val referenceResources = layout.buildDirectory.dir("generated/reference-res")
val prepareReferenceTypography = tasks.register("prepareReferenceTypography") {
    inputs.files(fileTree(licensedFonts) { include("*.otf") })
    outputs.dir(referenceResources)
    doLast {
        val output = referenceResources.get().asFile
        val fonts = output.resolve("font").apply { mkdirs() }
        val values = output.resolve("values").apply { mkdirs() }
        val names = listOf("yowza_regular", "yowza_medium", "yowza_bold", "yowza_soft_regular", "yowza_soft_medium")
        val available = names.map { licensedFonts.resolve("$it.otf").exists() }
        require(available.all { it } || available.none { it }) { "Provide all five licensed Yowza font files or none" }
        val enabled = available.all { it }
        for (name in names) {
            val destination = fonts.resolve("$name.otf")
            if (enabled) licensedFonts.resolve("$name.otf").copyTo(destination, overwrite = true) else destination.delete()
        }
        fun family(name: String, members: List<Pair<String, Int>>) {
            val file = fonts.resolve("$name.xml")
            if (!enabled) { file.delete(); return }
            file.writeText("<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\">" +
                members.joinToString("") { (font, weight) -> "<font android:font=\"@font/$font\" android:fontStyle=\"normal\" android:fontWeight=\"$weight\"/>" } + "</font-family>")
        }
        family("reference_sans", listOf("yowza_regular" to 400, "yowza_medium" to 500, "yowza_bold" to 700))
        family("reference_display", listOf("yowza_soft_regular" to 400, "yowza_soft_medium" to 500))
        val family = if (enabled) "@font/reference_sans" else "sans-serif"
        values.resolve("reference_typography.xml").writeText("<resources><style name=\"Base.OpenWisperTypography\" parent=\"Theme.Material3.DayNight.NoActionBar\"><item name=\"fontFamily\">$family</item><item name=\"android:fontFamily\">$family</item></style></resources>")
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareReferenceTypography) }

android {
    sourceSets.getByName("main").res.srcDir(referenceResources)
    namespace = "com.edib.openwhispr"
    compileSdk = 35

    signingConfigs {
        getByName("debug") {
            val developmentKey = rootProject.file(".tools/wisprlite-debug.keystore")
            if (developmentKey.exists()) {
                storeFile = developmentKey
                storePassword = "android"
                keyAlias = "AndroidDebugKey"
                keyPassword = "android"
            }
        }
        create("release") {
            if (releaseProps.isNotEmpty()) {
                storeFile = rootProject.file(releaseProps.getProperty("storeFile"))
                storePassword = releaseProps.getProperty("storePassword")
                keyAlias = releaseProps.getProperty("keyAlias")
                keyPassword = releaseProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.veenum.wisprlite"
        minSdk = 30
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 7
        versionName = "0.2.4"

        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseProps.isNotEmpty()) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    @Suppress("DEPRECATION")
    kotlinOptions { jvmTarget = "17" }

    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.k2fsa:sherpa-onnx:1.13.8@aar")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.work:work-runtime:2.9.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("org.apache.commons:commons-compress:1.27.1")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}



