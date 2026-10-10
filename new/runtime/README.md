# Android QEMU runtime integration

No QEMU binary/library is bundled here yet. The app must report the runtime as unavailable until a real runtime can be initialized; never simulate a successful boot.

## Integration spike

1. Pin a QEMU revision and dependency versions.
2. Build only AArch64 system emulation for Android ARM64/Bionic.
3. Validate TCG translated-code execution under Android's memory protections.
4. Choose between JNI/native-library integration and an Android-compatible child process based on runtime tests.
5. Expose a small adapter for launch, serial output, state, and orderly stop.
6. Boot the existing Buildroot guest before implementing multi-VM UI.
