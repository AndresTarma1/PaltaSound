import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
    id("dev.nucleusframework") version "2.5.4"

}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(project(":shared"))
    val nucleusVersion = "2.5.4"



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
        "-XX:CompressedClassSpaceSize=64m",
        "-XX:ReservedCodeCacheSize=128m",
        "-XX:CICompilerCount=2",
        // Limita los "cores vistos" por la JVM: reduce ForkJoinPool/Dispatchers.Default (32->4),
        // el dispatcher de OkHttp y los hilos de GC. Para un reproductor bastan 4 workers CPU.
        "-XX:ActiveProcessorCount=4",
        // Pilas de hilo más pequeñas (muchos hilos IO/render).
        "-Xss768k",
        // Pool IO de coroutines: default real es 64 hilos (~1MB stack c/u).
        "-Dkotlinx.coroutines.io.parallelism=16",
        // Silencia el warning de Truffle del solver EJS (corre en intérprete si no hay
        // compilador Graal; opción 2 del perfil de rendimiento).
        "-Dpolyglot.engine.WarnInterpreterOnly=false",
        // JVMCI: habilita el JIT de Truffle/GraalJS si la JVM trae el compilador Graal
        // (p. ej. corriendo sobre GraalVM JDK). En un JDK sin compilador Graal no acelera
        // (sigue en intérprete) pero es inofensivo. Inofensivo también si el JVM lo ignora.
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        // Skiko: render en GPU (Direct3D) pero con caché de recursos acotada (default ilimitada).
        "-Dskiko.gpu.resourceCacheLimit=128M",
//        "-Dskiko.buffering=DOUBLE",
//        "-Dskiko.vsync.enabled=true",
    )


    nativeDistributions {
        // El AOT cache del JDK 25 (JEP 483) tiene un límite fijo de tamaño de la región 'ro'
        // (~40 MB); con el volumen de clases actual la app se desborda por pocos KB al
        // escribir app.aot ("Unable to allocate from 'ro' region", sin flag para agrandarlo).
        // Desactivado: además falla en CI ("An error has occurred while writing the shared archive file").
        enableAotCache = false
        appName = "PaltaSound"
        packageName = "PaltaSound"
        packageVersion = "0.8.2"
        vendor = "Tarma"
        homepage = "https://github.com/AndresTarma1/PaltaSound"
        // Nsis (no Exe): el updater selecciona el instalador .exe filtrando por el sufijo
        // "-nsis." del nombre del artefacto, y el plugin solo añade ese sufijo para el target
        // Nsis/NsisWeb/Portable. Ambos generan el mismo instalador electron-builder "nsis".
        targetFormats(TargetFormat.Nsis, TargetFormat.Deb, TargetFormat.Rpm)

        windows {
            upgradeUuid = "4A2F8B6C-1D3E-4F5A-B7C8-9D0E1F2A3B4C"
            menu = true
            perUserInstall = true
            iconFile.set(project.file("src/icons/PaltaSound.ico"))
            // NSIS (EXE): instalador asistido con página de licencia GPL-3.0 y elección de carpeta.
            nsis {
                oneClick = false
                allowToChangeInstallationDirectory = true
                license = rootProject.file("LICENSE")
            }
        }

        linux {
            iconFile.set(project.file("src/icons/PaltaSound.png"))
            debMaintainer = "Andres Tarma <andrestormenta1@gmail.com>"
        }

        includeAllModules = true
        appResourcesRootDir.set(project.layout.projectDirectory.dir("../mpv-resources"))
    }

    // ── GraalVM Native Image (DESACTIVADO) ────────────────────────────────
    // Se distribuye únicamente la variante JVM (compatibilidad): el runtime Truffle/GraalJS
    // exige module-path y el uber-jar de classpath rompe native-image (errores ForceOnModulePath
    // y JNI$JNIEnv word operation, verificados en GraalVM 25.2.4 y 25.3.4.1). Además el cipher
    // EJS necesita GraalJS completo y los motores alternativos (Rhino/QuickJS) no lo cubren.
    // Nucleus genera los metadatos de reflexión/recursos/JNI automáticamente (5 niveles) y descarga GraalVM CE.
    // Tareas: packageGraalvmNative, runGraalvmNative, runWithNativeAgent.
    graalvm {
        isEnabled = false
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