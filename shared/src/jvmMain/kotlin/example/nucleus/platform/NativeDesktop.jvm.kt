package example.nucleus.platform

import io.github.aakira.napier.Napier
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Operaciones de escritorio compatibles con GraalVM native-image.
 *
 * AWT [java.awt.FileDialog] y partes de [Desktop] rompen en Windows nativo
 * (p. ej. `NoSuchFieldError: sun.awt.windows.WFileDialogPeer.parent`). Preferimos
 * diálogos del SO (WinForms vía PowerShell, zenity/kdialog, osascript) y
 * `explorer` / `xdg-open` / `open`.
 */
object NativeDesktop {

    fun openFolder(folder: File): Boolean {
        return try {
            if (!folder.exists()) folder.mkdirs()
            val path = folder.absolutePath
            when {
                Platform.isWindows -> {
                    ProcessBuilder("explorer.exe", path)
                        .redirectErrorStream(true)
                        .start()
                    true
                }
                Platform.isMac -> runProcess("open", path)
                Platform.isLinux -> runProcess("xdg-open", path) ||
                    tryAwtDesktop { it.open(folder) }
                else -> tryAwtDesktop { it.open(folder) }
            }
        } catch (t: Throwable) {
            Napier.w("NativeDesktop.openFolder failed: ${t.message}")
            tryAwtDesktop { it.open(folder) }
        }
    }

    fun browse(uri: URI): Boolean {
        return try {
            val url = uri.toString()
            when {
                Platform.isWindows -> {
                    // rundll32 evita el parsing de `&` de cmd.exe (que cortaba `&body=`).
                    // Fallback a `cmd /c start "" "url"` citado si rundll32 falla.
                    runCatching {
                        ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url)
                            .redirectErrorStream(true)
                            .start()
                    }.isSuccess || runCatching {
                        ProcessBuilder("cmd", "/c", "start", "", "\"$url\"")
                            .redirectErrorStream(true)
                            .start()
                    }.isSuccess || runCatching {
                        ProcessBuilder("explorer.exe", url)
                            .redirectErrorStream(true)
                            .start()
                    }.isSuccess
                }
                Platform.isMac -> runProcess("open", url)
                Platform.isLinux -> runProcess("xdg-open", url) ||
                    tryAwtDesktop { it.browse(uri) }
                else -> tryAwtDesktop { it.browse(uri) }
            }
        } catch (t: Throwable) {
            Napier.w("NativeDesktop.browse failed: ${t.message}")
            tryAwtDesktop { it.browse(uri) }
        }
    }

    /**
     * Diálogo nativo de apertura de archivo. Devuelve la ruta absoluta o null si se cancela.
     *
     * @param title Título de la ventana
     * @param filterDescription Descripción legible del filtro (p. ej. "CSV")
     * @param extensions extensiones sin punto (csv, jpg, …)
     */
    fun pickOpenFile(
        title: String,
        filterDescription: String,
        extensions: List<String>,
    ): String? {
        val exts = extensions.map { it.trim().removePrefix(".").lowercase() }.filter { it.isNotEmpty() }
        return try {
            when {
                Platform.isWindows -> pickOpenFileWindows(title, filterDescription, exts)
                Platform.isMac -> pickOpenFileMac(title, exts)
                Platform.isLinux -> pickOpenFileLinux(title, filterDescription, exts)
                else -> null
            }
        } catch (t: Throwable) {
            Napier.w("NativeDesktop.pickOpenFile failed: ${t.message}")
            null
        }
    }

    private fun pickOpenFileWindows(
        title: String,
        filterDescription: String,
        extensions: List<String>,
    ): String? {
        val patterns = extensions.joinToString(";") { "*.$it" }.ifEmpty { "*.*" }
        val allPattern = if (extensions.isEmpty()) "*.*" else patterns
        // Filter: "CSV (*.csv)|*.csv|All files (*.*)|*.*"
        val filter =
            if (extensions.isEmpty()) {
                "All files (*.*)|*.*"
            } else {
                "$filterDescription ($patterns)|$allPattern|All files (*.*)|*.*"
            }
        // WinForms OpenFileDialog requiere STA.
        val ps = """
            Add-Type -AssemblyName System.Windows.Forms | Out-Null
            ${'$'}d = New-Object System.Windows.Forms.OpenFileDialog
            ${'$'}d.Title = @'
$title
'@
            ${'$'}d.Filter = @'
$filter
'@
            ${'$'}d.Multiselect = ${'$'}false
            ${'$'}d.CheckFileExists = ${'$'}true
            if (${'$'}d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
                [Console]::Out.Write(${'$'}d.FileName)
            }
        """.trimIndent()
        val out = runPowerShell(ps) ?: return null
        val path = out.trim().trim('"')
        return path.takeIf { it.isNotEmpty() && File(it).isFile }
    }

    private fun pickOpenFileMac(title: String, extensions: List<String>): String? {
        val typeClause = if (extensions.isEmpty()) {
            ""
        } else {
            val list = extensions.joinToString(", ") { "\"$it\"" }
            " of type {$list}"
        }
        val script =
            """
            set theFile to choose file with prompt "$title"$typeClause
            return POSIX path of theFile
            """.trimIndent()
        val out = runProcessCapture("osascript", "-e", script) ?: return null
        val path = out.trim()
        return path.takeIf { it.isNotEmpty() && File(it).isFile }
    }

    private fun pickOpenFileLinux(
        title: String,
        filterDescription: String,
        extensions: List<String>,
    ): String? {
        val zenityArgs = buildList {
            add("zenity")
            add("--file-selection")
            add("--title=$title")
            if (extensions.isNotEmpty()) {
                val patterns = extensions.joinToString(" ") { "*.$it" }
                add("--file-filter=$filterDescription | $patterns")
                add("--file-filter=All files | *.*")
            }
        }
        runProcessCapture(zenityArgs)
            ?.trim()?.takeIf { it.isNotEmpty() && File(it).isFile }?.let { return it }

        if (extensions.isNotEmpty()) {
            val pattern = extensions.joinToString(" ") { "*.$it" }
            runProcessCapture(
                "kdialog",
                "--getopenfilename",
                ".",
                "$filterDescription ($pattern)",
            )?.trim()?.takeIf { it.isNotEmpty() && File(it).isFile }?.let { return it }
        } else {
            runProcessCapture("kdialog", "--getopenfilename", ".")
                ?.trim()?.takeIf { it.isNotEmpty() && File(it).isFile }?.let { return it }
        }
        return null
    }

    private fun runPowerShell(script: String): String? {
        val pb = ProcessBuilder(
            "powershell",
            "-STA",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy", "Bypass",
            "-Command", script,
        ).redirectErrorStream(true)
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader().readText()
        if (!proc.waitFor(120, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            return null
        }
        // Cancelar devuelve vacío y exit 0; errores de PS suelen ser exit != 0 con texto.
        if (proc.exitValue() != 0 && out.isBlank()) return null
        return out
    }

    private fun runProcess(vararg cmd: String): Boolean {
        return try {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            p.waitFor(30, TimeUnit.SECONDS)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun runProcessCapture(vararg cmd: String): String? =
        runProcessCapture(cmd.toList())

    private fun runProcessCapture(cmd: List<String>): String? {
        return try {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return null
            }
            if (p.exitValue() != 0 && out.isBlank()) null else out
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryAwtDesktop(block: (Desktop) -> Unit): Boolean {
        return try {
            if (Desktop.isDesktopSupported()) {
                block(Desktop.getDesktop())
                true
            } else {
                false
            }
        } catch (t: Throwable) {
            Napier.w("AWT Desktop fallback failed: ${t.message}")
            false
        }
    }
}
