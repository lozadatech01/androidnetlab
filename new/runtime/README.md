# Android QEMU runtime integration

A QEMU 9.2.0 Android ARM64 executable has been cross-built in CI as a **candidate artifact**, but it is not bundled in the APK and has not been run on a phone. The app must report the runtime as unavailable until the candidate is validated and connected to real launch, console, readiness, and shutdown behavior; never simulate a successful boot.

## Current next step

Follow [BUILD_SPIKE.md](BUILD_SPIKE.md) for the acceptance criteria. The [Android NDK compatibility probe](../../.github/workflows/qemu-android-probe.yml) now cross-builds a candidate for Android API 28 / ARM64 using NDK r27c and QEMU 9.2.0. The next gate is to install the APK, use the **VMs → Import & test QEMU ARM64 binary** action with the candidate from [the successful QEMU build artifact](https://github.com/lozadatech01/androidnetlab/actions/runs/38092721218), and verify `--version`, `-machine help`, and `-accel help` in the app sandbox. After that, test translated guest execution via TCG and boot the existing Buildroot guest. CI build success alone is not runtime proof.

## Integration principles

1. Pin an upstream QEMU revision and all relevant build-tool/dependency versions.
2. Start with AArch64 system emulation for an Android ARM64 host, TCG, a headless generic `virt` guest, and serial output.
3. Validate TCG translated-code execution under Android's memory protections on an actual Android device.
4. Choose between JNI/native-library integration and an Android-compatible child process based on evidence.
5. Expose a narrow adapter for launch, serial output, lifecycle state, and orderly stop; keep QEMU internals out of UI code.
6. Boot the existing Buildroot guest before implementing multi-VM UI.
7. Keep Linux-host CI results separate from Android runtime claims.

A future custom VM engine remains an option only after profiling identifies a concrete performance or capability bottleneck. Avoid implementing a replacement before the QEMU baseline is measured.
