plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    // Standalone extractor library used by PipePipe (GPL-3.0). It bundles the
    // NewPipeExtractor API plus the SABR/UMP stack. Pinned to the commit that
    // PipePipe 5.5.0 ships; the v5.5.0 tag is not published on JitPack.
    api("com.github.InfinityLoop1308.PipePipeExtractor:extractor:1a09d20e68")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.json:json:20260522")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("io.github.aakira:napier:2.7.1")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
