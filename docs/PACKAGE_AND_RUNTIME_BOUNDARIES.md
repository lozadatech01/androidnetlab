# Package and Runtime Boundaries

AndroidNetLab Next is the active Android application. Keep its existing Android identity stable:

- Gradle namespace: `dev.lozadatech.androidnetlab.next`
- Application ID: `dev.lozadatech.androidnetlab.next`
- Existing UI entry point: `dev.lozadatech.androidnetlab.next.MainActivity`

## Rules for future updates

1. **Do not rename the namespace or application ID** to organize new features. A package refactor must be deliberate, reviewed, and tested as a migration.
2. Put new VM/QEMU integration code under `dev.lozadatech.androidnetlab.next.runtime`; keep UI under the root `.next` package (or a future `.ui` subpackage).
3. Keep QEMU-specific details behind the `QemuRuntime` interface. UI code must not call native functions or construct QEMU command lines directly.
4. Keep storage, VM lifecycle, console transport, and networking in separate subpackages as they grow: `.runtime`, `.storage`, `.console`, and `.network`.
5. Never claim a VM is running unless the runtime has confirmed process/guest readiness. A missing native runtime must return an explicit unavailable result.
6. Keep native sources and build output separate from Kotlin source: native integration belongs in `new/app/src/main/cpp` and packaged libraries in the normal Android Gradle build output. Never commit generated APKs or build directories.
7. Keep the old `old/` tree and the active `new/` application clearly separated. Do not copy old Java/Kotlin classes into the active namespace without checking duplicate class names and package declarations.
8. Every feature update must build `gradle --no-daemon -p new assembleDebug`. Runtime claims require separate Android-device evidence; Linux QEMU CI is only a Linux baseline.

## Runtime integration sequence

- M0: pin QEMU source/toolchain and produce a reproducible Android ARM64 build experiment.
- M1: prove native loading/execution and TCG translated-code execution on an actual Android device.
- M2: boot one AArch64 Buildroot guest through serial and shut down cleanly.
- M3: add persistent per-VM storage and lifecycle recovery.
- M4: connect two independent guests over an isolated virtual Ethernet segment.

The Kotlin runtime contract is intentionally introduced before native integration. Until an Android-compatible implementation is present, its implementation must report `UNAVAILABLE` rather than simulate a running VM.
