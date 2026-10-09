import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)

    alias(libs.plugins.dependency.analysis) apply true
}

kotlin {
    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

val ndkVersion = "30.0.16248370"

android {
    namespace = "org.perceivers25.warpinator"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "org.perceivers25.warpinator"
        minSdk = 26
        targetSdk = 37
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("RELEASE_ANDROID_KEYSTORE_PATH")
            if (!keystorePath.isNullOrEmpty() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null) {
                signingConfig = releaseSigning
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugRuntimeOnly(libs.androidx.compose.ui.test.manifest)

    // State management
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.zxing)

    implementation(libs.kotlinx.serialization.json)

    implementation("net.java.dev.jna:jna:5.18.1@aar")

    testImplementation(libs.junit)
}

val ffiBindingsDir = rootProject.projectDir.parentFile.resolve("ffi-bindings")

fun findNdkDir(): String? {
    // Check environment first (CI / developer override)
    val envNdk = System.getenv("ANDROID_NDK_HOME")
    if (!envNdk.isNullOrEmpty()) {
        return envNdk
    }
    val localPropertiesFile = rootProject.projectDir.resolve("local.properties")
    if (localPropertiesFile.exists()) {
        val properties = Properties()
        localPropertiesFile.inputStream().use { properties.load(it) }
        // Explicit ndk.dir takes priority
        val ndkDir = properties.getProperty("ndk.dir")
        if (!ndkDir.isNullOrEmpty()) {
            return ndkDir
        }
        // Derive from sdk.dir + hardcoded ndkVersion
        val sdkDir = properties.getProperty("sdk.dir")
        if (!sdkDir.isNullOrEmpty()) {
            val versionedNdk = file("$sdkDir/ndk/$ndkVersion")
            if (versionedNdk.exists()) {
                return versionedNdk.absolutePath
            }
        }
    }
    return null
}

val buildRustLibs by tasks.registering(Exec::class) {
    description =
        "Compile Rust FFI libraries for all Android architectures and generate Kotlin bindings"
    group = "build"
    workingDir = ffiBindingsDir

    val ndkDir = findNdkDir()
    if (ndkDir != null) {
        environment("ANDROID_NDK_HOME", ndkDir)
    }

    val userHome = System.getProperty("user.home")
    val currentPath = System.getenv("PATH") ?: ""
    environment("PATH", "$userHome/.cargo/bin:$currentPath")

    commandLine(
        "cargo",
        "run",
        "--bin",
        "build-android",
        "--features",
        "virtual_filesystem",
    )
}

tasks.named("preBuild") {
    dependsOn(buildRustLibs)
}

tasks.named<Delete>("clean") {
    delete(
        fileTree("src/main/jniLibs") {
            include("**/*.so")
        },
    )
}