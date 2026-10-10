# Android QEMU runtime integration

No QEMU binary/library is bundled here yet. The app must report the runtime as unavailable until a real runtime can be initialized; never simulate a successful boot.

## Current next step

Follow [BUILD_SPIKE.md](BUILD_SPIKE.md) for the Android compatibility investigation and its acceptance criteria. This is intentionally a gated proof of concept, not a promise that upstream QEMU will compile unchanged for Android.

## Integration principles

1. Pin an upstream QEMU revision and all relevant build-tool/dependency versions.
2. Start with AArch64 system emulation for an Android ARM64 host, TCG, a headless generic `virt` guest, and serial output.
3. Validate TCG translated-code execution under Android's memory protections on an actual Android device.
4. Choose between JNI/native-library integration and an Android-compatible child process based on evidence.
5. Expose a narrow adapter for launch, serial output, lifecycle state, and orderly stop; keep QEMU internals out of UI code.
6. Boot the existing Buildroot guest before implementing multi-VM UI.
7. Keep Linux-host CI results separate from Android runtime claims.

A future custom VM engine remains an option only after profiling identifies a concrete performance or capability bottleneck. Avoid implementing a replacement before the QEMU baseline is measured.
