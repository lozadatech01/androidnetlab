package dev.lozadatech.androidnetlab.next.runtime

import java.io.File

/** Creates argument-safe QEMU commands for the first headless Buildroot guest. */
object QemuCommandBuilder {
    data class Config(
        val executable: File,
        val kernelImage: File,
        val rootfsImage: File,
        val dataDisk: File?,
        val memoryMiB: Int,
        val vcpus: Int
    )

    fun build(config: Config): List<String> {
        require(config.executable.isFile) { "QEMU executable is not installed." }
        require(config.executable.canExecute()) { "QEMU executable is not marked executable." }
        require(config.kernelImage.isFile && GuestArtifactStore.isAarch64KernelImage(config.kernelImage)) {
            "A valid AArch64 kernel Image is required."
        }
        require(config.rootfsImage.isFile && GuestArtifactStore.isExt2Filesystem(config.rootfsImage)) {
            "A valid ext2 guest root filesystem is required."
        }
        require(config.dataDisk == null || config.dataDisk.isFile) { "Configured data disk does not exist." }
        require(config.memoryMiB in 64..2048) { "Memory must be between 64 and 2048 MiB." }
        require(config.vcpus in 1..4) { "vCPU count must be between 1 and 4." }

        return buildList {
            add(config.executable.absolutePath)
            addAll(listOf("-machine", "virt"))
            addAll(listOf("-cpu", "cortex-a57"))
            addAll(listOf("-accel", "tcg"))
            addAll(listOf("-m", "${config.memoryMiB}M"))
            addAll(listOf("-smp", config.vcpus.toString()))
            addAll(listOf("-display", "none"))
            addAll(listOf("-monitor", "none"))
            addAll(listOf("-serial", "stdio"))
            add("-no-reboot")
            addAll(listOf("-kernel", config.kernelImage.absolutePath))
            addAll(listOf("-drive", "file=${config.rootfsImage.absolutePath},if=virtio,format=raw"))
            // The UI-created sparse disk is a separate data disk; the imported
            // Buildroot rootfs remains the guest's boot/root volume (/dev/vda).
            config.dataDisk?.let {
                addAll(listOf("-drive", "file=${it.absolutePath},if=virtio,format=raw"))
            }
            addAll(listOf("-append", "root=/dev/vda rw console=ttyAMA0"))
        }
    }
}
