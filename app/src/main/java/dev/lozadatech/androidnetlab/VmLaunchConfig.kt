package dev.lozadatech.androidnetlab

/**
 * Validated resource profile for a future Android-compatible system-emulation backend.
 *
 * This deliberately does not claim to start a VM: AndroidNetLab does not yet bundle a
 * QEMU system-emulation engine. Keeping configuration/argument construction separate
 * makes it testable before the native runtime is integrated.
 */
data class VmLaunchConfig(
    val memoryMiB: Int = DEFAULT_MEMORY_MIB,
    val cpuCount: Int = DEFAULT_CPU_COUNT,
    val headless: Boolean = true
) {
    init {
        require(memoryMiB in MIN_MEMORY_MIB..MAX_MEMORY_MIB) {
            "Guest RAM must be between $MIN_MEMORY_MIB and $MAX_MEMORY_MIB MiB."
        }
        require(cpuCount in 1..2) { "This low-memory profile supports 1–2 vCPUs." }
    }

    fun summary(): String =
        "$memoryMiB MiB guest RAM · $cpuCount vCPU${if (cpuCount == 1) "" else "s"} · " +
            if (headless) "headless" else "display enabled"

    companion object {
        const val DEFAULT_MEMORY_MIB = 256
        const val MIN_MEMORY_MIB = 128
        const val MAX_MEMORY_MIB = 1024
        const val DEFAULT_CPU_COUNT = 1
        val PRESETS_MIB = listOf(128, 192, 256, 384, 512)
    }
}
