#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_DIR="$ROOT/vm/dist"
KERNEL="$DIST_DIR/Image"
ROOTFS="$DIST_DIR/rootfs.ext2"
LOG="$DIST_DIR/qemu-boot.log"
METRICS="$DIST_DIR/qemu-smoke-metrics.txt"
GUEST_DIAG="$DIST_DIR/guest-runtime-diagnostics.txt"

test -s "$KERNEL"
test -s "$ROOTFS"
command -v qemu-system-aarch64 >/dev/null || {
  echo "qemu-system-aarch64 is required for guest boot smoke tests" >&2
  exit 2
}

# This validates the image under CI QEMU, not Android performance.
set +e
/usr/bin/time -v -o "$METRICS" timeout --signal=TERM --kill-after=5s 90s \
  qemu-system-aarch64 \
    -machine virt \
    -cpu cortex-a57 \
    -m 256M \
    -smp 1 \
    -nographic \
    -no-reboot \
    -kernel "$KERNEL" \
    -drive "file=$ROOTFS,if=virtio,format=raw" \
    -append "root=/dev/vda rw console=ttyAMA0" \
    -netdev user,id=net0 \
    -device virtio-net-device,netdev=net0 \
    >"$LOG" 2>&1
qemu_status=$?
set -e

cat "$LOG"
if ! grep -Eqi 'Welcome to Buildroot|buildroot login:|login:' "$LOG"; then
  echo "Guest did not reach a recognizable console/login banner." >&2
  echo "QEMU exit status: $qemu_status" >&2
  exit 1
fi

{
  echo "Result: boot reached a recognizable console/login banner"
  echo "QEMU exit status: $qemu_status (timeout is expected for this smoke test)"
  echo "Configured guest RAM: 256 MiB"
  echo "Configured guest vCPUs: 1"
  echo "Guest console output bytes: $(wc -c < "$LOG")"
  echo
  echo "Boot log: $LOG"
  echo "Host-side QEMU process measurements (CI x86_64 runner; not Android measurements):"
  cat "$METRICS"
} | tee "$DIST_DIR/qemu-smoke-report.txt"

# Host process lists cannot be treated as guest process inventory.
{
  echo "Guest runtime diagnostics status: not yet collected from inside the guest"
  echo "Reason: this smoke test verifies normal init/console boot but does not log in"
  echo "to the guest. Host ps output is not a valid substitute for guest process inventory."
  echo
  echo "Kernel/rootfs artifact sizes:"
  du -h "$KERNEL" "$ROOTFS"
  echo
  echo "Guest root filesystem image type:"
  file "$ROOTFS"
  echo
  echo "Host QEMU memory metrics are in qemu-smoke-metrics.txt and apply only to CI."
} | tee "$GUEST_DIAG"
