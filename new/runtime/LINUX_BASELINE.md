# QEMU Linux-host baseline CI

Workflow: `.github/workflows/qemu-linux-baseline.yml`

This workflow downloads a pinned upstream QEMU release (9.2.0), configures only the AArch64 system-emulation target, builds it on Ubuntu, and checks that the generic `virt` machine is present. It uploads the configure log and machine list for inspection.

## What this proves

- The pinned upstream source can be configured and built as a minimal Linux-host AArch64 system emulator in CI.
- The expected `qemu-system-aarch64` binary and generic `virt` machine are present.

## What this does not prove

- It is **not an Android build**. The resulting executable is for the GitHub Actions Linux host and must never be packaged into the Android APK.
- It does not prove QEMU can run under Android/Bionic, that TCG JIT works under Android memory protections, or that a guest boots on the phone.
- It does not prove multi-VM networking or performance on the target device.

## Why this baseline exists

Keep upstream source/configuration failures distinct from Android host-porting failures. The next spike must investigate the Android NDK/Bionic host build and validate the resulting artifact on an actual Android device. Android's QEMU-derived emulator code is a useful build-system reference, but it has Android-specific changes and targets host platforms for running the Android Emulator; it is not itself proof of a guest VM engine running inside an ordinary Android app.

References:
- Upstream QEMU source downloads: https://www.qemu.org/download/
- Android QEMU build script reference: https://android.googlesource.com/platform/external/qemu/+/refs/heads/studio-master-dev/android/scripts/build-qemu-android.sh
