package dev.lozadatech.androidnetlab.next.runtime

/** Conservative serial-console readiness detection for the first Buildroot guest. */
object GuestBootReadiness {
    /**
     * A kernel boot line alone is not enough. Wait for the userspace welcome
     * banner or a login prompt before reporting RUNNING to the UI.
     */
    fun isReadyMarker(line: String): Boolean =
        line.contains("Welcome to Buildroot", ignoreCase = true) ||
            line.contains("buildroot login:", ignoreCase = true) ||
            Regex("login:\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(line)
}
