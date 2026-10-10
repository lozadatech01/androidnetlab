#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILDROOT_VERSION="${BUILDROOT_VERSION:-2025.02.2}"
WORK="${BUILDROOT_WORK_DIR:-${ROOT}/.build/buildroot}"
mkdir -p "$(dirname "$WORK")"
if [[ ! -d "$WORK/.git" ]]; then
  git clone --depth 1 --branch "${BUILDROOT_VERSION}" https://github.com/buildroot/buildroot.git "$WORK"
fi
make -C "$WORK" qemu_aarch64_virt_defconfig
make -C "$WORK" -j"$(nproc)"
mkdir -p "$ROOT/vm/dist"
cp "$WORK/output/images/Image" "$ROOT/vm/dist/Image"
cp "$WORK/output/images/rootfs.ext2" "$ROOT/vm/dist/rootfs.ext2"
printf 'Guest build complete: %s\n' "$ROOT/vm/dist"
