# AndroidNetLab

An Android-first lightweight Linux networking lab, using QEMU system emulation as the intended VM engine.

## Project layout

- `new/` — clean next-generation Android app and QEMU-first runtime integration work.
- `old/prototype-0.1/` — preserved original Android app and original Gradle project files.
- `vm/` — existing minimal AArch64 guest build and Linux-host functional test scripts.
- `docs/` — architecture, Android runtime research, and virtual network design.
- `.github/workflows/` — CI for the new Android app and guest functional tests.

## Current status

The new app is a clean foundation, not yet a working VM runtime. Its UI explicitly reports that QEMU is not integrated. The first runtime milestone is to build an Android-compatible QEMU for ARM64 and boot one real Linux guest on Android. Linux-host QEMU tests validate guest/test logic but do not prove Android compatibility.

## Build

The Android CI workflow builds the new project from `new/` and publishes its debug APK as an artifact. The old prototype remains in the repository for reference and is not the main app build.

## Engineering docs

- [QEMU research and build order](docs/QEMU_RESEARCH_AND_BUILD_ORDER.md) — what QEMU provides, what must be adapted for Android, and the recommended implementation sequence.
- [QEMU Android implementation plan](docs/QEMU_ANDROID_IMPLEMENTATION_PLAN.md)
- [Android runtime research](docs/ANDROID_RUNTIME_RESEARCH.md)
- [VM sockets and virtual networks](docs/VM_SOCKETS_AND_VIRTUAL_NETWORKS.md)
