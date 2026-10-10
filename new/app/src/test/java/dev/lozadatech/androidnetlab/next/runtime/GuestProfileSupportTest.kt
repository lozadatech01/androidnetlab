package dev.lozadatech.androidnetlab.next.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestProfileSupportTest {
    @Test
    fun onlyBuildrootProfileCanBootWithTheShippedImages() {
        assertTrue(GuestProfileSupport.canBoot("Buildroot Minimal"))
        assertTrue(GuestProfileSupport.canBoot("buildroot minimal"))
        assertFalse(GuestProfileSupport.canBoot("Alpine Linux"))
        assertFalse(GuestProfileSupport.canBoot("Debian Linux"))
        assertFalse(GuestProfileSupport.canBoot("Custom ARM64 VM"))
        assertFalse(GuestProfileSupport.canBoot("My custom guest"))
    }

    @Test
    fun unsupportedProfilesExplainTheImageMismatch() {
        val message = GuestProfileSupport.unavailableReason("Alpine Linux")
        assertTrue(message.contains("Buildroot ARM64"))
        assertTrue(message.contains("not an installed guest OS"))
    }

    @Test
    fun supportedProfileIsRecognizedExplicitly() {
        assertTrue(
            GuestProfileSupport.unavailableReason("Buildroot Minimal")
                .contains("supported initial guest")
        )
    }
}
