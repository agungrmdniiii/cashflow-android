package com.cashflow.app

import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.backup.*
import com.cashflow.app.data.service.AutomaticBackupJob
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalTime

/** Explicit physical-test controller. Never reads/edits financial records or exports credentials. */
class BackupPowerAuditTest {
    @Test fun control() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Physical power audit requires explicit opt-in", args.getString("backupPowerAudit") == "approved")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = BackupSettingsStore(context)
        val snapshot = File(context.cacheDir, "backup-power-audit-options.json")
        when (args.getString("stage")) {
            "arm" -> {
                check(store.driveAccount != null && BackupPasswordStore(context).isConfigured() &&
                    !store.preferences.getBoolean("drive_reauth", false)) { "Connect Drive before the power audit." }
                val original = store.automation()
                if (!snapshot.exists()) snapshot.writeText(JSONObject().apply {
                    put("enabled", original.enabled); put("frequency", original.frequency.name)
                    put("destination", original.destination.name); put("network", original.network.name)
                    put("start", original.startHour); put("end", original.endHour); put("month_day", original.monthDay)
                    put("last_auto", store.automaticSuccess(BackupDestination.DRIVE))
                }.toString())
                val hour = LocalTime.now().hour
                store.save(original.copy(enabled = true, frequency = BackupFrequency.DAILY,
                    destination = BackupDestination.DRIVE, network = BackupNetwork.WIFI,
                    startHour = hour, endHour = (hour + 1) % 24))
                // Independent power cases need a fresh eligibility cursor; successful archives remain untouched.
                check(store.preferences.edit().remove("automatic_success_DRIVE").putBoolean("power_audit", true).commit())
                AutomaticBackupJob.schedule(context, replace = true)
                println("Power audit armed; Wi-Fi-only job uses the current hourly window.")
            }
            "inspect" -> println("Power audit: enabled=${store.automation().enabled}, automaticSuccess=${store.automaticSuccess(BackupDestination.DRIVE)}, errorPresent=${store.error(BackupDestination.DRIVE) != null}")
            "restore" -> {
                check(snapshot.isFile) { "No power-audit options snapshot to restore." }
                val saved = JSONObject(snapshot.readText())
                store.save(BackupAutomation(saved.getBoolean("enabled"),
                    BackupFrequency.valueOf(saved.getString("frequency")),
                    BackupDestination.valueOf(saved.getString("destination")),
                    BackupNetwork.valueOf(saved.getString("network")), saved.getInt("start"),
                    saved.getInt("end"), saved.getInt("month_day")))
                val latest = maxOf(saved.getLong("last_auto"), store.automaticSuccess(BackupDestination.DRIVE))
                val edit = store.preferences.edit().remove("power_audit")
                if (latest == 0L) edit.remove("automatic_success_DRIVE") else edit.putLong("automatic_success_DRIVE", latest)
                check(edit.commit())
                AutomaticBackupJob.schedule(context, replace = true)
                check(snapshot.delete())
                println("Original automatic-backup options restored; successful archives retained.")
            }
            else -> error("Choose arm, inspect, or restore.")
        }
    }
}
