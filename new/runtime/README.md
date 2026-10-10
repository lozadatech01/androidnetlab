# Android QEMU runtime integration

## Current status

AndroidNetLab contains an **experimental single-guest launch path** for a Buildroot AArch64 guest. The app code includes:

- import and app-sandbox smoke testing for an Android ARM64 `qemu-system-aarch64` executable;
- validation and import for an AArch64 Linux kernel `Image` and ext2 `rootfs.ext2`;
- a headless QEMU command builder using TCG and the generic `virt` machine;
- a private writable rootfs copy per VM profile;
- serial-console output/input, a conservative userspace readiness check, boot timeout, and manual stop handling.

This code has **not yet been validated by booting the guest on a physical Android device**. An APK build, QEMU cross-build, or Linux-host guest boot is not proof that QEMU executes or boots successfully on Android.

Only **Buildroot Minimal** is eligible to boot with the current image pair. Alpine, Debian, and Custom ARM64 are configuration templates, not installed operating systems. The initial Android launch path attaches no virtual NIC; network-mode choices are stored as preferences only. The Multi screen is a layout preview, not a multi-VM runtime. Automatic VM startup is intentionally inactive.

## Device acceptance sequence

1. From the [QEMU Android build workflow](../../.github/workflows/qemu-android-probe.yml), download the successful `qemu-android-ndk-probe-evidence` artifact, extract `qemu-system-aarch64-android-arm64`, and keep the executable separate from its ZIP. A prior successful candidate build is available [here](https://github.com/lozadatech01/androidnetlab/actions/runs/38092721218).
2. Install the APK artifact from the [Android APK workflow](../../.github/workflows/android.yml).
3. In **VMs → Import / test Android ARM64 QEMU**, select the extracted executable. The app tests `--version`, `-machine help`, and `-accel help`. Save the output and SHA-256.
4. Run the [Linux guest image workflow](../../.github/workflows/linux-guest.yml) if its artifact is not available. From its `androidnetlab-linux-guest-aarch64` artifact, import `Image` and `rootfs.ext2` separately.
5. Select **Buildroot Minimal**, start one guest, and observe the serial console. The app should not report `RUNNING` until a Buildroot welcome banner or login prompt appears. The current boot-readiness timeout is 180 seconds.
6. Test `poweroff` in the guest, then restart it and confirm that per-profile root filesystem changes persist. A forced Stop VM is not a substitute for a clean guest shutdown and may leave filesystem changes incomplete.

Record the phone model, Android version/API, QEMU smoke-test result, console output, and observed shutdown/restart behavior. If any stage fails, preserve its exact diagnostic output rather than treating the preceding stage as evidence of success.

## Build evidence versus device evidence

- [Android APK workflow](../../.github/workflows/android.yml): unit tests and APK build.
- [Linux guest image workflow](../../.github/workflows/linux-guest.yml): builds and boots the Buildroot image under Linux-host QEMU. Its host measurements are not Android performance measurements.
- [Android QEMU cross-build workflow](../../.github/workflows/qemu-android-probe.yml): builds an Android ARM64 candidate. Cross-compilation alone does not prove app-sandbox execution, TCG guest execution, guest boot, or acceptable performance on a phone.

## Next engineering gates

1. Obtain a successful QEMU executable smoke-test result from the phone.
2. Prove TCG actually executes guest instructions under the phone's Android memory protections.
3. Boot Buildroot on-device and validate input/output, clean stop, restart, and persisted guest state.
4. Measure boot time, memory use, thermal behavior, and stability on the target phone.
5. Only after the single-guest path passes, implement actual networking and multiple concurrent VMs. On a 4 GiB phone, validate the smallest guest before considering more than one guest at a time.

Keep the runtime behind a narrow adapter. Do not replace QEMU with a custom engine unless measured limitations justify the additional engineering and maintenance cost.
