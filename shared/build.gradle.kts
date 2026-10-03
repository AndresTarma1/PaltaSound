plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Variante de instalación (ver bloque de motores JS en jvmMain más abajo):
// por defecto los runtimes JS viajan en el paquete (JVM); con -PjsEngines=false
// se dejan en compileOnly + excludes para la imagen nativa GraalVM.
val jsEnginesOff = findProperty("jsEngines") == "false"



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
            // OJO: innertubex trae QuickJS de forma transitiva (su paquete cipher, que esta
            // app no usa: solo se usan InnerTube/models/sabr/extraction.PlaybackNonce, sin
            // referencias a cipher). Con -PjsEngines=false se excluye para que ni el jar ni
            // sus nativos (.dll/.so embebidos) viajen en la variante GraalVM.
            implementation("com.github.MetrolistGroup.innertubex:innertubex:v0.2.1") {
                if (jsEnginesOff) exclude(group = "io.github.dokar3")
            }
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
        // Los motores JS van en tests SIEMPRE, haya o no flag: el classpath de test no se
        // empaqueta, asi que no afecta a ninguna de las dos variantes de instalacion, y los
        // tests del cipher (EjsCipherSolverSmokeTest, QuickJs4jEjsTest) los necesitan para
        // correr tambien cuando se compila con -PjsEngines=false.
        jvmTest.dependencies {
            implementation(libs.quickjs)
            implementation("org.graalvm.js:js-scriptengine:25.0.3")
            implementation("org.graalvm.js:js:25.0.3")
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
            //
            // ── Motores JS por variante de instalación ──────────────────────────
            // - JVM (defecto, instaladores Nsis/Deb/Rpm y `run`): `implementation`, los
            //   motores viajan en el paquete y el cipher web funciona completo.
            // - GraalVM nativo (`-PjsEngines=false`): `compileOnly`, el código compila
            //   (las referencias directas resuelven) pero los jars NO entran ni en el
            //   runtimeClasspath ni en el análisis de native-image. Sin esto, el runtime
            //   Truffle exige module-path y rompe el uber-jar (errores ForceOnModulePath
            //   y JNI$JNIEnv, verificados en GraalVM 25.2.4 y 25.3.4.1).
            // En runtime la ausencia se detecta sola: `getEngineByName("graal.js")`
            // devuelve null (CipherException, ya capturada) y `QuickJs.create()` lanza
            // LinkageError (capturado en ParsedScriptSolver.create). El cipher web queda
            // degradado en nativo; la reproducción tira de otros clientes/yt-dlp.
            if (!jsEnginesOff) {
                implementation(libs.quickjs)
                implementation("org.graalvm.js:js-scriptengine:25.0.3")
                implementation("org.graalvm.js:js:25.0.3")
            } else {
                compileOnly(libs.quickjs)
                compileOnly("org.graalvm.js:js-scriptengine:25.0.3")
                compileOnly("org.graalvm.js:js:25.0.3")
            }
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
