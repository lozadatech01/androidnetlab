# VM Library prototype

## Implemented in the Android app

- Import a local file through Android's system document picker.
- Accepted extensions: `.qcow2`, `.img`, `.raw`, `.vdi`, `.vhd`, `.vhdx`, and `.iso`.
- Copy the selected file into the app's private `files/vms` directory.
- List imported files and their sizes.
- Create a full, independent disk-file copy with a unique filename.
- Show aggregate VM-library storage usage in the diagnostics panel.
- Use temporary `.partial` files during copies so interrupted writes are less likely to appear as valid library items.

## Current limitations

- These are file-library operations only. The prototype does not yet boot, configure, or execute imported VMs.
- A disk image alone may not contain the VM's firmware, machine configuration, CPU architecture, or boot settings. These will be modeled separately when a runtime is integrated.
- An ISO is installation/live media, not usually a writable VM disk.
- Copies consume approximately the same additional storage as the original file. Large images can exhaust free storage; users should check available device storage before copying.
- Files are stored in app-private storage and will be removed if Android app data is cleared or the app is uninstalled. Export/backup is a later feature.
- This first version uses a simple foreground activity and background I/O thread. A future iteration should add copy progress, cancellation, free-space checks, delete/export actions, and durable background work for large files.
