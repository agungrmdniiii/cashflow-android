package com.cashflow.app

import com.cashflow.app.data.backup.BackupArchive
import com.cashflow.app.data.service.CloudBackupService
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BackupArchiveTest {
    private fun inDirectory(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("backup-archive-test").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    @Test fun multipleAuthenticatedBlocksRoundTripAndEveryArchiveUsesFreshRandomness() = inDirectory { dir ->
        val input = File(dir, "input.zip").apply { writeBytes(ByteArray(180_000) { (it % 251).toByte() }) }
        val first = File(dir, "first.duitaing")
        val second = File(dir, "second.duitaing")
        BackupArchive.encrypt(input, first, "kata-sandi-uji")
        BackupArchive.encrypt(input, second, "kata-sandi-uji")
        assertFalse(first.readBytes().contentEquals(second.readBytes()))
        val output = File(dir, "output.zip")
        BackupArchive.decrypt(first, output, "kata-sandi-uji")
        assertArrayEquals(input.readBytes(), output.readBytes())
        assertEquals(BackupArchive.Kind.ENCRYPTED_ZIP, BackupArchive.kind(first))
    }

    @Test fun wrongPasswordTamperingTruncationAndTrailingBytesNeverLeavePartialPlaintext() = inDirectory { dir ->
        val input = File(dir, "input").apply { writeBytes(ByteArray(150_000) { 42 }) }
        val archive = File(dir, "backup.duitaing")
        BackupArchive.encrypt(input, archive, "kata-sandi-uji")
        val original = archive.readBytes()
        for (variant in listOf(original, original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() },
            original.copyOf(original.size - 20), original + byteArrayOf(1))) {
            val corrupted = File(dir, "corrupt").apply { writeBytes(variant) }
            val output = File(dir, "partial.zip")
            try { BackupArchive.decrypt(corrupted, output, if (variant === original) "password-salah" else "kata-sandi-uji"); fail("Must reject unauthenticated archive") }
            catch (_: Exception) { assertFalse("Partial plaintext must be removed", output.exists()) }
        }
        assertArrayEquals(ByteArray(150_000) { 42 }, input.readBytes())
    }

    @Test fun movingBlocksOrChangingDeclaredSizeCannotProduceAValidArchive() = inDirectory { dir ->
        val input = File(dir, "input").apply { writeBytes(ByteArray(150_000) { (it % 173).toByte() }) }
        val archive = File(dir, "backup.duitaing")
        BackupArchive.encrypt(input, archive, "kata-sandi-uji")
        val original = archive.readBytes()
        val header = "DUITAING_ZIP_V1".length + 16 + 8 + 8
        val block = 65536 + 16
        val reordered = original.copyOf().apply {
            original.copyInto(this, header, header + block, header + 2 * block)
            original.copyInto(this, header + block, header, header + block)
        }
        val changedHeader = original.copyOf().apply { this[header - 1] = (this[header - 1].toInt() xor 1).toByte() }
        for (bytes in listOf(reordered, changedHeader)) {
            val corrupted = File(dir, "corrupt").apply { writeBytes(bytes) }
            val output = File(dir, "partial")
            try { BackupArchive.decrypt(corrupted, output, "kata-sandi-uji"); fail("Must reject moved blocks/header") }
            catch (_: Exception) { assertFalse(output.exists()) }
        }
    }

    @Test fun olderFormatsAreDetectedFromTheirContentsRatherThanTheirFileNames() = inDirectory { dir ->
        val file = File(dir, "no-extension")
        for ((bytes, kind) in listOf(
            "\uFEFF  {\"schema_version\":11}".toByteArray() to BackupArchive.Kind.JSON,
            byteArrayOf(80, 75, 3, 4) to BackupArchive.Kind.ZIP,
            CloudBackupService.encryptWithPassword("{}".toByteArray(), "lama") to BackupArchive.Kind.ENCRYPTED_JSON
        )) { file.writeBytes(bytes); assertEquals(kind, BackupArchive.kind(file)) }
        file.writeText("bukan cadangan")
        try { BackupArchive.kind(file); fail("Unknown contents must not be accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun anExistingDestinationIsNeverTruncatedOnAnInvalidOperation() = inDirectory { dir ->
        val source = File(dir, "source").apply { writeText("source") }
        val destination = File(dir, "existing").apply { writeText("backup sebelumnya") }
        try { BackupArchive.encrypt(source, destination, "kata-sandi-uji"); fail("Must protect existing destination") }
        catch (_: IllegalArgumentException) { assertEquals("backup sebelumnya", destination.readText()) }
        try { BackupArchive.encrypt(source, source, "kata-sandi-uji"); fail("Must not replace its own input") }
        catch (_: IllegalArgumentException) { assertEquals("source", source.readText()) }
    }
}
