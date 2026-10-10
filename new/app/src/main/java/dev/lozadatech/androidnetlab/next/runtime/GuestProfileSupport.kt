package dev.lozadatech.androidnetlab.next.runtime

/**
 * The initial runtime ships one guest image pair: a Buildroot AArch64 kernel
 * and ext2 root filesystem. Other profile names remain configuration templates,
 * not claims that their operating systems are installed.
 */
object GuestProfileSupport {
    const val INITIAL_SUPPORTED_PROFILE = "Buildroot Minimal"

    fun canBoot(profileName: String): Boolean =
        profileName.equals(INITIAL_SUPPORTED_PROFILE, ignoreCase = true)

    fun unavailableReason(profileName: String): String =
        if (canBoot(profileName)) {
            "$INITIAL_SUPPORTED_PROFILE is the supported initial guest."
        } else {
            "The current runtime only has a Buildroot ARM64 kernel and root filesystem. " +
                "'$profileName' is a saved configuration template, not an installed guest OS. " +
                "Select Buildroot Minimal or add matching OS-specific boot images before starting this profile."
        }
}
