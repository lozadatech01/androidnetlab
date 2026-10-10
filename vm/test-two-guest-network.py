#!/usr/bin/env python3
"""End-to-end two-guest ARP/ICMP test over QEMU's isolated socket Ethernet."""
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time

import pexpect

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "vm" / "dist"
KERNEL = DIST / "Image"
ROOTFS = DIST / "rootfs.ext2"
REPORT = DIST / "two-guest-network-report.txt"
LOG_A = DIST / "guest-a-network-console.log"
LOG_B = DIST / "guest-b-network-console.log"
PORT = 17654


def wait_for_login(child, log):
    child.expect(re.compile(r"(?i)(?:buildroot )?login:\s*$"), timeout=90)
    child.sendline("root")
    idx = child.expect([re.compile(r"(?i)password:\s*$"),
                        re.compile(r"(?m)[#\$] ?$")], timeout=25)
    if idx == 0:
        child.sendline("")
        child.expect(re.compile(r"(?m)[#\$] ?$"), timeout=20)


def run_command(child, command, marker, timeout=25):
    child.sendline(f"{command}; echo {marker}")
    child.expect(marker, timeout=timeout)
    return child.before


def configure_ip(child, address):
    # Prefer iproute2/BusyBox ip; fall back to BusyBox ifconfig.
    out = run_command(
        child,
        f"(ip link set eth0 up && ip addr flush dev eth0 && ip addr add {address}/24 dev eth0) "
        f"|| (ifconfig eth0 {address} netmask 255.255.255.0 up)",
        "__NET_CONFIG_DONE__",
        timeout=15,
    )
    if re.search(r"(not found|can't|unknown|error|failed)", out, re.I):
        raise RuntimeError(f"Could not configure eth0 at {address}: {out[-1200:]}")


def main():
    if not KERNEL.is_file() or not ROOTFS.is_file():
        raise SystemExit("Build the guest first; vm/dist/Image and rootfs.ext2 are required.")
    qemu_bin = shutil.which("qemu-system-aarch64")
    if not qemu_bin:
        raise SystemExit("qemu-system-aarch64 is required.")
    results = []
    children = []
    with tempfile.TemporaryDirectory(prefix="androidnetlab-two-vms-") as tmp:
        root_a = Path(tmp) / "rootfs-a.ext2"
        root_b = Path(tmp) / "rootfs-b.ext2"
        shutil.copyfile(ROOTFS, root_a)
        shutil.copyfile(ROOTFS, root_b)

        def command(rootfs, net_arg):
            return [
                qemu_bin, "-machine", "virt", "-cpu", "cortex-a57",
                "-m", "256M", "-smp", "1", "-nographic", "-no-reboot",
                "-monitor", "none", "-serial", "stdio",
                "-kernel", str(KERNEL),
                "-drive", f"file={rootfs},if=virtio,format=raw",
                "-append", "root=/dev/vda rw console=ttyAMA0",
                *net_arg, "-device", "virtio-net-device,netdev=net0",
            ]

        try:
            with LOG_A.open("w", encoding="utf-8") as log_a:
                a = pexpect.spawn(qemu_bin, command(root_a, [
                    "-netdev", f"socket,id=net0,listen=127.0.0.1:{PORT}"
                ])[1:], encoding="utf-8", timeout=35, logfile=log_a, echo=False)
                children.append(a)
                wait_for_login(a, log_a)

                with LOG_B.open("w", encoding="utf-8") as log_b:
                    b = pexpect.spawn(qemu_bin, command(root_b, [
                        "-netdev", f"socket,id=net0,connect=127.0.0.1:{PORT}"
                    ])[1:], encoding="utf-8", timeout=35, logfile=log_b, echo=False)
                    children.append(b)
                    wait_for_login(b, log_b)

                    configure_ip(a, "10.77.0.1")
                    configure_ip(b, "10.77.0.2")
                    # First try ARP resolution plus ICMP. Explicit ping output is
                    # captured and asserted instead of treating guest boot as success.
                    out = run_command(a, "ping -c 3 -W 3 10.77.0.2",
                                      "__PING_A_TO_B_DONE__", timeout=20)
                    log_a.write("\n=== PING A -> B ===\n" + out + "\n")
                    if not re.search(r"(0% packet loss|bytes from 10\.77\.0\.2)", out, re.I):
                        raise RuntimeError(f"Guest A could not ping guest B: {out[-2000:]}")
                    out_reverse = run_command(b, "ping -c 3 -W 3 10.77.0.1",
                                              "__PING_B_TO_A_DONE__", timeout=20)
                    log_b.write("\n=== PING B -> A ===\n" + out_reverse + "\n")
                    if not re.search(r"(0% packet loss|bytes from 10\.77\.0\.1)", out_reverse, re.I):
                        raise RuntimeError(f"Guest B could not ping guest A: {out_reverse[-2000:]}")
                    results.append("PASS: both guests exchanged ICMP in both directions on an isolated QEMU socket network.")
        except Exception as exc:
            results.append(f"FAIL: {type(exc).__name__}: {exc}")
            print(results[-1], file=sys.stderr)
        finally:
            for child in reversed(children):
                try:
                    child.close(force=True)
                except Exception:
                    pass

    REPORT.write_text(
        "Two-guest isolated virtual Ethernet test\n"
        "Network backend: QEMU socket backend (no Android host networking or root required)\n"
        "Guest A: 10.77.0.1/24\nGuest B: 10.77.0.2/24\n"
        + "\n".join(results) + "\n"
        + "Console logs: guest-a-network-console.log, guest-b-network-console.log\n",
        encoding="utf-8",
    )
    print(REPORT.read_text(encoding="utf-8"))
    return 0 if results and results[0].startswith("PASS") else 1


if __name__ == "__main__":
    sys.exit(main())
