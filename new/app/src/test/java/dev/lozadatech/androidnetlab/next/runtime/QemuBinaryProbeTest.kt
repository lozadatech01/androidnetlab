package dev.lozadatech.androidnetlab.next.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QemuBinaryProbeTest {
    @Test
    fun acceptsArm64LittleEndianElf64() {
        assertTrue(QemuBinaryProbe.isAarch64Elf(elfHeader(machine = 183)))
    }

    @Test
    fun rejectsWrongArchitectureAndElfVariants() {
        assertFalse(QemuBinaryProbe.isAarch64Elf(elfHeader(machine = 62))) // x86_64
        assertFalse(QemuBinaryProbe.isAarch64Elf(elfHeader(machine = 40))) // ARM32
        assertFalse(QemuBinaryProbe.isAarch64Elf(elfHeader(machine = 183, elfClass = 1)))
        assertFalse(QemuBinaryProbe.isAarch64Elf(elfHeader(machine = 183, dataEncoding = 2)))
        assertFalse(QemuBinaryProbe.isAarch64Elf(ByteArray(19)))
    }

    @Test
    fun rejectsNonElfInput() {
        val bytes = elfHeader(machine = 183)
        bytes[0] = 0
        assertFalse(QemuBinaryProbe.isAarch64Elf(bytes))
    }

    private fun elfHeader(
        machine: Int,
        elfClass: Int = 2,
        dataEncoding: Int = 1
    ): ByteArray = ByteArray(20).apply {
        this[0] = 0x7f.toByte()
        this[1] = 'E'.code.toByte()
        this[2] = 'L'.code.toByte()
        this[3] = 'F'.code.toByte()
        this[4] = elfClass.toByte()
        this[5] = dataEncoding.toByte()
        this[18] = (machine and 0xff).toByte()
        this[19] = ((machine ushr 8) and 0xff).toByte()
    }
}
