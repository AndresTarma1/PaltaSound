plugins {
    alias(libs.plugins.kotlin.serialization)
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)
    // El upstream (Metrolist) usa Timber (solo Android) para logging; se cambió por Napier, ya que este
    // módulo no tiene dependencia de Android y el resto de MusicPlayer ya usa Napier para logging.
    implementation("io.github.aakira:napier:2.7.1")
    testImplementation(libs.junit)
}

