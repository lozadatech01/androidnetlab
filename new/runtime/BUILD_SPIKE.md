# Android QEMU Build Spike

**Status:** Preparation only. No Android-compatible QEMU binary has been built or run yet.

## Goal

Find the smallest reproducible path to run upstream QEMU system emulation inside AndroidNetLab on an ARM64 Android device. This spike is a gate: do not build multi-VM management or networking UI until it passes.

## Important platform distinction

QEMU officially tests a defined set of host platforms; Android is not listed as a supported host OS in its build-platform documentation. The Android NDK can cross-compile native code for the `aarch64-linux-android` ABI, but that alone does not prove the full QEMU host build works with Android's Bionic libc, linker, process model, or executable-memory policy.

Therefore, use a staged investigation rather than assuming a normal Linux build will work unchanged:

1. **Pin upstream source.** Select a maintained upstream QEMU release and record the exact tag/commit, source archive checksum, NDK version, SDK/API level, host tool versions, and license notices.
2. **Map host build assumptions.** Identify the required QEMU host tools and libraries, Linux-only APIs, signal/memory-mapping assumptions, and optional dependencies. Disable everything not needed for headless AArch64 system emulation.
3. **Choose a packaging experiment.** Test a JNI-linked native library and an app-private executable only where Android's linker and execution policy permit it. Do not assume either route works before testing on-device.
4. **Compile a minimal artifact.** Target only `aarch64-softmmu`, TCG, serial console, generic `virt` board, and only the block/network backends needed for the first guest. Keep build logs and the exact configure options.
5. **Run a native smoke test.** On Android, report ABI, process start result, exit status, and QEMU version. A Linux CI build is not evidence of Android execution.
6. **Run a TCG smoke test.** Execute a tiny AArch64 system-emulation workload and prove translated guest code runs under Android memory protections.
7. **Boot one real guest.** Use the existing Buildroot guest artifacts; capture serial output and verify a clear boot-ready marker and clean shutdown.

## Automated Android NDK probe

Workflow: `.github/workflows/qemu-android-probe.yml`.

Run it manually from the repository's **Actions** tab or let it run when runtime-spike files change. It pins Android NDK r27c (revision `27.2.12479018`) and QEMU 9.2.0, first proves the NDK emits an AArch64 Android ELF, then captures QEMU's configure diagnostics for that toolchain. The artifact is named `qemu-android-ndk-probe-evidence`.

This workflow is deliberately diagnostic: a green workflow means the probe ran and preserved evidence, **not** that QEMU configured or built. Check `qemu-android-probe-result.txt`, `qemu-android-configure-exit-code.txt`, and any build result/log. A configure failure is an actionable compatibility/dependency blocker, not a runtime pass. Even a build pass only produces a candidate artifact; Android device execution, TCG JIT behavior, guest boot, stop/restart, and performance remain separate gates.

The probe initially uses runner-provided libraries to expose the first upstream configure/build barrier. If QEMU rejects the target or requires Android-built dependencies/patches, record that exact failure and extend the experiment rather than packaging a Linux-host binary or changing the UI to imply a VM can boot.

## Required evidence

The spike is considered successful only when all of these are recorded:

- Exact QEMU source revision and reproducible build command.
- Native artifact ABI and dependencies.
- Actual Android device/OS/API used for runtime validation.
- QEMU starts, executes guest code, and exits without crashing.
- One real Linux guest reaches its boot-ready marker through serial.
- Shutdown and restart work without leaving orphaned processes or corrupting writable state.
- Known limitations and measured CPU/RSS/boot-time numbers.

Linux-host QEMU tests can validate guest images and virtual-network concepts, but must be labeled separately from Android runtime evidence.

## Fallbacks if upstream build is blocked

Try, in order:

1. Reduce the enabled QEMU features and optional dependencies.
2. Patch only the specific Android/Bionic incompatibility, keeping patches small and documented.
3. Compare the relevant integration/build ideas from Limbo and Android's QEMU build scripts, while avoiding blindly importing old forks or toolchains.
4. Re-evaluate the integration form (JNI library versus executable) with a minimal proof of concept.
5. If a blocker requires a maintained Android-specific fork, document the exact blocker and maintenance cost before adopting it.

Do not start a from-scratch VM engine because the first cross-build is difficult. A custom engine is a later performance/feature decision based on measurements, not the default response to build friction.

## References

- QEMU supported build platforms: https://www.qemu.org/docs/master/about/build-platforms.html
- QEMU build system: https://www.qemu.org/docs/master/devel/build-system
- Android NDK with other build systems: https://developer.android.com/ndk/guides/other_build_systems
- QEMU ARM system emulator: https://www.qemu.org/docs/master/system/target-arm.html
- Android QEMU build script (reference only): https://android.googlesource.com/platform/external/qemu/+/refs/heads/studio-master-dev/android/scripts/build-qemu-android.sh
- Limbo native build reference: https://github.com/limboemu/limbo/blob/master/Developers.md
