# AndroidNetLab: QEMU-first implementation plan

**Status:** Engineering direction and staged plan; Android runtime is not yet implemented.
**Primary goal:** Run multiple independent, lightweight Linux VMs on ordinary ARM64 Android devices, then connect them through isolated virtual Ethernet networks.

## Latest CI compatibility finding (QEMU 9.2.0 / Android NDK r27c)

The repeatable probe has now successfully built the Android-target dependency set (libffi, zlib, PCRE2, GLib/GObject, and Pixman) and passed QEMU's configure step when those target libraries are used. The first QEMU compile then exposed `shm_open`/`shm_unlink` in the optional `backends/hostmem-shm.c` backend. Android Bionic intentionally does not provide these functions, so the current probe removes that one optional source file from the QEMU build graph. The initial AndroidNetLab guest profile does not need this shared-memory backend; it uses regular guest RAM and a file-backed disk. The patched compile is being validated separately.

A green probe workflow is not by itself a runtime pass. The artifact/result files must confirm whether the binary was produced, and native execution, TCG guest-code execution, guest boot, and shutdown/restart still require device validation.

## Core decision

Build AndroidNetLab around QEMU's **system emulation** rather than writing a VM engine from scratch. Reuse QEMU's CPU translation, virtual board/device models, block layer, serial devices, and network backends wherever they work on Android. Write AndroidNetLab-specific code for VM lifecycle, Android integration, configuration, resource admission, persistent per-VM storage, and virtual network topology.

Do not treat a successful Linux-host QEMU test as proof that the Android runtime works.

## How existing QEMU-based Android apps inform us

The open-source Limbo project demonstrates that a QEMU-based Android application can package architecture-specific emulator builds and connect an Android UI/VM manager to native QEMU code. Its repository includes a shared Android library and JNI/native integration. This is a useful architectural reference, not a runtime we should blindly copy: parts of its documented implementation use old QEMU versions and legacy dependencies. Review the current source, version, build instructions, and license before reusing any code.

Reference: https://github.com/limboemu/limbo

## Proposed layers

1. **Kotlin Android app**
   - VM list and details, launch profiles, console, network patch panel, diagnostics.
   - Explicit states: Stopped, Starting, Running, Stopping, Failed.
   - Show Running only after the guest's serial boot/readiness signal, not merely after launching a process.

2. **VM manager**
   - Validates configuration and artifacts before launch.
   - Creates unique VM IDs, MAC addresses, ports/socket paths, writable disk state, and log destinations.
   - Starts and stops VMs asynchronously; handles cancellation, crashes, cleanup, and app/process lifecycle.
   - Applies memory/CPU admission limits based on measured runtime overhead and a safety reserve.

3. **QEMU Android adapter**
   - First prove a minimal ARM64 Android-compatible `qemu-system-aarch64` build.
   - Compare two launch/integration forms in a small proof of concept:
     - QEMU built as a native library and invoked through JNI.
     - QEMU built as an Android-compatible executable and launched as an app-owned child process, if Android execution policy and the QEMU build permit it.
   - Choose based on TCG JIT compatibility, reliable shutdown, console/file-descriptor plumbing, signal handling, debugging, and packaging. Do not assume either form works until tested on Android.
   - Keep a narrow adapter API so Kotlin does not depend on QEMU internals.

4. **Guest artifact and disk layer**
   - Start with one supported machine profile: ARM64 host, AArch64 guest, QEMU generic `virt` board, headless serial console.
   - Keep the kernel and base root filesystem immutable/shared where practical; give each VM independent writable state (for example, a QEMU-supported backing image/overlay arrangement).
   - Stream disk I/O; avoid loading entire disk images into RAM.
   - Version and validate guest artifacts and record their hashes/metadata.

5. **Virtual network manager**
   - Model a NIC as a guest-facing Ethernet endpoint; model links/switches as separate objects managed by AndroidNetLab.
   - First support a two-VM point-to-point socket link, using QEMU's socket backend if the Android build supports it.
   - Then test a shared LAN for three or more VMs. Evaluate QEMU datagram/multicast support against a small userspace virtual switch; select only after packet-delivery and resource tests.
   - Keep lab traffic isolated by default. Do not require root, TAP, or bridging to the phone's physical network for the initial milestones.
   - Use QEMU/backend packet paths rather than forwarding frames through the UI thread. Bound all capture/log buffers.

## Android-specific constraints to validate early

- ABI and native dependency compatibility with Android ARM64/Bionic.
- QEMU TCG JIT executable-memory behavior under Android's memory protections and SELinux policy.
- Ability to start, monitor, signal, and clean up the runtime within the app sandbox.
- Foreground-service and background-execution behavior for long-running VMs on modern Android.
- App-private storage capacity, disk I/O, APK/download size, and runtime library packaging.
- Device-specific RAM pressure, thermal throttling, battery use, and process termination.
- Whether Android Virtualization Framework APIs are available to an ordinary third-party app on the target device; do not assume privileged AVF access.

## Optimization rules

- Headless first: no guest GUI, audio, USB, or unused devices in the first runtime.
- Start with one vCPU and 128–256 MiB guest RAM as a test profile; these are experimental starting points, not guarantees.
- Prefer the QEMU generic `virt` machine and virtio devices where supported by the guest.
- Build only the required system target and devices; disable unneeded QEMU features/dependencies after checking their transitive requirements.
- Do not promise a maximum VM count from guest RAM alone. Measure host RSS, native allocations, translated-code cache, disk cache, kernel overhead, and safety reserve.
- Make resource use observable before attempting automatic scaling.
- Keep guest networking, console output, and diagnostics bounded so a noisy guest cannot exhaust app memory.

## Milestones and acceptance gates

### M0 — Native runtime feasibility
Build an Android ARM64 QEMU artifact and verify it can launch on a real Android environment. Record QEMU revision, build flags, dependencies, ABI, and exact invocation. A Linux CI runner is not sufficient evidence.

### M1 — One guest boots
Boot the current minimal Buildroot AArch64 guest on Android using the generic `virt` board. Capture serial output; verify boot completion, guest identity, and clean shutdown. Do not add a complex UI before this works.

### M2 — Repeatable lifecycle and persistence
Launch/stop/relaunch without orphan processes or corrupting guest files. Test app backgrounding, force-stop/crash recovery, and low-storage failure behavior. Add a foreground service only with the correct Android lifecycle/notification behavior.

### M3 — Two independent VMs
Boot two guest kernels with separate RAM and writable disk state at the same time. Verify neither VM is merely a process/session view of the other.

### M4 — Two-VM Ethernet
Connect the two guest NICs through a QEMU socket backend; configure distinct MAC/IP addresses; prove ARP and bidirectional ICMP from inside the guests. Save logs and packet-test results.

### M5 — Shared virtual LAN
Validate a three-VM shared network and verify broadcast/ARP behavior, isolation from the physical phone LAN, teardown, and restart. Choose multicast or a dedicated switch based on measured reliability.

### M6 — Performance and capacity
On representative phones, measure boot time, steady-state host memory, CPU load, thermal behavior, battery impact, disk throughput, and packet loss. Then define recommended VM profiles and a conservative VM admission policy.

## Test matrix and honest status

| Test | Linux CI can prove | Android device/runtime must prove |
|---|---|---|
| Guest kernel and rootfs boot | Guest artifacts and boot arguments work with Linux-host QEMU | Android-compatible QEMU starts and boots the guest |
| Socket networking | Backend configuration and guest network setup work on Linux | Android sockets/backend work under app sandbox |
| APK build | Kotlin/UI and packaging compile | Runtime can load/execute native components on device |
| Performance | Reproducible functional tests in CI | Actual phone speed, RAM use, heat, and battery |

## Licensing and reuse

QEMU and any reused Android port/dependencies have license and notice obligations. Before copying or distributing code, identify the exact QEMU revision, read its license and the licenses of bundled dependencies, preserve notices, and ensure the distribution/build model complies with the applicable terms. Do not assume that an existing APK or an old fork is a suitable drop-in dependency.

## Immediate next engineering task

Create a minimal Android ARM64 QEMU build spike—not a full feature implementation. It should:
1. Pin a QEMU revision and document the patch set.
2. Cross-build only `qemu-system-aarch64` and required dependencies for Android ARM64.
3. Produce a reproducible artifact in CI.
4. Test the executable/library integration route and TCG JIT behavior on Android.
5. Boot the existing minimal guest and capture serial output.
6. Publish evidence and known limitations before proceeding to multi-VM networking.

The current app and Linux-host guest/network tests remain useful foundations, but until M0–M1 pass on Android, the QEMU runtime must be described as planned rather than implemented.
