import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
    id("dev.nucleusframework") version "2.5.18"

}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(project(":shared"))
    val nucleusVersion = "2.5.18"



    implementation("dev.nucleusframework:nucleus.nucleus-application:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.updater-runtime:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.decorated-window-tao:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.decorated-window-material3:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.graalvm-runtime:${nucleusVersion}")
    // Add only the modules you need:
    implementation("dev.nucleusframework:nucleus.notification-common:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.global-hotkey:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.media-control:${nucleusVersion}")
    implementation("dev.nucleusframework:nucleus.autolaunch:${nucleusVersion}")

    implementation("dev.nucleusframework:composenativetray:2.1.3")


    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.compose.ui)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)


    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.decompose)
    implementation(libs.decompose.compose)
    implementation(libs.decompose.compose.experimental)

    implementation(libs.kotlinx.serialization.core)

    implementation(libs.coil.compose.get().toString()) {
        exclude(group = "org.jetbrains.skiko")
    }
    implementation(libs.coil.network.ktor3.get().toString()) {
        exclude(group = "org.jetbrains.skiko")
    }

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    implementation(libs.materialKolor)
    implementation(libs.kmpalette.core)
    implementation(libs.kmpalette.network)
    implementation(libs.kmpalette.extensions.file)


    implementation(libs.reorderable)

    implementation(libs.heze)
    implementation(libs.heze.blur)
    implementation(libs.heze.blur.materials)

    implementation("ir.mahozad.multiplatform:wavy-slider:2.2.0")

    implementation(libs.composeSettings.ui)
    implementation(libs.composeSettings.ui.expressive)
    implementation(libs.composeSettings.ui.extended)



    implementation(libs.kotlin.test)

    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.jnativehook)

    implementation(libs.jna)
    implementation(libs.jna.platform.jpms)

    implementation(libs.jewel.ui.standalone)
    implementation(libs.jewel.ui.decorated.window)
    implementation(libs.jbr)

    // Widgets Compose dentro de la barra de tareas de Windows (solo desktop/JVM)
    implementation("uk.kulikov:compose-windows-taskbar:0.1.0")



    implementation(compose.desktop.currentOs)
}

// Jewel trae el fork de coroutines de JetBrains (`org.jetbrains.intellij.deps.kotlinx`,
// via icons-api de la plataforma IntelliJ) con OTRO groupId, asi que Gradle no lo ve como
// conflicto y AMBOS jars viajan en el app-image: el viejo (1.10.2-intellij-1, sin
// `runBlockingK`) ordena primero en el classpath de jpackage y la app muere al arrancar
// con NoSuchMethodError. Se excluye el fork: la API la cubre el oficial 1.11.0, mas nuevo.
// Si reaparece otro modulo del fork, mirar el arbol (`:desktopApp:dependencies
// --configuration runtimeClasspath`) y ampliar la exclusion.
configurations.all {
    exclude(group = "org.jetbrains.intellij.deps.kotlinx", module = "kotlinx-coroutines-core-jvm")
}


nucleus.application {
    mainClass = "example.nucleus.MainKt"

    // Perfil de memoria: SerialGC (footprint mínimo, sin card-tables/regiones de G1) + heap
    // moderado. Devuelve heap al OS vía Min/MaxHeapFreeRatio en cada GC full (el FrameWatcher
    // de Skiko lo dispara periódicamente). Ver Oracle GC tuning + Skiko configuration.
    jvmArgs(
        // Heap
        "-Xms64m",
        "-Xmx320m",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=100",
        // G1PeriodicGC libera periódicamente el heap Y los objetos nativos de Skia (imágenes):
        // sin GC completo periódico, la memoria nativa de Skia/Compose se acumula (peak 740MB).
        "-XX:G1PeriodicGCInterval=10000",
        "-XX:G1PeriodicGCSystemLoadThreshold=0.0",
        "-XX:+UseStringDeduplication",
        // Encoger el heap y devolverlo al OS cuando sobra (default 40/70 retiene demasiado).
        "-XX:MinHeapFreeRatio=10",
        "-XX:MaxHeapFreeRatio=30",
        // No-heap acotado (hoy sin tope; Compose+Ktor+Coil cargan muchas clases).
        "-XX:MaxMetaspaceSize=192m",
        // JDK-8376125: el presupuesto de las regiones rw/ro del AOT sale de aqui. Con 64m
        // (67.108.864 B) el reparto del JDK se quedaba 2-64 KB corto y el writer moria con
        // "Unable to allocate from 'ro' region" (nosotros en ro, el bug en rw). El total medido
        // fue 67.043.328 B en todas las pruebas, asi que 96m da holgura de sobra. Subirlo por
        // `aotCache { extraTrainingJvmArgs }` NO sirve: ese flag no llega al proceso que
        // escribe la cache, tiene que ir en los jvmArgs de la app.
        "-XX:CompressedClassSpaceSize=96m",
        "-XX:ReservedCodeCacheSize=128m",
        "-XX:CICompilerCount=2",
        // Limita los "cores vistos" por la JVM: reduce ForkJoinPool/Dispatchers.Default (32->4),
        // el dispatcher de OkHttp y los hilos de GC. Para un reproductor bastan 4 workers CPU.
        "-XX:ActiveProcessorCount=4",
        // Pilas de hilo más pequeñas (muchos hilos IO/render).
        "-Xss768k",
        // Pool IO de coroutines: default real es 64 hilos (~1MB stack c/u).
        "-Dkotlinx.coroutines.io.parallelism=16",
        "-Dpolyglot.engine.WarnInterpreterOnly=false",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        "-Dskiko.gpu.resourceCacheLimit=32M",
    )


    nativeDistributions {
        // Cache AOT de Leyden para arranque rapido en JVM. Con `-PaotCache=false` se omite
        // (no hay training ni app.aot): util para iterar el instalador en local sin pagar el
        // training, y como via de escape si el training graba mas clases de las que caben en
        // la region `ro` ("Unable to allocate from 'ro' region"). Por defecto ON para release.
        enableAotCache = findProperty("aotCache") != "false"
        appName = "PaltaSound"
        packageName = "PaltaSound"
        packageVersion = "0.8.2"
        vendor = "Tarma"
        homepage = "https://github.com/AndresTarma1/PaltaSound"

        targetFormats(TargetFormat.Nsis, TargetFormat.Deb, TargetFormat.Rpm)

        windows {
            upgradeUuid = "4A2F8B6C-1D3E-4F5A-B7C8-9D0E1F2A3B4C"
            menu = true
            iconFile.set(project.file("src/icons/PaltaSound.ico"))
            // NSIS (EXE): instalador asistido con página de licencia GPL-3.0 y elección de carpeta.
            nsis {
                oneClick = false
                allowToChangeInstallationDirectory = true
                license = rootProject.file("LICENSE")
                perMachine = true
            }
        }

        linux {
            iconFile.set(project.file("src/icons/PaltaSound.png"))
            debMaintainer = "Andres Tarma <andrestormenta1@gmail.com>"
        }

        includeAllModules = true
        appResourcesRootDir.set(project.layout.projectDirectory.dir("../mpv-resources"))
    }

    // ── GraalVM Native Image ──────────────────────────────────────────
    // Segunda forma de instalación (Windows): binario nativo SIN runtimes JS.
    // Los motores JS (GraalJS/QuickJS) se excluyen compilando con `-PjsEngines=false`,
    // que los deja en `compileOnly`: el código compila pero los jars no entran en el
    // análisis de native-image. Ese era el bloqueo verificado antes (el runtime Truffle
    // exige module-path y rompía el uber-jar con ForceOnModulePath y JNI$JNIEnv en
    // GraalVM 25.2.4/25.3.4.1). En nativo el cipher web queda degradado (ver el bloque
    // de motores JS en shared/build.gradle.kts); la primera forma de instalación, la
    // distribución JVM (Nsis/Deb/Rpm, con motores), no cambia.
    // Nucleus genera los metadatos de reflexión/recursos/JNI automáticamente (5 niveles) y descarga GraalVM CE.
    // Tareas: packageGraalvmNative, runGraalvmNative, runWithNativeAgent.
    graalvm {
        isEnabled = true
        imageName = "paltasound"
        // GUI desktop: AWT no-headless explícito para native-image.
        buildArgs.add("-Djava.awt.headless=false")
        // SLF4J termina en el image heap durante el build (doc de Nucleus: paquete completo).
        buildArgs.add("--initialize-at-build-time=org.slf4j")
        // Iconos del thumbbar: incluir los .ico como recursos del image heap para que
        // getResourceAsStream("/thumbbar/...") funcione en el binario nativo.
        buildArgs.add("-H:IncludeResources=thumbbar/.*")
        // Heap del binario nativo acotado (objetivo RAM); Serial GC por defecto.
        maxHeapSize = "320m"
    }

    // Los recursos Windows de runtime a veces no se propagan desde appResources al app-image
    // GraalVM (especialmente en un checkout limpio). Se copian explícitamente antes de empaquetar.
    val windowsRuntimeResources = rootProject.file("mpv-resources/windows")
    val graalvmOutputDir = layout.buildDirectory.dir("compose/binaries/main/graalvm-app/PaltaSound")
    tasks.register<Copy>("copyWindowsRuntimeResourcesGraalvm") {
        description = "Copiamos el runtime de windows necesario para un perfecto funcionamiento en GraalVM"
        from(windowsRuntimeResources) {
            include("libmpv-2.dll", "smtc_bridge.dll", "yt-dlp.exe", "rustypipe-botguard.exe")
        }
        into(graalvmOutputDir)
    }
    tasks.matching { it.name == "packageGraalvmNative" }.configureEach {
        finalizedBy("copyWindowsRuntimeResourcesGraalvm")
    }
    tasks.matching { it.name.startsWith("packageGraalvm") && it.name.contains("Distribution") }.configureEach {
        dependsOn("copyWindowsRuntimeResourcesGraalvm")
    }
    tasks.matching { it.name == "packageGraalvmMsi" || it.name == "packageGraalvmNsis" }.configureEach {
        dependsOn("copyWindowsRuntimeResourcesGraalvm")
    }

    val jvmOutputDir = layout.buildDirectory.dir("compose/binaries/main/app/PaltaSound")
    val smtcBridgeDll = rootProject.file("mpv-resources/windows/smtc_bridge.dll")
    tasks.register<Copy>("copySmtcBridgeJvm") {
        from(smtcBridgeDll)
        into(jvmOutputDir)
    }
    tasks.matching { it.name == "packageDistributionForCurrentOS" }.configureEach {
        finalizedBy("copySmtcBridgeJvm")
    }

}