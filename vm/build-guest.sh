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

# The CI runner provides QEMU; building a second host-side QEMU is unnecessary.
sed -i \
  -e 's/^BR2_PACKAGE_HOST_QEMU=y/# BR2_PACKAGE_HOST_QEMU is not set/' \
  -e 's/^BR2_PACKAGE_HOST_QEMU_SYSTEM_MODE=y/# BR2_PACKAGE_HOST_QEMU_SYSTEM_MODE is not set/' \
  "$WORK/.config"
make -C "$WORK" olddefconfig

grep -q '^BR2_aarch64=y$' "$WORK/.config"
grep -q '^BR2_LINUX_KERNEL=y$' "$WORK/.config"
grep -q '^BR2_TARGET_ROOTFS_EXT2=y$' "$WORK/.config"
if grep -q '^BR2_PACKAGE_HOST_QEMU=y$' "$WORK/.config"; then
  echo "ERROR: unnecessary host QEMU build is still enabled" >&2
  exit 1
fi

IMAGE_DIR="$WORK/output/images"
TARGET_DIR="$WORK/output/target"
DIST_DIR="$ROOT/vm/dist"
mkdir -p "$DIST_DIR"

# Sample actual build processes while make runs. This is CI-host telemetry,
# not a measurement of processes or RAM on the Android device.
(
  while :; do
    {
      echo "===== $(date -u +%FT%TZ) ====="
      ps -eo pid,ppid,pcpu,pmem,rss,etime,comm,args --sort=-pcpu | head -n 25
      echo
    } >> "$DIST_DIR/build-process-samples.txt"
    sleep 10
  done
) &
sampler_pid=$!
cleanup_sampler() {
  kill "$sampler_pid" 2>/dev/null || true
  wait "$sampler_pid" 2>/dev/null || true
}
trap cleanup_sampler EXIT

/usr/bin/time -v -o "$DIST_DIR/build-time-metrics.txt" make -C "$WORK" -j"$(nproc)"

cleanup_sampler
trap - EXIT

for artifact in Image rootfs.ext2; do
  test -s "$IMAGE_DIR/$artifact" || {
    echo "ERROR: expected guest artifact missing or empty: $IMAGE_DIR/$artifact" >&2
    exit 1
  }
done

cp "$IMAGE_DIR/Image" "$DIST_DIR/Image"
cp "$IMAGE_DIR/rootfs.ext2" "$DIST_DIR/rootfs.ext2"

{
  echo "Buildroot version: $BUILDROOT_VERSION"
  echo "Architecture: AArch64 (QEMU virt)"
  echo "Host QEMU package: disabled (CI runner supplies QEMU)"
  echo "Desktop GUI/audio: not intentionally configured; inspect inventory below"
  echo
  echo "Artifact sizes:"
  du -h "$DIST_DIR/Image" "$DIST_DIR/rootfs.ext2"
  echo
  echo "Target filesystem usage:"
  du -sh "$TARGET_DIR"
  echo
  echo "Shared-library files (.so*):"
  find "$TARGET_DIR" -type f -name 'lib*.so*' | wc -l
  echo
  echo "Guest init scripts (/etc/init.d):"
  if [[ -d "$TARGET_DIR/etc/init.d" ]]; then
    find "$TARGET_DIR/etc/init.d" -maxdepth 1 -type f -printf '%f\n' | sort
  else
    echo "(none)"
  fi
  echo
  echo "Potential GUI/audio and optional service binaries (presence only):"
  for path in usr/bin/Xorg usr/bin/Xwayland usr/bin/weston usr/bin/pulseaudio usr/bin/pipewire usr/sbin/sshd usr/sbin/cron usr/bin/dbus-daemon usr/lib/libasound.so usr/lib/libpulse.so; do
    if [[ -e "$TARGET_DIR/$path" ]]; then echo "PRESENT: $path"; fi
  done
  echo
  echo "Top-level service-related directories:"
  for path in etc/init.d etc/rc.d etc/systemd/system; do
    [[ ! -e "$TARGET_DIR/$path" ]] || find "$TARGET_DIR/$path" -maxdepth 1 -mindepth 1 -printf '%P\n' | sort
  done
} | tee "$DIST_DIR/build-report.txt"

printf 'Guest build complete: %s\n' "$DIST_DIR"
