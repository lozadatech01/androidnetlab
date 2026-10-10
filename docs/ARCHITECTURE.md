# Architecture and validation gates

## Direction
- Native Android controller app.
- A proven Linux system-emulation engine, integrated only after Android compatibility and licensing are reviewed.
- A minimal Buildroot/BusyBox Linux guest.
- An isolated virtual Ethernet segment joining independent guests.
- Runtime measurements separate from guest configuration estimates.

## Milestones
1. Build and install the Android diagnostics APK.
2. Build a minimal AArch64 guest image in CI.
3. Select and integrate a viable Android runtime; boot the guest and expose its console.
4. Run two guests and validate ARP/ICMP over a virtual link.
5. Add topology UI and packet inspection.

## Non-goals for 0.1
- No fake guest status presented as working virtualization.
- No Kali image or desktop environment.
- No host networking bridge or access to external networks.
- No claim that CI success proves device-side virtualization works.

## Measurements
The app samples Android device memory and app process memory. It does not yet measure guest memory or host CPU utilization accurately. Those fields must be added only when the runtime exposes reliable measurements.
