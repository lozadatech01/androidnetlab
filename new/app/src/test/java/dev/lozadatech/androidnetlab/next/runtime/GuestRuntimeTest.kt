package dev.lozadatech.androidnetlab.next.runtime

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class GuestArtifactStoreTest {
    @Test
    fun recognizesOnlyExpectedBuildrootArtifactMagic() {
        val dir = Files.createTempDirectory("guest-artifacts").toFile()
        try {
            val kernel = File(dir, "Image").apply { writeBytes(kernelImage()) }
            val rootfs = File(dir, "rootfs.ext2").apply { writeBytes(ext2Image()) }
            val wrongKernel = File(dir, "wrong-Image").apply { writeBytes(ByteArray(80)) }
            val wrongRootfs = File(dir, "wrong-rootfs.ext2").apply { writeBytes(ByteArray(2048)) }

            assertTrue(GuestArtifactStore.isAarch64KernelImage(kernel))
            assertTrue(GuestArtifactStore.isExt2Filesystem(rootfs))
            assertFalse(GuestArtifactStore.isAarch64KernelImage(wrongKernel))
            assertFalse(GuestArtifactStore.isExt2Filesystem(wrongRootfs))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun importsOnlyValidatedGuestArtifacts() {
        val dir = Files.createTempDirectory("guest-artifact-import").toFile()
        try {
            val kernel = QemuArtifactFixtures.kernelImage()
            val installedKernel = GuestArtifactStore.installKernel(ByteArrayInputStream(kernel), dir)
            assertTrue(GuestArtifactStore.isAarch64KernelImage(installedKernel))

            val rootfs = QemuArtifactFixtures.ext2Image()
            val installedRootfs = GuestArtifactStore.installRootfs(ByteArrayInputStream(rootfs), dir)
            assertTrue(GuestArtifactStore.isExt2Filesystem(installedRootfs))

            assertThrows(IOException::class.java) {
                GuestArtifactStore.installRootfs(ByteArrayInputStream(ByteArray(2048)), dir)
            }
            assertTrue(GuestArtifactStore.isExt2Filesystem(installedRootfs))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun keepsPerProfileWritableRootfsCopyAcrossRestarts() {
        val dir = Files.createTempDirectory("guest-rootfs-copy").toFile()
        try {
            val base = File(dir, "base.ext2").apply { writeBytes(ext2Image()) }
            val vmDir = File(dir, "vm")
            val first = GuestArtifactStore.rootfsForProfile(base, vmDir, 100L * 1024L * 1024L)
            assertEquals(ext2Image().toList(), first.readBytes().toList())

            first.appendBytes(byteArrayOf(1, 2, 3))
            base.writeBytes(ext2Image() + byteArrayOf(4, 5))
            val second = GuestArtifactStore.rootfsForProfile(base, vmDir, 100L * 1024L * 1024L)
            assertEquals(first.length(), second.length())
            assertTrue(second.readBytes().takeLast(3) == listOf<Byte>(1, 2, 3))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun kernelImage(): ByteArray = QemuArtifactFixtures.kernelImage()
    private fun ext2Image(): ByteArray = QemuArtifactFixtures.ext2Image()
}

class QemuCommandBuilderTest {
    @Test
    fun buildsHeadlessTcGCommandWithSeparateRootAndDataDisk() {
        val dir = Files.createTempDirectory("qemu launch path with spaces").toFile()
        try {
            val binary = File(dir, "qemu-system-aarch64").apply {
                writeText("candidate")
                setExecutable(true)
            }
            val kernel = File(dir, "kernel Image").apply { writeBytes(QemuArtifactFixtures.kernelImage()) }
            val rootfs = File(dir, "guest rootfs.ext2").apply { writeBytes(QemuArtifactFixtures.ext2Image()) }
            val dataDisk = File(dir, "profile disk.img").apply { writeText("disk") }

            val command = QemuCommandBuilder.build(
                QemuCommandBuilder.Config(binary, kernel, rootfs, dataDisk, 256, 1)
            )

            assertEquals(binary.absolutePath, command.first())
            assertTrue(command.windowed(2).any { it[0] == "-accel" && it[1] == "tcg" })
            assertTrue(command.windowed(2).any { it[0] == "-m" && it[1] == "256M" })
            assertTrue(command.windowed(2).any { it[0] == "-smp" && it[1] == "1" })
            assertEquals(2, command.windowed(2).count { it[0] == "-drive" })
            assertTrue(command.contains("file=${rootfs.absolutePath},if=virtio,format=raw"))
            assertTrue(command.contains("file=${dataDisk.absolutePath},if=virtio,format=raw"))
            assertFalse(command.contains("-netdev"))
            assertTrue(command.contains("root=/dev/vda rw console=ttyAMA0"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidMemoryCpuAndMissingArtifacts() {
        val dir = Files.createTempDirectory("qemu-launch-invalid").toFile()
        try {
            val binary = File(dir, "qemu-system-aarch64").apply {
                writeText("candidate")
                setExecutable(true)
            }
            val kernel = File(dir, "Image").apply { writeBytes(QemuArtifactFixtures.kernelImage()) }
            val rootfs = File(dir, "rootfs.ext2").apply { writeBytes(QemuArtifactFixtures.ext2Image()) }
            assertThrows(IllegalArgumentException::class.java) {
                QemuCommandBuilder.build(QemuCommandBuilder.Config(binary, kernel, rootfs, null, 63, 1))
            }
            assertThrows(IllegalArgumentException::class.java) {
                QemuCommandBuilder.build(QemuCommandBuilder.Config(binary, kernel, rootfs, null, 256, 5))
            }
            assertThrows(IllegalArgumentException::class.java) {
                QemuCommandBuilder.build(QemuCommandBuilder.Config(binary, File(dir, "missing"), rootfs, null, 256, 1))
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}

private object QemuArtifactFixtures {
    fun kernelImage(): ByteArray = ByteArray(64).apply {
        this[56] = 0x41
        this[57] = 0x52
        this[58] = 0x4d
        this[59] = 0x64
    }

    fun ext2Image(): ByteArray = ByteArray(2048).apply {
        this[1080] = 0x53
        this[1081] = 0xef.toByte()
    }
}
