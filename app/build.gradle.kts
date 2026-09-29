plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.app.speechsummary"
    ndkVersion = "28.2.13676358"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.app.speechsummary"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        debug {
            // Keep Kotlin/UI debuggable while optimizing CPU-heavy whisper.cpp.
            externalNativeBuild {
                cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" }
            }
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    externalNativeBuild {
        cmake { path = file("src/main/jni/whispercpp/CMakeLists.txt") }
    }
    sourceSets.getByName("main").jniLibs.directories.add(
        layout.buildDirectory.dir("generated/llamaJniLibs").get().asFile.absolutePath
    )
    sourceSets.getByName("main").jniLibs.directories.add(
        layout.buildDirectory.dir("generated/nemoJniLibs").get().asFile.absolutePath
    )
    // Keep local reference binaries on disk, but never merge them into an APK.
    sourceSets.getByName("main").assets.setSrcDirs(listOf("src/main/assets-downloadless"))
}

val buildLlamaNative = tasks.register("buildLlamaNative") {
    val output = layout.buildDirectory.dir("generated/llamaJniLibs")
    val sdk = androidComponents.sdkComponents.sdkDirectory.get().asFile
    val ndkVersionForTask = android.ndkVersion
    val root = layout.projectDirectory.asFile
    val source = root.resolve("src/main/jni/llamacpp")
    val adapter = root.resolve("src/main/jni/llama_adapter")
    val buildRoot = layout.buildDirectory.dir("llamaNative")
    val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
    val executable = if (isWindows) ".exe" else ""
    val cmakeBin = sdk.resolve("cmake/3.22.1/bin")
    val cmake = cmakeBin.resolve("cmake$executable")
    val ninja = cmakeBin.resolve("ninja$executable")
    inputs.dir(source)
    inputs.dir(adapter)
    outputs.dir(output)
    doLast {
        val ndk = sdk.resolve("ndk/$ndkVersionForTask")
        val toolchain = ndk.resolve("build/cmake/android.toolchain.cmake")
        check(toolchain.isFile) { "Android NDK $ndkVersionForTask is required" }
        check(cmake.isFile && ninja.isFile) { "Android SDK CMake 3.22.1 with Ninja is required" }
        check(source.resolve("src/llama.cpp").isFile) { "llama.cpp b9878 source is missing" }
        listOf("arm64-v8a", "x86_64").forEach { abi ->
            val build = buildRoot.get().asFile.resolve(abi)
            build.mkdirs()
            fun run(vararg args: String) {
                val process = ProcessBuilder(*args).directory(root)
                    .redirectErrorStream(true).start()
                val outputText = process.inputStream.bufferedReader().use { it.readText() }
                check(process.waitFor() == 0) { outputText }
                println(outputText)
            }
            run(cmake.absolutePath, "-G", "Ninja", "-S", adapter.absolutePath,
                "-B", build.absolutePath,
                "-DCMAKE_MAKE_PROGRAM=${ninja.absolutePath}",
                "-DCMAKE_TOOLCHAIN_FILE=${toolchain.absolutePath}",
                "-DANDROID_ABI=$abi", "-DANDROID_PLATFORM=android-26",
                "-DCMAKE_BUILD_TYPE=Release")
            run(cmake.absolutePath, "--build", build.absolutePath, "--config", "Release", "-j", "4")
            val destination = output.get().asFile.resolve(abi).also { it.mkdirs() }
            val names = setOf("libllama_document.so", "libllama.so", "libggml.so", "libggml-base.so", "libggml-cpu.so")
            names.forEach { name ->
                val built = build.walkTopDown().firstOrNull { it.isFile && it.name == name }
                    ?: error("Missing native library: $name ($abi)")
                built.copyTo(destination.resolve(name), overwrite = true)
            }
        }
    }
}

val buildNemotronNative = tasks.register("buildNemotronNative") {
    val output = layout.buildDirectory.dir("generated/nemoJniLibs")
    val sdk = androidComponents.sdkComponents.sdkDirectory.get().asFile
    val ndkVersionForTask = android.ndkVersion
    val root = layout.projectDirectory.asFile
    val source = root.resolve("src/main/jni/nemo_speech")
    val adapter = root.resolve("src/main/jni/nemo_adapter")
    val buildRoot = layout.buildDirectory.dir("nemoNative")
    val executable = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) ".exe" else ""
    val cmakeBin = sdk.resolve("cmake/3.22.1/bin")
    val cmake = cmakeBin.resolve("cmake$executable")
    val ninja = cmakeBin.resolve("ninja$executable")
    inputs.dir(source)
    inputs.dir(adapter)
    outputs.dir(output)
    doLast {
        val toolchain = sdk.resolve("ndk/$ndkVersionForTask/build/cmake/android.toolchain.cmake")
        check(toolchain.isFile && cmake.isFile && ninja.isFile) { "Android NDK and SDK CMake 3.22.1 are required" }
        listOf("arm64-v8a", "x86_64").forEach { abi ->
            val build = buildRoot.get().asFile.resolve(abi).also { it.mkdirs() }
            fun run(vararg args: String) {
                val process = ProcessBuilder(*args).directory(root).redirectErrorStream(true).start()
                val outputText = process.inputStream.bufferedReader().use { it.readText() }
                check(process.waitFor() == 0) { outputText }
                println(outputText)
            }
            run(cmake.absolutePath, "-G", "Ninja", "-S", adapter.absolutePath,
                "-B", build.absolutePath, "-DCMAKE_MAKE_PROGRAM=${ninja.absolutePath}",
                "-DCMAKE_TOOLCHAIN_FILE=${toolchain.absolutePath}",
                "-DANDROID_ABI=$abi", "-DANDROID_PLATFORM=android-26", "-DCMAKE_BUILD_TYPE=Release")
            run(cmake.absolutePath, "--build", build.absolutePath, "--target", "nemotron_diar", "-j", "4")
            val destination = output.get().asFile.resolve(abi).also { it.mkdirs() }
            build.resolve("libnemotron_diar.so").copyTo(destination.resolve("libnemotron_diar.so"), overwrite = true)
        }
    }
}

tasks.matching { it.name.matches(Regex("merge.*(NativeLibs|JniLibFolders)")) }.configureEach {
    dependsOn(buildLlamaNative)
    dependsOn(buildNemotronNative)
}

dependencies {
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
