package dev.lozadatech.androidnetlab.next.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestBootReadinessTest {
    @Test
    fun acceptsUserspaceWelcomeAndLoginPrompts() {
        assertTrue(GuestBootReadiness.isReadyMarker("Welcome to Buildroot 2025.02.2"))
        assertTrue(GuestBootReadiness.isReadyMarker("buildroot login:"))
        assertTrue(GuestBootReadiness.isReadyMarker("login:"))
        assertTrue(GuestBootReadiness.isReadyMarker("LOGIN:"))
    }

    @Test
    fun doesNotTreatKernelStartupAsGuestReady() {
        assertFalse(GuestBootReadiness.isReadyMarker("Booting Linux on physical CPU 0x0"))
        assertFalse(GuestBootReadiness.isReadyMarker("Run /sbin/init as init process"))
        assertFalse(GuestBootReadiness.isReadyMarker("random login: text in middle"))
        assertFalse(GuestBootReadiness.isReadyMarker(""))
    }
}
