package dev.lozadatech.androidnetlab.next.runtime

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class VmConfigTest {
    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return checkNotNull(outcome).getOrThrow()
    }

    @Test
    fun defaultsDescribeSmallAarch64VirtGuest() {
        val config = VmConfig(name = "Alpine Linux")

        assertEquals(128, config.memoryMiB)
        assertEquals(1, config.vcpus)
        assertEquals(2, config.diskGiB)
        assertEquals("aarch64", config.guestArchitecture)
        assertEquals("virt", config.machine)
    }

    @Test
    fun acceptsValuesAtSupportedResourceBoundaries() {
        VmConfig(name = "minimum", memoryMiB = 64, vcpus = 1, diskGiB = 1)
        VmConfig(name = "maximum", memoryMiB = 2048, vcpus = 4, diskGiB = 32)
    }

    @Test
    fun rejectsBlankNamesAndOutOfRangeResources() {
        assertThrows(IllegalArgumentException::class.java) { VmConfig(name = "  ") }
        assertThrows(IllegalArgumentException::class.java) { VmConfig(name = "low-memory", memoryMiB = 63) }
        assertThrows(IllegalArgumentException::class.java) { VmConfig(name = "high-memory", memoryMiB = 2049) }
        assertThrows(IllegalArgumentException::class.java) { VmConfig(name = "zero-cpu", vcpus = 0) }
        assertThrows(IllegalArgumentException::class.java) { VmConfig(name = "too-many-cpus", vcpus = 5) }
        assertThrows(IllegalArgumentException::class.java) { VmConfig(name = "small-disk", diskGiB = 0) }
        assertThrows(IllegalArgumentException::class.java) { VmConfig(name = "large-disk", diskGiB = 33) }
    }

    @Test
    fun rejectsGuestTargetsOutsideInitialRuntimeScope() {
        assertThrows(IllegalArgumentException::class.java) {
            VmConfig(name = "x86", guestArchitecture = "x86_64")
        }
        assertThrows(IllegalArgumentException::class.java) {
            VmConfig(name = "other-machine", machine = "pc")
        }
    }

    @Test
    fun unavailableRuntimeNeverPretendsToStartOrRunAGuest() {
        val runtime = UnavailableQemuRuntime()
        val config = VmConfig(name = "Test guest")

        assertEquals(VmState.UNAVAILABLE, runtime.availability().state)
        assertEquals(VmState.UNAVAILABLE, runSuspend { runtime.start(config) }.state)
        assertEquals(VmState.UNAVAILABLE, runSuspend { runtime.stop(config.name) }.state)
        assertEquals(VmState.UNAVAILABLE, runtime.status(config.name).state)
    }

    @Test
    fun profileNameValidationAcceptsTrimmedUniqueNames() {
        assertNull(validateVmProfileName("  Fedora Lab  ", listOf("Alpine Linux")))
    }

    @Test
    fun profileNameValidationRejectsBlankLongControlAndDuplicateNames() {
        assertEquals("Enter a profile name.", validateVmProfileName("   ", emptyList()))
        assertEquals("Keep the name under 40 characters.", validateVmProfileName("x".repeat(41), emptyList()))
        assertEquals(
            "Profile names cannot contain control characters or line breaks.",
            validateVmProfileName("Lab\nOne", emptyList())
        )
        assertEquals("That profile already exists.", validateVmProfileName("debian linux", listOf("Debian Linux")))
    }
}
