plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}



kotlin {

    jvm()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            // coloca aquí tus dependencias Multiplatform

            api(project(":innertube"))
            // InnertubeX: cliente SABR/UMP (streaming segmentado) para URLs con enforcement
            // de Range (rqh/spc) que mpv no puede reproducir de forma progresiva.
            implementation("com.github.MetrolistGroup.innertubex:innertubex:v0.2.1")
            api(libs.compose.components.resources)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.websockets)

            api(libs.sqldelight.coroutines)


            api("io.github.aakira:napier:2.7.1")



            // Librería DataStore
            api("androidx.datastore:datastore:1.2.1")
            api("androidx.datastore:datastore-preferences:1.2.1")

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            api(libs.sqldelight.driver.jvm)
            // Proveedores de letras (LRC sincronizado) — módulos solo JVM
            implementation(project(":lrclib"))
            implementation(project(":kugou"))
            // Fuente: https://mvnrepository.com/artifact/net.java.dev.jna/jna
            implementation("net.java.dev.jna:jna:5.18.1")

            // Fuente: https://mvnrepository.com/artifact/net.java.dev.jna/jna-platform-jpms
            implementation("net.java.dev.jna:jna-platform-jpms:5.18.1")
            implementation("org.jetbrains.runtime:jbr-api:1.10.1")
            implementation("dev.toastbits:mediasession:0.1.1")
            // Media controls del sistema (SMTC/MPRIS/Now Playing) vía Nucleus.
            implementation("dev.nucleusframework:nucleus.media-control:2.5.14")

            // PoTokens web: desde julio 2026 los programas de BotGuard solo entregan el
            // minter con un entorno tipo JSDOM, fuera del alcance de un QuickJS embebido.
            // Se delega al sidecar rustypipe-botguard (RustyPipeBotGuardSidecar, binario en
            // mpv-resources/windows). Ver PoTokenGenerator.jvm para el historial.
            implementation(libs.quickjs)

            // GraalJS (cipher `n`/`s` de player.js) — motor JS del solucionador EJS de los
            // formatos web. Sin él, WEB_REMIX/TVHTML5 devuelven sigCipher pero no se puede
            // deobfuscar y la calidad web no se recupera.
            // NOTA: truffle-runtime es un módulo JPMS incompatible con el uber JAR de
            // GraalVM native-image; si reactivas `buildGraalvmNative`, hay que excluirlo o
            // comentar estas dos líneas (como estaba antes).
            implementation("org.graalvm.js:js-scriptengine:25.0.3")
            implementation("org.graalvm.js:js:25.0.3")
        }
    }
}

compose.resources {
    packageOfResClass = "example.nucleus.generated.resources"
    publicResClass = true
}

sqldelight {
    databases {
        create("MusicPlayerDatabase") {
            packageName.set("example.nucleus.db")
        }
    }
}
