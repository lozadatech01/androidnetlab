# QEMU Research for AndroidNetLab

**Purpose:** Decide what to reuse from upstream QEMU, what needs Android-specific work, and what to implement first.
**Status:** Research and implementation plan. Android runtime execution is not yet proven.

## Executive decision

Use upstream QEMU as the VM engine. Do not write a replacement emulator.

Reuse as much of QEMU's system emulation as possible: ARM CPU emulation, machine/device models, memory map, serial console, block layer, virtio devices, and network backends. Build a small AndroidNetLab integration layer around it.

**Do not use a desktop/Linux QEMU binary as if it were an Android binary.** QEMU must be built for Android's ABI and runtime environment, with its dependencies and configuration checked. Some Android-specific patches or build adaptations may be required. The amount of patching is an engineering question to answer with a proof of concept, not an assumption that we must fork QEMU deeply.

## How QEMU works

### System emulation vs user-mode emulation

AndroidNetLab needs **system emulation**. QEMU models a complete machine: guest CPU, RAM, board, interrupt controller, timers, serial devices, storage, and NICs. A guest kernel boots as if it were running on that virtual hardware.

QEMU user-mode emulation is different: it runs individual programs compiled for another CPU/OS ABI. That is not enough for our goal of multiple independent Linux kernels.

### TCG vs hardware virtualization

QEMU can use TCG (Tiny Code Generator) to translate guest CPU instructions into host-executable code. This is the likely baseline for an ordinary third-party Android app.

QEMU's KVM acceleration is a Linux-host facility that requires access to the relevant kernel virtualization interface. We must not assume an ordinary Android app can access KVM or Android Virtualization Framework facilities. Verify availability on the target device before considering hardware acceleration.

Even if the phone CPU and guest CPU are both ARM64, QEMU system emulation still models a virtual board and guest machine; same CPU family does not automatically make the guest a native Android process.

## Can we use QEMU as-is?

| Part | Decision |
|---|---|
| QEMU core source and system emulator | Reuse upstream as much as possible |
| ARM64/AArch64 target and generic `virt` board | Use for first guest profile |
| TCG CPU emulation | Initial acceleration baseline; benchmark on device |
| QEMU serial, block, virtio and network subsystems | Reuse supported features |
| Existing Linux-host QEMU executable | Not usable as an Android executable just by copying it |
| Build configuration and dependencies | Adapt/build for Android NDK, ARM64 and Bionic |
| QEMU launch, serial I/O, shutdown and process lifecycle | Integrate and validate for Android |
| VM profiles, storage layout, topology, admission control and app UI | Implement in AndroidNetLab |
| Deep QEMU fork | Avoid unless a measured Android blocker requires it |

## Existing Android reference

Limbo is a real QEMU-based Android project and is useful evidence that QEMU can be integrated into an Android app using native libraries and Android-side code. Its documented build uses old QEMU versions (including 2.9.1 and 5.1.0) and legacy build instructions. Use it to learn the integration pattern, not as proof that an old build can be copied into our modern app unchanged.

Reference sources:
- QEMU emulation overview: https://www.qemu.org/docs/master/about/emulation.html
- QEMU ARM system emulator: https://www.qemu.org/docs/master/system/target-arm.html
- QEMU generic ARM virt board: https://www.qemu.org/docs/master/system/arm/virt
- Limbo Android native build configuration: https://github.com/limboemu/limbo/blob/master/limbo-android-lib/src/main/jni/android-config/android-qemu-config.mak
- Limbo developer build notes: https://github.com/limboemu/limbo/blob/master/Developers.md

## Android-specific uncertainties to test

1. **Build/ABI:** Can the selected upstream QEMU revision and minimal dependencies be cross-compiled for Android ARM64 using the Android NDK and Bionic?
2. **TCG JIT:** Does generated guest code execute correctly under the Android device's memory protection policy?
3. **Integration form:** Should QEMU be linked as a native library invoked through JNI, or run as an app-owned executable if Android policy/build support permits it? Prototype both only as needed; choose based on evidence.
4. **Runtime behavior:** Can the app capture serial output, request orderly shutdown, detect crashes, and clean up reliably?
5. **Resource cost:** What is actual host memory and CPU overhead beyond the guest's configured RAM?
6. **Networking:** Do QEMU socket backends work within the app sandbox, and can they connect two independent guests reliably?
7. **Android lifecycle:** How do background limits, process death, foreground-service rules, storage constraints, heat and battery affect long-running VMs?

## Recommended first guest profile

- Android host ABI: ARM64.
- Guest architecture: AArch64.
- QEMU system target: `aarch64-softmmu` / `qemu-system-aarch64`.
- Machine: QEMU generic `virt`.
- Console: serial, headless.
- vCPU: 1.
- RAM: test 128 MiB and 256 MiB profiles; do not treat these as proven capacity limits.
- Guest: the existing small Buildroot AArch64 Linux image.
- No guest GUI, audio, USB, physical LAN bridging, or unnecessary device models in the first milestone.

## Build order

### 1. Pin the source and make a minimal Android build spike
Choose a supported upstream QEMU release, record its commit and license notices, use the Android NDK, and enable only AArch64 system emulation and the required dependencies. Preserve reproducible build logs and configuration.

### 2. Prove the runtime can launch
Test the resulting Android ARM64 artifact on Android itself. A Linux CI build or a Linux-host QEMU test does not prove this. Record whether executable or JNI integration works and whether TCG can run guest code.

### 3. Boot one guest
Boot the current Buildroot kernel/root filesystem with the generic `virt` machine. Capture serial output, identify a clear boot-ready condition, and shut down cleanly.

### 4. Make lifecycle reliable
Implement Stopped/Starting/Running/Stopping/Failed states, logs, cancellation, crash cleanup, and safe per-VM writable disk state. Only report Running after a guest readiness signal.

### 5. Run two independent guests
Use separate QEMU instances, guest RAM, kernel boot, identities and writable state. Verify each guest can be stopped/restarted independently.

### 6. Add virtual Ethernet
First connect two guests with a QEMU socket backend and prove ARP plus bidirectional ping. Then evaluate a shared virtual LAN for three or more VMs. Do not route packets through the UI thread.

### 7. Measure before optimizing capacity
Measure boot time, total process RSS, guest RAM, CPU load, packet loss, storage I/O, heat and battery. Use those results to set conservative admission limits and choose later optimizations.

## Optimization principles

- Keep one guest architecture and one machine profile initially.
- Disable display, audio, USB, and other unused features.
- Prefer virtio devices supported by the guest.
- Share immutable guest base artifacts, but keep writable state independent for each VM.
- Stream disk I/O; never load whole disk images into RAM.
- Bound console, diagnostic and packet-capture buffers.
- Keep guest traffic isolated from the phone's physical LAN by default.
- Keep the QEMU integration behind a narrow adapter so app code does not depend on QEMU internals.
- Do not promise a VM count from guest RAM alone; include QEMU host overhead and an Android memory safety reserve.

## Decision

**Reuse QEMU; adapt the build and integration for Android; build the VM manager and mobile resource/network orchestration ourselves.** The first deliverable is not a larger dashboard. It is a minimal Android ARM64 QEMU runtime that boots one real guest and shuts down cleanly. Once that passes, proceed to two VMs and networking.
