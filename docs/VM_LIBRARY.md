# VM Library prototype

## Implemented

- Import a local file through Android's system document picker.
- Accepted extensions: `.qcow2`, `.img`, `.raw`, `.vdi`, `.vhd`, `.vhdx`, and `.iso`.
- Copy the selected file into the app's private `files/vms` directory.
- List imported files and their sizes.
- Create a full, independent disk-file copy with a unique filename.
- Show aggregate VM-library storage usage and free app storage in diagnostics.
- Check available app storage before starting a known-size import or duplicate, and re-check during the copy to reduce the risk of filling the device.
- Show coarse copy progress while large files are imported or duplicated.
- Export a library file through Android's system save-file picker.
- Delete a library file after an explicit confirmation.
- Use temporary `.partial` files during imports and copies so interrupted writes are less likely to appear as valid library items.

## Current limitations

- These are file-library operations only. The prototype does not yet boot, configure, or execute imported VMs.
- A disk image alone may not contain the VM's firmware, machine configuration, CPU architecture, or boot settings. These will be modeled separately when a runtime is integrated.
- An ISO is installation/live media, not usually a writable VM disk.
- A 16 MiB reserve is maintained during known file copies, but Android storage can change concurrently and unknown-size document providers may not report a reliable file size. A failed copy should leave no listed partial file.
- Files are stored in app-private storage and will be removed if Android app data is cleared or the app is uninstalled. Export/backup lets users keep a copy outside the app.
- Large imports and exports currently run in a single background executor while the activity is open; durable background work and cancellation are future improvements.

## Next technical milestone

Select and validate a runtime on the target Android device before adding VM launch controls. Then boot the generated AArch64 guest and verify console access before implementing a two-guest virtual Ethernet test.
