# VM sockets, plugs, and virtual patch panel

This document assumes AndroidNetLab will run **multiple independent Linux VMs**. It does not propose replacing them with namespaces. The purpose is to map QEMU's existing interfaces to a simple AndroidNetLab connection model.

## The analogy

- **VM:** a machine with sockets.
- **Virtual NIC:** an Ethernet socket exposed by a VM.
- **Plug/link:** a connection between two virtual NICs.
- **Virtual switch/patch panel:** connects three or more VM NICs on the same virtual LAN.
- **AndroidNetLab connection manager:** owns link IDs, endpoint IDs, network isolation, startup order, and cleanup.

The key principle: don't make the VM UI responsible for network mechanics. The UI says which VM NIC connects to which virtual network; a backend adapter turns that into QEMU configuration.

## 1. Identify the socket types

### A. Guest serial console socket

Purpose: app-to-guest text console, boot readiness, diagnostics, and orderly shutdown.

It is not an Ethernet connection. Keep it separate from the VM's network interfaces. The first runtime spike should expose serial output through a Kotlin console screen or a log view, with bounded buffering so an indefinitely noisy guest cannot consume unbounded memory.

### B. Guest virtual Ethernet NIC

Purpose: guest Linux sends and receives Ethernet frames through a virtual NIC such as QEMU's `virtio-net-device`.

This is the guest-facing interface. The guest configures MAC/IP addresses and runs ARP, IPv4/IPv6, ICMP, TCP, etc. AndroidNetLab should not reimplement those protocols inside the UI.

### C. QEMU socket network backend

Purpose: connect a guest NIC to another QEMU instance through host sockets.

QEMU documents a TCP socket backend with one listener and one connector. The listener waits; the connector joins it. This is a useful first “plug” because it can connect two guest NICs without a host TAP interface or Android root access, subject to verifying the chosen Android build.

Conceptual example for the current Linux CI test (the test already has this shape):

**VM A — listener**
```sh
-netdev socket,id=net0,listen=127.0.0.1:17654
-device virtio-net-device,netdev=net0,mac=52:54:00:77:00:01
```

**VM B — connector**
```sh
-netdev socket,id=net0,connect=127.0.0.1:17654
-device virtio-net-device,netdev=net0,mac=52:54:00:77:00:02
```

Then configure guest addresses such as `10.77.0.1/24` and `10.77.0.2/24`, and test ARP plus ICMP both ways. MAC addresses should be unique per VM. The test should keep the endpoint bound to loopback, not a phone LAN address.

**Important:** this TCP socket backend is a point-to-point connection between two QEMU network endpoints. It should not be mistaken for an automatic many-port Ethernet switch.

### D. QEMU multicast datagram backend

QEMU also documents a datagram/multicast socket backend that can share an emulated network among QEMU instances using the same multicast address and port. This is a candidate “shared LAN plug” for more than two VMs because it avoids a separate switch process.

It is a hypothesis to validate, not yet the chosen Android implementation. Test multicast group membership, loopback routing, packet delivery, and cleanup on the target Android environment. Multicast can be sensitive to platform socket behavior and is not a substitute for tests. Use a private lab-only multicast address and never assume it bridges to the phone's physical LAN.

### E. QEMU hub ports

QEMU has an in-process emulated hub to which multiple NICs/backends in one QEMU process can attach. This is useful inside one QEMU process, but AndroidNetLab's chosen model has separate QEMU processes for separate VMs. Do not assume a hub ID in VM A automatically connects to a hub ID in VM B; process boundaries matter.

## 2. What “many VMs on one LAN” needs

For two VMs, a point-to-point socket link is enough.

For three or more VMs on one shared Ethernet segment, choose one of these only after a proof test:

1. **First experiment: multicast backend.** Lowest implementation effort if it works reliably in Android's app environment.
2. **Fallback: an explicit user-space Ethernet switch.** Each VM NIC plugs into the switch; the switch forwards Ethernet frames between ports. It needs a real, specified transport framing protocol, not an assumption that arbitrary bytes on a TCP stream equal packets.
3. **Alternative topology: pairwise links.** This is valid for routed topologies, but it is not automatically equivalent to a single shared LAN. It also adds NIC and guest network configuration complexity.

Do not write a custom Ethernet switch until the built-in QEMU options have been tested. If a custom switch becomes necessary, implement it as a small, separately tested backend component with packet-length validation, bounded queues, per-network isolation, and a packet counter/capture hook.

## 3. AndroidNetLab's logical model

Keep a backend-neutral model in Kotlin. For example:

- `VmInstance`: stable VM ID, kernel/rootfs, resource profile, lifecycle state.
- `VmNic`: stable NIC ID, owning VM ID, MAC address, model, link state.
- `VirtualNetwork`: stable network ID, network mode (isolated point-to-point or shared LAN), optional MTU, packet counters.
- `NetworkLink`: link ID and endpoint NIC IDs; validates that endpoints are compatible.
- `RuntimeEndpoint`: internal backend detail such as listener/connector port or Unix socket path. Do not expose ports/paths as user concepts.

The user should connect a NIC to a named virtual network (e.g. `lab-net-1`) rather than type raw socket arguments. The backend then creates a listener before connectors, waits for connection readiness, starts guests, and records failures.

### Example state flow

```text
Create VM A NIC ─┐
                 ├─ Attach to Lab Network ─> Backend chooses transport
Create VM B NIC ─┘                           ├─ TCP socket pair (2 endpoints)
                                             ├─ multicast backend (candidate shared LAN)
                                             └─ switch adapter (future fallback)
```

For a two-endpoint TCP link, the connection manager should:
1. Allocate an ephemeral local port or a private Unix-domain socket if the selected backend supports it.
2. Start/configure the listener endpoint first.
3. Start the connector endpoint second.
4. Wait for both runtime endpoints to be ready; timeout and surface a useful error.
5. Start guests or bring NICs up according to the tested launch ordering.
6. On stop/failure, terminate both ends, remove temporary socket paths, and free the port/link record.
7. Never leave a UI link marked Connected after either endpoint has died.

If QEMU can accept an already-open socket file descriptor on Android, passing an app-owned descriptor may avoid port allocation and reduce local port exposure. Treat this as a later optimization: verify the exact QEMU backend and JNI/native integration before designing around FD passing.

## 4. Launch-order and lifecycle rules

- A TCP listener must exist before its connector attempts to connect.
- Startup should be transactional: if VM B fails to start, the app must close the half-created link and either stop VM A or report A as running but disconnected—never pretend the network is connected.
- Stopping a VM should remove its endpoint from every virtual network and update other VM link states.
- A VM restart should not reuse stale socket files, ports, or stale “connected” state.
- Use unique, generated IDs and MAC addresses. Do not derive shell commands from user-provided names.
- Use loopback or app-private Unix-domain sockets for internal control/data endpoints where supported. Never expose QEMU's QMP control monitor on an unauthenticated TCP port.
- Keep VM-to-VM traffic isolated from the phone's real network by default. Internet/NAT access should be an explicit, separate feature and must not be enabled as a side effect of creating an internal lab link.

## 5. Performance optimization at the plug layer

The biggest initial cost is likely to be running independent guest CPUs under software emulation, not the few bytes of metadata used by the connection manager. Still, networking should be efficient:

- Prefer built-in QEMU backends over copying packet data through Kotlin for every frame.
- Keep packet forwarding in native/runtime code; don't route every Ethernet frame through UI state or JSON messages.
- Use bounded queues and backpressure. An unbounded packet queue can exhaust app memory if one guest sends faster than another receives.
- Keep packet inspection disabled by default; when enabled, capture to a bounded ring buffer or a capped file.
- Start with one NIC per VM. Add extra NICs only for a topology that requires them.
- Avoid a host TAP bridge in the first version; it adds Android host privilege/configuration problems and is unnecessary for an isolated lab.
- Benchmark each candidate with ARP, ICMP, sustained TCP throughput, packet loss, and CPU/RSS measurements on Android. Linux CI can verify correctness but not phone performance.

## 6. Exact test sequence

### Test 1 — current Linux-host proof
Use `vm/test-two-guest-network.py` to prove the existing Buildroot guest and TCP socket backend can exchange ARP/ICMP under Linux-host QEMU. Preserve the console logs and report.

### Test 2 — Android runtime, one guest
Package the Android-compatible system emulator, boot one guest, and prove console, stop, and restart. No network topology work should be considered Android-validated before this gate passes.

### Test 3 — Android runtime, two guests
Use the same listener/connector topology with both endpoints on loopback. Assert:
- both guest consoles reach the login/ready marker;
- both NICs are present;
- ARP learns the peer MAC;
- ICMP succeeds both ways;
- stopping one guest clears the link state and leaves no orphan process.

### Test 4 — shared LAN candidate
Start three VMs with QEMU's documented multicast backend, each with a unique MAC and IP. Test broadcast ARP and all-to-all ping. If it fails under Android or proves unreliable, test the custom-switch fallback rather than quietly claiming multicast works.

### Test 5 — scaling and resource policy
Increase VM count gradually. Record per-VM incremental RSS, boot time, CPU use, and packet latency. Establish a dynamic admission limit based on measured runtime overhead and Android memory pressure, not guest RAM settings alone.

## 7. Research sources

- QEMU network emulation, socket backend, multicast, and hubs: https://www.qemu.org/docs/master/system/devices/net.html
- QEMU command-line details for TCP socket and datagram/multicast backends: https://www.qemu.org/docs/master/system/qemu-manpage.html
- QEMU QMP protocol (control plane, separate from guest Ethernet): https://www.qemu.org/docs/master/interop/qmp-spec.html
- QEMU security guidance for privileged monitor interfaces: https://www.qemu.org/docs/master/system/security.html
- Android app sandbox: https://source.android.com/docs/security/app-sandbox

## Decision

The next practical experiment is not a new UI. It is to validate the existing two-VM TCP socket topology, then test QEMU's multicast backend for a shared LAN. This identifies which “plugs” QEMU already supplies and whether AndroidNetLab needs to build a switch at all.
