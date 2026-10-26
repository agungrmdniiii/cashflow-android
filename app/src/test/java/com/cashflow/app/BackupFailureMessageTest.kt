package com.cashflow.app

import com.cashflow.app.data.backup.BackupDestination
import com.cashflow.app.data.backup.DriveAuthorizationRequired
import com.cashflow.app.data.service.CloudBackupService
import com.cashflow.app.data.service.NotificationHelper
import org.junit.Assert.*
import org.junit.Test

class BackupFailureMessageTest {
    @Test fun failureMessagesExplainTheNextStepWithoutEchoingPrivateDetails() {
        val drive = BackupDestination.DRIVE
        assertTrue(NotificationHelper.backupFailureMessage(drive, DriveAuthorizationRequired(null)).contains("Hubungkan ulang"))
        assertTrue(NotificationHelper.backupFailureMessage(drive, CloudBackupService.RequestFailed(401)).contains("Hubungkan ulang"))
        assertTrue(NotificationHelper.backupFailureMessage(drive, CloudBackupService.RequestFailed(403)).contains("izin akun"))
        assertTrue(NotificationHelper.backupFailureMessage(drive, CloudBackupService.RequestFailed(503)).contains("Coba lagi nanti"))
        val privateDetails = "private-account-and-path"
        assertFalse(NotificationHelper.backupFailureMessage(drive, java.io.IOException(privateDetails)).contains(privateDetails))
        assertTrue(NotificationHelper.backupFailureMessage(BackupDestination.LOCAL, SecurityException(privateDetails)).contains("folder cadangan"))
    }
}
