package example.nucleus.bootstrap

import example.nucleus.data.AppDirs
import example.nucleus.data.repository.CrashReportRepository
import example.nucleus.viewmodels.AppViewModel
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

object PlatformCrashHandler {

    fun register() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logCrash(thread, throwable)
        }
    }

    private fun logCrash(thread: Thread, throwable: Throwable) {
        runCatching {
            val report = CrashReportRepository.buildCrashReport(
                thread = thread,
                throwable = throwable,
                appVersion = AppViewModel.CURRENT_VERSION,
            )
            CrashReportRepository.saveCrashReport(report)

            val logsDir = AppDirs.logsDir.also { if (!it.exists()) it.mkdirs() }
            val logFile = File(logsDir, "startup.log")
            val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            val entry = buildString {
                appendLine("[${LocalDateTime.now()}] Uncaught exception on thread '${thread.name}'")
                appendLine(stackTrace)
                appendLine("------------------------------------------------------------")
            }
            logFile.appendText(entry)
        }
        Runtime.getRuntime().halt(1)
    }

    inline fun <T> runSafely(context: String, block: () -> T): T {
        return try {
            block()
        } catch (e: Throwable) {
            logStartupError(context, e)
            throw e
        }
    }

    fun logStartupError(context: String, throwable: Throwable) {
        runCatching {
            val logsDir = AppDirs.logsDir.also { if (!it.exists()) it.mkdirs() }
            val logFile = File(logsDir, "startup.log")
            val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            val entry = buildString {
                appendLine("[${LocalDateTime.now()}] $context")
                appendLine(stackTrace)
                appendLine("------------------------------------------------------------")
            }
            logFile.appendText(entry)
        }
    }
}
