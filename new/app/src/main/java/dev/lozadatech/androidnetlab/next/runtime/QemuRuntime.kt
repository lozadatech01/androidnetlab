package dev.lozadatech.androidnetlab.next.runtime

/** A validated, deliberately small first-guest configuration. */
data class VmConfig(
    val name: String,
    val memoryMiB: Int = 128,
    val vcpus: Int = 1,
    val diskGiB: Int = 2,
    val guestArchitecture: String = "aarch64",
    val machine: String = "virt"
) {
    init {
        require(name.isNotBlank()) { "VM name must not be blank" }
        require(memoryMiB in 64..2048) { "Memory must be between 64 and 2048 MiB" }
        require(vcpus in 1..4) { "vCPU count must be between 1 and 4" }
        require(diskGiB in 1..32) { "Disk size must be between 1 and 32 GiB" }
        require(guestArchitecture == "aarch64") { "The initial runtime supports AArch64 guests only" }
        require(machine == "virt") { "The initial runtime supports the QEMU virt machine only" }
    }
}

enum class VmState {
    UNAVAILABLE, STOPPED, STARTING, RUNNING, STOPPING, FAILED
}

data class VmRuntimeStatus(
    val state: VmState,
    val detail: String
)

/**
 * Stable seam between the Android UI/VM manager and the QEMU implementation.
 * Implementations must not return RUNNING until the guest has reached a
 * documented readiness signal. Keep blocking process and I/O work off the main thread.
 */
interface QemuRuntime {
    fun availability(): VmRuntimeStatus
    suspend fun start(config: VmConfig): VmRuntimeStatus
    suspend fun stop(vmName: String): VmRuntimeStatus
    fun status(vmName: String): VmRuntimeStatus
}

/** Honest placeholder until an Android ARM64 QEMU build has passed device tests. */
class UnavailableQemuRuntime : QemuRuntime {
    override fun availability() = VmRuntimeStatus(
        VmState.UNAVAILABLE,
        "Android QEMU runtime has not been built and validated on this device yet."
    )

    override suspend fun start(config: VmConfig) = availability()

    override suspend fun stop(vmName: String) = VmRuntimeStatus(
        VmState.UNAVAILABLE,
        "Cannot stop '$vmName': no QEMU runtime is connected."
    )

    override fun status(vmName: String) = VmRuntimeStatus(
        VmState.UNAVAILABLE,
        "No runtime status is available for '$vmName'."
    )
}
