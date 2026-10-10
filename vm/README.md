# Linux guest image

The first guest target is a small AArch64 Linux system built with Buildroot's QEMU AArch64 virt defconfig. This is a guest-machine target, not a promise that the image boots on every Android runtime.

Run ./vm/build-guest.sh on a Linux host with common Buildroot build dependencies installed. GitHub Actions can build and upload the generated kernel/rootfs artifacts.

Runtime integration is a separate milestone. Do not ship the kernel/rootfs as an installed VM until the Android app can launch it and expose a usable console.
