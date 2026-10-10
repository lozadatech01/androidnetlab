# AndroidNetLab

An Android-first prototype for a lightweight Linux networking lab.

## Current milestone

Prototype 0.1 focuses on a native Android dashboard, measurable host/app diagnostics, and a reproducible CI build. Linux guest images are built separately from the Android APK. Real guest execution and two-guest virtual networking will only be marked implemented after they are integrated and tested on-device.

## Runtime research

See [`docs/ANDROID_RUNTIME_RESEARCH.md`](docs/ANDROID_RUNTIME_RESEARCH.md) for the researched runtime options, Android constraints, proposed architecture, networking plan, optimization hypotheses, and validation gates. This is a proposal; it does not claim that guest execution is already integrated.

## Repository layout

- `app/` — Android application.
- `vm/` — Linux guest build configuration and scripts.
- `.github/workflows/` — GitHub Actions builds and artifact publishing.
- `docs/` — architecture, testing, and compatibility notes.

## Build

Pushes and pull requests run the Android build workflow. Successful builds publish an APK as a workflow artifact. Guest image builds are separate because they require a Linux cross-build environment and may take significantly longer.

## Important

A successful CI build does not prove that virtualization works on a particular phone. Runtime compatibility, memory use, and virtual networking must be validated on the target device.