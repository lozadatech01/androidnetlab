# Android QEMU Build Spike

**Status:** An Android ARM64 QEMU candidate has been cross-built in CI. It has **not** been executed on an Android device, TCG guest code has not been validated under Android's memory protections, and no guest boot is claimed.

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

Run it manually from the repository's **Actions** tab or let it run when runtime-spike files change. It pins Android NDK r27c (revision `27.2.12479018`) and upstream QEMU 9.2.0 for Android API 28 / `arm64-v8a`. The workflow cross-builds Android-target dependencies (libffi, zlib, PCRE2, GLib/GObject, and Pixman), applies one narrowly scoped Bionic compatibility patch (disables QEMU's optional POSIX shared-memory backend), configures only `aarch64-softmmu` with TCG, and builds the candidate. Evidence and the candidate executable are uploaded as `qemu-android-ndk-probe-evidence`. The current published candidate is about 34.7 MB after removing debug sections.

A successful configure/build is a **cross-build result only**. Inspect `qemu-android-probe-result.txt`, `qemu-android-build-result.txt`, the ELF report, and build logs. A green diagnostic job by itself only means evidence was captured: read the result files. The stripped candidate has not yet been executed on a real Android device; TCG JIT behavior, guest boot, stop/restart, app-sandbox execution, and performance remain separate gates.

### App-sandbox smoke test

1. Open [the successful QEMU build run](https://github.com/lozadatech01/androidnetlab/actions/runs/38092721218) and download the `qemu-android-ndk-probe-evidence` artifact.
2. Extract `qemu-system-aarch64-android-arm64` from the ZIP. Do not select the ZIP or the unstripped build.
3. Install the Android APK from the Android workflow artifact, open **VMs**, and choose **Import & test QEMU ARM64 binary**.
4. Select the extracted executable. The app copies it to private storage and runs only `--version`, `-machine help`, and `-accel help`. It displays the exact error if Android refuses to execute it.
5. Save the result, Android version, device model/ABI, and app version before proceeding to guest boot.

A passing app-sandbox smoke test only proves the executable starts and advertises the expected QEMU machine/TCG backend. It does not prove translated guest instructions execute or that a guest boots. A failed execution can indicate that Android prohibits executing a writable app-private file; in that case, test a native-library/JNI packaging route instead of weakening security controls.

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
