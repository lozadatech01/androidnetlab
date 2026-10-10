# Linux guest image and CI tests

The initial guest is an AArch64 Linux system built with Buildroot's `qemu_aarch64_virt_defconfig`. It targets QEMU's `virt` board; this does **not** guarantee compatibility with every Android VM engine.

## Build locally

Run `bash vm/build-guest.sh` on a Linux host with common Buildroot dependencies installed. GitHub Actions builds the kernel and root filesystem and retains the generated artifacts and reports for 14 days.

## Automated checks

The Linux guest workflow now:
1. Builds the kernel and ext2 root filesystem.
2. Verifies both artifacts exist and are non-empty.
3. Reports kernel/rootfs sizes, Buildroot target filesystem usage, shared-library file count, and whether common GUI/audio files are present.
4. Boots the guest with QEMU using one emulated vCPU, 256 MiB of configured guest RAM, and a virtual NIC.
5. Requires a recognizable Buildroot login/banner in the serial output and records host-side QEMU resource measurements.

The GUI/audio inventory is diagnostic rather than a hard failure: package contents are reported before making aggressive removals that might break the image. Shared libraries are not removed just because they are `.so` files; some are required for boot and networking.

## What the tests prove—and do not prove

A successful CI smoke test proves the generated image can reach its console under the CI version of QEMU. The reported QEMU RSS and CPU time are measurements of the emulated guest process on a GitHub-hosted x86_64 runner, **not** expected Android RAM use or speed. The 256 MiB value is configured guest RAM, not measured physical RAM consumption.

These checks do not yet prove that the image boots in the Android app, that Android hardware acceleration is available, or that two guests can exchange ARP/ICMP. Those require runtime integration and later device testing.

## Next milestones

1. Review the CI artifact reports and package inventory.
2. Select and integrate an Android-compatible runtime.
3. Boot one guest through the app's console and measure on-device memory.
4. Run two guests over an isolated virtual Ethernet segment and verify ARP/ICMP.
5. Add topology UI and packet inspection.
