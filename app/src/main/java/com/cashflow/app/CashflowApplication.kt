package com.cashflow.app

import android.app.Application
import android.os.Build
import com.cashflow.app.data.service.OfflineSyncManager
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CashflowApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Setup global crash logging handler
        setupCrashLogging()

        // 2. Initialize resilient network connectivity monitor
        OfflineSyncManager.initialize(this)
        com.cashflow.app.data.service.LocalReminderJob.schedule(this)
    }

    private fun setupCrashLogging() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logDir = File(filesDir, "logs")
                if (!logDir.exists()) logDir.mkdirs()
                val crashFile = File(logDir, "crash_latest.log")

                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()

                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val report = buildString {
                    appendLine("Crash Timestamp: $time")
                    appendLine("App Version: 1.1.0 (110)")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                    appendLine("Stacktrace:")
                    appendLine(stackTrace)
                }

                crashFile.writeText(report)
            } catch (_: Exception) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
