# VM Library prototype

## Implemented

- Import a local file through Android's system document picker.
- Accepted extensions: `.qcow2`, `.img`, `.raw`, `.vdi`, `.vhd`, `.vhdx`, and `.iso`.
- Copy the selected file into the app's private `files/vms` directory.
- List imported files and their sizes.
- Create a full, independent disk-file copy with a unique filename.
- Show device RAM, app memory, VM-library storage usage, free app storage, and session uptime.
- Check available app storage before starting a known-size import or duplicate, and re-check free space during each copy to reduce the risk of filling the device.
- Use temporary `.partial` files during imports and copies so interrupted writes are less likely to appear as valid library items.
- Perform file I/O on a background executor so large copies do not run on the UI thread.

## Current limitations

- These are file-library operations only. The prototype does not yet boot, configure, or execute imported VMs.
- A disk image alone may not contain the VM's firmware, machine configuration, CPU architecture, or boot settings. These will be modeled separately when a runtime is integrated.
- An ISO is installation/live media, not usually a writable VM disk.
- A 16 MiB reserve is maintained during copies, but Android storage can change concurrently and some document providers do not report a reliable file size. If a copy fails, its temporary `.partial` file is removed.
- Files are stored in app-private storage and will be removed if Android app data is cleared or the app is uninstalled. Export/backup, deletion controls, copy progress, and cancellation are not implemented yet.
- Large operations currently run in a single background executor while the activity is open; durable background work is a later improvement.

## Next technical milestone

Select and validate a runtime on the target Android device before adding VM launch controls. Then boot the generated AArch64 guest and verify console access before implementing a two-guest virtual Ethernet test.
