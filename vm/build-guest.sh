#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILDROOT_VERSION="${BUILDROOT_VERSION:-2025.02.2}"
WORK="${BUILDROOT_WORK_DIR:-${ROOT}/.build/buildroot}"
mkdir -p "$(dirname "$WORK")"

if [[ ! -d "$WORK/.git" ]]; then
  git clone --depth 1 --branch "${BUILDROOT_VERSION}" https://github.com/buildroot/buildroot.git "$WORK"
fi

# Start from Buildroot's supported AArch64 QEMU board configuration.
make -C "$WORK" qemu_aarch64_virt_defconfig
make -C "$WORK" -j"$(nproc)"

IMAGE_DIR="$WORK/output/images"
TARGET_DIR="$WORK/output/target"
DIST_DIR="$ROOT/vm/dist"
mkdir -p "$DIST_DIR"

for artifact in Image rootfs.ext2; do
  test -s "$IMAGE_DIR/$artifact" || {
    echo "ERROR: expected guest artifact missing or empty: $IMAGE_DIR/$artifact" >&2
    exit 1
  }
done

cp "$IMAGE_DIR/Image" "$DIST_DIR/Image"
cp "$IMAGE_DIR/rootfs.ext2" "$DIST_DIR/rootfs.ext2"

# Write a reproducible build report for comparing future size changes.
{
  echo "Buildroot version: $BUILDROOT_VERSION"
  echo "Architecture: AArch64 (QEMU virt)"
  echo "Desktop GUI: not intentionally configured"
  echo "Audio services: not intentionally configured"
  echo
  echo "Artifact sizes:"
  du -h "$DIST_DIR/Image" "$DIST_DIR/rootfs.ext2"
  echo
  echo "Target filesystem usage:"
  du -sh "$TARGET_DIR"
  echo
  echo "Target shared-library files (.so*):"
  find "$TARGET_DIR" -type f -name 'lib*.so*' | wc -l
  echo
  echo "Potential GUI/audio package footprints (presence only; not a failure):"
  for path in usr/bin/Xorg usr/bin/Xwayland usr/bin/weston usr/bin/pulseaudio usr/bin/pipewire usr/lib/libasound.so usr/lib/libpulse.so; do
    if [[ -e "$TARGET_DIR/$path" ]]; then echo "PRESENT: $path"; fi
  done
} | tee "$DIST_DIR/build-report.txt"

printf 'Guest build complete: %s\n' "$DIST_DIR"
