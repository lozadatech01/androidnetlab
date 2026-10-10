# AndroidNetLab runtime research and architecture proposal

**Status:** Research proposal, not an implementation claim  
**Scope:** Android ARM64 host, minimal AArch64 Linux guests, headless operation, isolated VM networking  
**Decision summary:** First investigate an Android-native QEMU system-emulation backend using software translation (TCG). Keep the guest on QEMU's generic `virt` machine and Buildroot. Do not depend on Android Virtualization Framework (AVF) as the default third-party-app path. Do not add multi-device namespaces until one guest and then two guests are demonstrably working.

## 1. What the app is trying to run

There are two different Linux layers:

1. **Android host:** the phone's existing Linux-based Android kernel, Android userspace, app sandbox, and device drivers.
2. **Guest Linux:** a separate kernel booted by a virtual machine engine, with its own virtual CPU, RAM, devices, filesystem, processes, and network stack.

Android already runs on Linux, but an ordinary Android app cannot simply create arbitrary kernel namespaces, TAP interfaces, or KVM virtual machines with unrestricted host privileges. Android apps are sandboxed by UID and SELinux; access to host networking primitives and virtualization features must be treated as restricted unless explicitly verified on the device.

The app should not try to replace or modify the phone's kernel. The first milestone is a small Linux guest in an emulated virtual board, with a serial console and a persistent disk.

## 2. Runtime options

### Option A — Android-native QEMU system emulation (recommended first investigation)

Build/package a QEMU **system emulator** for Android ARM64 and use TCG software CPU emulation initially. The Android NDK supports shipping native shared libraries in an APK and connecting Kotlin/Java to native code through JNI. This is a plausible integration path, but it is not a drop-in change: QEMU's Android host support, dependency set, build flags, process model, file descriptors, console plumbing, and license notices must be validated.

Advantages:
- Can boot an independent Linux kernel and emulate the virtual board and devices.
- QEMU has documented AArch64 system emulation and recommends the generic `virt` board for guests that do not need to match a physical board.
- QEMU documents socket-based networking between separate emulator instances, which is a promising basis for a lab-only Ethernet segment.

Risks:
- TCG may be slow on a low-end phone, particularly if guest workloads are CPU-heavy.
- A Linux-host QEMU binary is not an Android binary. Android uses Bionic and has its own ABI, linker, SELinux policy, and app sandbox constraints.
- A full QEMU system build brings native dependencies and packaging work. A Linux CI runner successfully booting QEMU proves guest correctness, not Android compatibility or phone performance.
- Need to audit the exact QEMU version, enabled components, dependencies, license obligations, and APK size.

### Option B — Android Virtualization Framework (AVF)

AVF is real Android virtualization infrastructure, but its public-facing applicability is limited for this app. The Android source documentation says its Java APIs are System APIs and require the restricted `MANAGE_VIRTUAL_MACHINE` permission; they are not generally available to ordinary third-party apps. AVF is designed around Microdroid payload VMs and is dependent on device/system support. It should not be the default architecture for a normally installed AndroidNetLab APK unless a concrete target device and distribution model prove that it is available.

### Option C — One shared kernel with namespaces (future alternative)

Linux network namespaces isolate network stacks, interfaces, routes, and firewall rules. A veth pair can connect namespaces. This can represent many lightweight logical devices without booting one kernel per device, but it requires creating and managing namespaces and veth links in a Linux environment with the necessary kernel configuration and privileges.

This option is appropriate as a later **logical-device simulation mode**, not as a substitute for the first real-VM milestone. On a normal Android app, the phone's host kernel is not generally available for unrestricted namespace administration. If implemented inside one guest Linux VM, namespaces can be used inside that guest, but all logical devices then share the guest kernel.

### Option D — Rooted-device or custom-ROM host integration

Root, a custom ROM, privileged/system-app permissions, or device-specific virtualization support could unlock more host-level control. This would sharply narrow the supported user base and increase security, installation, and support complexity. Keep it out of the baseline unless the product explicitly becomes a rooted-device tool.

## 3. Guest image strategy

The existing guest pipeline uses Buildroot's `qemu_aarch64_virt_defconfig` and produces an AArch64 kernel image (`Image`) and an ext2 root filesystem (`rootfs.ext2`). Buildroot is a suitable way to generate a small embedded Linux system reproducibly.

The current artifacts are **guest artifacts**, not an Android runtime. They cannot boot themselves; the runtime needs to provide the machine model, RAM, CPU, virtual serial device, network device, and storage attachment.

The Buildroot example for this target uses the QEMU `virt` machine, a Cortex-A53 CPU, one vCPU, a serial console on `ttyAMA0`, a virtio block device, and a virtio network device. The precise kernel command line and device model must match the generated kernel configuration and the QEMU version used.

Recommended first guest:
- AArch64, QEMU `virt` machine, one vCPU.
- 128 MiB initial test profile if the guest can boot reliably; otherwise 192 or 256 MiB.
- Headless serial console; no GUI, audio, desktop, or unnecessary daemons.
- Minimal Buildroot root filesystem and only the network/debug tools needed for tests.
- Read-only base guest image where practical, with a separate writable overlay or per-instance disk for changes. Never let two guests accidentally write to the same raw filesystem image concurrently.
- Add SSH only if needed; first prefer serial console access to reduce services and attack surface.

The current Android file library primarily accepts disk formats such as qcow2/raw/ISO. It should eventually distinguish **bootable guest bundles** (kernel + rootfs + metadata) from generic imported disk files. An ISO or disk file by itself is not a complete launch configuration.

## 4. Proposed architecture

Keep the Android UI in Kotlin. Put the emulator integration behind a small backend interface so UI and file management do not depend directly on QEMU internals.

- **UI/control layer:** VM list, Create/Import, validated resource profile, Start/Stop, console, state, and diagnostics.
- **VM manager:** validates image paths and configuration; owns instance IDs, lifecycle state, stop requests, and per-instance files.
- **Native runtime adapter:** starts and stops the Android-built emulator engine; passes a controlled argument/configuration set; captures serial output and exit status. Avoid shell-string construction from user input.
- **Guest artifact store:** immutable kernel/base rootfs plus per-VM writable state and metadata.
- **Virtual network manager:** initially a private, isolated lab network. It must never silently bridge guests onto the phone's LAN or Internet.
- **Diagnostics:** distinguish host/app RSS and memory pressure from configured guest RAM and guest-reported metrics. CI runner measurements are not device measurements.

Suggested lifecycle states: `Stopped → Starting → Running → Stopping → Stopped`, with `Failed` and a user-readable reason. The UI must never show Running until the runtime confirms the guest process is alive and boot has reached a defined success condition (for example, a guest-ready marker on the serial console).

Do not block the Android main thread. Start/stop and console I/O belong on a managed worker or service. Handle activity recreation, backgrounding, process death, and explicit stop; Android may kill background processes under memory pressure. A long-running foreground service may be needed for user-visible VM sessions, subject to current Android service rules and appropriate notification UX.

## 5. Virtual networking plan

### Milestone 1: one guest, no external network
Boot a guest and interact through a serial console. This isolates the hard runtime/console/boot problems from networking.

### Milestone 2: two guests on a private virtual Ethernet segment
Use QEMU's socket network backend if the Android build supports it and both emulator instances can connect through local sockets/TCP. The guest kernels must include virtio-net and IPv4/ARP/ICMP support. Assign static addresses first and test:
1. each guest sees its virtual NIC;
2. interfaces are brought up;
3. ARP resolves between guest addresses;
4. ICMP echo succeeds both ways;
5. packet capture or structured counters can show traffic.

QEMU's socket backend is a virtual link between emulator instances, not a Linux TAP interface. TAP/bridge is not the first choice on Android because it often requires host networking privileges unavailable to an ordinary app.

Do not assume that a socket backend automatically creates a complete Ethernet switch for arbitrary topologies. If multi-node switching is required, implement and test an explicit virtual switch/bridge layer or validate QEMU's hub facilities on the chosen Android build. Keep lab traffic isolated by default.

### Milestone 3: packet inspection
Add counters and packet capture at a deliberate point: guest-side `tcpdump` if included in the guest, or a controlled virtual-network capture point. Make capture opt-in and bounded so packet logs cannot exhaust storage.

### Milestone 4: many logical nodes
Only after the two-guest test works, compare:
- many independent QEMU guests (strong isolation, higher per-guest memory and CPU cost);
- one guest with network namespaces and veth pairs (lower overhead, shared kernel, requires guest kernel features and setup scripts).

## 6. Low-memory and performance strategy

The app should use explicit profiles, not promise a fixed number of VMs for all phones. Available RAM, Android version, chipset, thermal limits, and background restrictions vary.

Initial test profiles (hypotheses to measure, not performance guarantees):
- **Tiny boot test:** 128 MiB guest RAM, 1 vCPU.
- **Default candidate:** 192–256 MiB, 1 vCPU.
- **Larger guest:** 384–512 MiB, 1 vCPU; use only when needed.
- Avoid 2 vCPUs until benchmarks show a benefit. More vCPUs can increase scheduling overhead and do not guarantee faster TCG emulation.

Optimizations:
- Disable display, audio, USB, unused emulated hardware, and unnecessary guest services.
- Prefer virtio devices where the guest kernel and machine support them.
- Avoid continuously polling diagnostics too frequently; current app polling is every five seconds.
- Stream large files with bounded buffers, never load disk images into a byte array.
- Keep base images shared/read-only; use per-VM writable overlays to avoid duplicate large files.
- Limit concurrent VM count based on configured guest RAM plus observed native/runtime overhead and a safety reserve. Do not use only configured RAM as the memory budget.
- On low-memory warning, refuse new starts before the OS kills the app. Provide explicit stop controls and recoverable state.
- Test thermal throttling and sustained CPU usage; a short boot test alone is not a performance benchmark.

## 7. Validation gates (no fake completion)

**Gate A — Android packaging:** build an Android ARM64 native runtime artifact and load it from the APK on an Android emulator/device. Verify ABI, dynamic dependencies, license inventory, startup, and clean shutdown.

**Gate B — one guest:** boot the exact Buildroot kernel/rootfs on Android, reach the serial-ready marker, run a basic guest command, stop cleanly, and record host process memory and startup time.

**Gate C — repeatability:** start/stop/restart repeatedly; test malformed config, missing images, corrupted rootfs, low storage, and interrupted start. No orphan emulator process or misleading Running state.

**Gate D — two-guest network:** start two separate guests, verify ARP and bidirectional ping, capture packet evidence, stop one and confirm the other behaves correctly.

**Gate E — phone performance:** measure on the actual target device: idle app memory, runtime RSS, peak memory during boot, steady-state memory, boot duration, CPU load, heat, battery impact, and behavior when Android backgrounds the app. Remote CI cannot establish these phone-specific values.

**Gate F — scale study:** increase guest count gradually and compare separate VMs with namespaces inside a guest. Establish measured limits rather than claiming a theoretical device count.

## 8. Current status versus proposed work

Already present in this repository:
- Android file library and app/device diagnostics.
- Buildroot guest image build scripts and Linux CI/QEMU test scripts.
- A low-memory configuration data class (configuration only; not connected to a runtime).
- Import/copy safeguards and documentation.

Not implemented yet:
- **Runtime validation:** although CI now cross-builds a QEMU 9.2.0 Android ARM64 executable, it has not been launched on a real Android device. This is a candidate build, not a proven usable runtime.
- JNI/native runtime adapter and VM process lifecycle.
- Actual guest boot from the Android app.
- Serial console integration.
- Android-side guest network wiring.
- Running two guests on a phone.

A successful Linux CI boot or two-guest QEMU test is useful evidence that guest images and the Linux-host test topology work, but it does **not** prove Android engine compatibility or acceptable phone performance.

## 8.1 Update: first Android-target QEMU cross-build

The CI experiment has advanced beyond the configure-only stage. With Android NDK r27c (revision `27.2.12479018`), API 28, QEMU 9.2.0, and Android-target builds of GLib/GObject, Pixman, libffi, zlib, and PCRE2, CI produced an AArch64 Android PIE executable whose ELF interpreter is `/system/bin/linker64`. The build disables QEMU's optional POSIX shared-memory host backend because Android Bionic does not provide `shm_open`/`shm_unlink`; regular RAM and file-backed disks remain the initial intended path.

Evidence run: https://github.com/lozadatech01/androidnetlab/actions/runs/38092206058

This is **not** yet an app runtime. The build is not packaged in the APK and has not been run on the target phone. Remaining gates are: strip/debug-size validation, launch permission and linker behavior in the Android app sandbox, `qemu-system-aarch64 --version`, machine/TCG smoke tests, booting the current Buildroot guest with serial readiness, and reliable stop/restart. Do not display a Running state before these are implemented and tested.

## 9. Research references

1. QEMU Arm system emulation: https://www.qemu.org/docs/master/system/target-arm
2. QEMU generic `virt` machine: https://www.qemu.org/docs/master/system/arm/virt
3. QEMU network backends and socket networking: https://www.qemu.org/docs/master/system/devices/net.html
4. QEMU command-line networking reference: https://www.qemu.org/docs/master/system/qemu-manpage.html
5. Buildroot manual: https://www.buildroot.org/downloads/manual/manual.html
6. Buildroot AArch64 QEMU `virt` example: https://github.com/buildroot/buildroot/blob/master/board/qemu/aarch64-virt/readme.txt
7. Android NDK native-code integration: https://developer.android.com/ndk/guides/concepts
8. Android application sandbox: https://source.android.com/docs/security/app-sandbox
9. Android Virtualization Framework API restrictions: https://android.googlesource.com/platform/packages/modules/Virtualization/+/HEAD/libs/framework-virtualization/README.md
10. Linux network namespaces: https://man7.org/linux/man-pages/man7/network_namespaces.7.html
11. Termux execution environment (Bionic vs glibc and optional QEMU): https://github.com/termux/termux-packages/wiki/Termux-execution-environment

## Conclusion

The project is feasible as a staged engineering effort, but the main uncertainty is the Android-compatible system-emulation engine and its real performance on the target phone. The next engineering task should be a small runtime feasibility spike—not a broad UI rewrite: choose a maintainable Android-native QEMU build, package it for ARM64, boot the existing minimal Buildroot guest, and prove console + stop/restart behavior. Only then add two-guest networking and scale tests.
