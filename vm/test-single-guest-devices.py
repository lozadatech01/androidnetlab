#!/usr/bin/env python3
"""Test multiple isolated logical devices in one Linux guest via netns + veth."""
from pathlib import Path
import re
import shutil
import shlex
import sys

import pexpect

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "vm" / "dist"
KERNEL = DIST / "Image"
ROOTFS = DIST / "rootfs.ext2"
REPORT = DIST / "single-guest-devices-report.txt"
LOG = DIST / "single-guest-devices-console.log"


def main():
    if not KERNEL.is_file() or not ROOTFS.is_file():
        raise SystemExit("Build the guest first; vm/dist/Image and rootfs.ext2 are required.")
    qemu = shutil.which("qemu-system-aarch64")
    if not qemu:
        raise SystemExit("qemu-system-aarch64 is required.")

    result = "FAIL: test did not complete"
    details = []
    child = None
    try:
        with LOG.open("w", encoding="utf-8") as logfile:
            child = pexpect.spawn(
                qemu,
                [
                    "-machine", "virt", "-cpu", "cortex-a57",
                    "-m", "256M", "-smp", "1", "-nographic", "-no-reboot",
                    "-monitor", "none", "-serial", "stdio",
                    "-kernel", str(KERNEL),
                    "-drive", f"file={ROOTFS},if=virtio,format=raw",
                    "-append", "root=/dev/vda rw console=ttyAMA0",
                    "-netdev", "user,id=net0",
                    "-device", "virtio-net-device,netdev=net0",
                ],
                encoding="utf-8", timeout=35, logfile=logfile, echo=False,
            )
            child.expect(re.compile(r"(?i)(?:buildroot )?login:\s*$"), timeout=100)
            child.sendline("root")
            idx = child.expect(
                [re.compile(r"(?i)password:\s*$"), re.compile(r"(?m)[#\$] ?$")],
                timeout=30,
            )
            if idx == 0:
                child.sendline("")
                child.expect(re.compile(r"(?m)[#\$] ?$"), timeout=20)

            commands = r"""
set -e
command -v ip
ip netns add device1
ip netns add device2
ip link add veth1 type veth peer name veth2
ip link set veth1 netns device1
ip link set veth2 netns device2
ip -n device1 link set lo up
ip -n device2 link set lo up
ip -n device1 link set veth1 up
ip -n device2 link set veth2 up
ip -n device1 addr add 10.88.0.1/24 dev veth1
ip -n device2 addr add 10.88.0.2/24 dev veth2
ip netns exec device1 ping -c 3 -W 3 10.88.0.2
ip netns exec device2 ping -c 3 -W 3 10.88.0.1
ip -n device1 neigh show
ip -n device2 neigh show
echo __SINGLE_GUEST_DEVICES_PASS__
"""
            child.sendline("sh -c " + shlex.quote(commands) + "; echo __SINGLE_GUEST_DEVICES_DONE__")
            child.expect("__SINGLE_GUEST_DEVICES_DONE__", timeout=50)
            output = child.before
            details.append(output[-5000:])
            if "__SINGLE_GUEST_DEVICES_PASS__" not in output:
                raise RuntimeError("namespace/veth commands failed; inspect console log")
            if not re.search(r"3 packets transmitted, 3 (?:packets )?received", output, re.I):
                raise RuntimeError("did not confirm all three ICMP replies; inspect console log")
            result = "PASS: two isolated network namespaces exchanged ICMP over a veth pair inside one guest kernel."
    except Exception as exc:
        result = f"FAIL: {type(exc).__name__}: {exc}"
        details.append("See single-guest-devices-console.log for serial output.")
        print(result, file=sys.stderr)
    finally:
        if child is not None:
            try:
                child.close(force=True)
            except Exception:
                pass

    report = (
        "Single-guest multiple logical device test\n"
        "Architecture: one AArch64 guest kernel; two network namespaces; one veth pair\n"
        "Addresses: device1=10.88.0.1/24, device2=10.88.0.2/24\n"
        "Configured guest RAM: 256 MiB (not a measurement of physical RAM use)\n"
        + result + "\n\n"
        + "\n".join(details) + "\n"
        + "Console log: single-guest-devices-console.log\n"
    )
    REPORT.write_text(report, encoding="utf-8")
    print(report)
    return 0 if result.startswith("PASS") else 1


if __name__ == "__main__":
    sys.exit(main())
