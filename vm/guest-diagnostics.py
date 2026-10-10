#!/usr/bin/env python3
"""Best-effort in-guest process/memory/network report through the serial console.

This runs the normal guest init system. It does not infer guest processes from
the host's process list. Diagnostics are best-effort so a console-login policy
does not turn a valid boot smoke test into a false failure.
"""
from pathlib import Path
import os
import re
import subprocess
import sys
import time

import pexpect

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "vm" / "dist"
KERNEL = DIST / "Image"
ROOTFS = DIST / "rootfs.ext2"
LOG = DIST / "guest-runtime-console.log"
REPORT = DIST / "guest-runtime-diagnostics.txt"


def main():
    qemu = [
        "qemu-system-aarch64", "-machine", "virt", "-cpu", "cortex-a57",
        "-m", "256M", "-smp", "1", "-nographic", "-no-reboot",
        "-monitor", "none", "-serial", "stdio",
        "-kernel", str(KERNEL),
        "-drive", f"file={ROOTFS},if=virtio,format=raw",
        "-append", "root=/dev/vda rw console=ttyAMA0",
        "-netdev", "user,id=net0", "-device", "virtio-net-device,netdev=net0",
    ]
    transcript = []
    result = "SKIPPED"
    child = None
    try:
        with LOG.open("w", encoding="utf-8") as log:
            child = pexpect.spawn(qemu[0], qemu[1:], encoding="utf-8",
                                  timeout=35, logfile=log, echo=False)
            # A serial console login is expected with normal Buildroot init.
            child.expect(re.compile(r"(?i)(?:buildroot )?login:\s*$"), timeout=75)
            child.sendline("root")
            index = child.expect([re.compile(r"(?i)password:\s*$"),
                                  re.compile(r"(?m)[#\$] ?$")], timeout=20)
            if index == 0:
                child.sendline("")
                child.expect(re.compile(r"(?m)[#\$] ?$"), timeout=20)

            commands = [
                "echo ===UPTIME===",
                "cat /proc/uptime",
                "echo ===MEMINFO===",
                "cat /proc/meminfo",
                "echo ===PROCESSES===",
                "ps",
                "echo ===NETWORK_INTERFACES===",
                "cat /proc/net/dev",
                "echo ===IP_LINK===",
                "ip link 2>&1 || ifconfig -a 2>&1 || true",
                "echo ===MOUNTS===",
                "cat /proc/mounts",
                "echo ===DIAGNOSTICS_DONE===",
            ]
            for command in commands:
                child.sendline(command)
            child.expect("===DIAGNOSTICS_DONE===", timeout=25)
            child.expect(re.compile(r"(?m)[#\$] ?$"), timeout=10)
            result = "PASS: collected in-guest process, memory, mount, and network reports"
    except Exception as exc:
        result = f"SKIPPED: unable to collect guest diagnostics through serial login: {type(exc).__name__}: {exc}"
    finally:
        if child is not None:
            try:
                child.close(force=True)
            except Exception:
                pass

    log_text = LOG.read_text(encoding="utf-8", errors="replace") if LOG.exists() else ""
    with REPORT.open("w", encoding="utf-8") as report:
        report.write(result + "\n")
        report.write("Configured guest RAM: 256 MiB (not measured physical RAM)\n")
        report.write("Configured guest vCPUs: 1\n")
        report.write("Console transcript path: guest-runtime-console.log\n")
        report.write("The transcript contains guest commands/output if serial login succeeded.\n")
        if result.startswith("SKIPPED"):
            report.write("No guest process list or guest RAM measurement is claimed.\n")
    print(REPORT.read_text(encoding="utf-8"))
    # Diagnostics are best-effort; normal boot test remains the required gate.
    return 0


if __name__ == "__main__":
    sys.exit(main())
