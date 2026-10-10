package dev.lozadatech.androidnetlab.next.runtime

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/** Validated import and per-profile copy helpers for the first Buildroot guest. */
object GuestArtifactStore {
    private const val MAX_KERNEL_BYTES = 128L * 1024L * 1024L
    private const val MAX_ROOTFS_BYTES = 1024L * 1024L * 1024L
    private val kernelMagic = byteArrayOf(0x41, 0x52, 0x4d, 0x64)

    @Throws(IOException::class)
    fun installKernel(source: InputStream, appFilesDir: File): File =
        installValidated(source, appFilesDir, "guests/buildroot-aarch64/Image", MAX_KERNEL_BYTES) { file ->
            isAarch64KernelImage(file)
        }.also { file ->
            if (!isAarch64KernelImage(file)) throw IOException("Kernel image is missing the AArch64 Image header magic.")
        }

    @Throws(IOException::class)
    fun installRootfs(source: InputStream, appFilesDir: File): File =
        installValidated(source, appFilesDir, "guests/buildroot-aarch64/rootfs.ext2", MAX_ROOTFS_BYTES) { file ->
            isExt2Filesystem(file)
        }.also { file ->
            if (!isExt2Filesystem(file)) throw IOException("Root filesystem does not have the expected ext2 filesystem magic.")
        }

    fun isAarch64KernelImage(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() < 60L) return@runCatching false
        val header = ByteArray(60)
        DataInputStream(FileInputStream(file)).use { it.readFully(header) }
        kernelMagic.indices.all { index -> header[56 + index] == kernelMagic[index] }
    }.getOrDefault(false)

    fun isExt2Filesystem(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() < 1082L) return@runCatching false
        RandomAccessFile(file, "r").use { input ->
            input.seek(1080L)
            input.readUnsignedByte() == 0x53 && input.readUnsignedByte() == 0xef
        }
    }.getOrDefault(false)

    /**
     * Give each profile its own writable rootfs copy. Existing copies are kept
     * on relaunch so guest changes persist; a new base image never silently
     * overwrites a user's existing guest filesystem.
     */
    @Throws(IOException::class)
    fun rootfsForProfile(baseRootfs: File, vmDirectory: File, availableBytes: Long): File {
        if (!isExt2Filesystem(baseRootfs)) throw IOException("The imported base rootfs is missing or invalid.")
        if (!vmDirectory.isDirectory && !vmDirectory.mkdirs()) {
            throw IOException("Could not create the selected VM storage directory.")
        }
        val destination = File(vmDirectory, "guest-rootfs.ext2")
        if (destination.isFile) {
            if (!isExt2Filesystem(destination)) {
                throw IOException("This profile's saved guest-rootfs.ext2 is invalid; back it up before replacing it.")
            }
            return destination
        }
        val required = baseRootfs.length()
        if (required > availableBytes - 32L * 1024L * 1024L) {
            throw IOException("Not enough app storage for a per-profile root filesystem copy.")
        }
        val temporary = File(vmDirectory, "guest-rootfs.ext2.creating")
        if (temporary.exists()) {
            throw IOException("An incomplete rootfs copy exists; inspect the VM folder before retrying.")
        }
        try {
            FileInputStream(baseRootfs).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            if (!isExt2Filesystem(temporary)) throw IOException("Copied rootfs failed ext2 validation.")
            if (!temporary.renameTo(destination)) throw IOException("Could not finalize the profile rootfs copy.")
            return destination
        } catch (error: Exception) {
            if (temporary.exists()) temporary.delete()
            if (error is IOException) throw error
            throw IOException("Could not prepare the profile rootfs: ${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    private fun installValidated(
        source: InputStream,
        appFilesDir: File,
        relativePath: String,
        maxBytes: Long,
        validator: (File) -> Boolean
    ): File {
        val destination = File(appFilesDir, relativePath)
        val parent = destination.parentFile ?: throw IOException("Invalid guest artifact path.")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Could not create guest artifact storage.")
        val temporary = File(parent, destination.name + ".importing")
        val backup = File(parent, destination.name + ".previous")
        if (temporary.exists() && !temporary.delete()) throw IOException("An incomplete import exists; inspect storage before retrying.")

        try {
            var total = 0L
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw IOException("Guest artifact exceeds the supported import size.")
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            if (!validator(temporary)) throw IOException("Selected file does not match the expected guest artifact format.")
            if (backup.exists() && !backup.delete()) throw IOException("Could not clear the previous artifact backup.")
            val hadDestination = destination.exists()
            if (hadDestination && !destination.renameTo(backup)) {
                throw IOException("Could not preserve the installed artifact before replacement.")
            }
            if (!temporary.renameTo(destination)) {
                if (hadDestination && backup.exists()) backup.renameTo(destination)
                throw IOException("Could not finalize the guest artifact import.")
            }
            if (backup.exists()) backup.delete()
            return destination
        } catch (error: Exception) {
            if (temporary.exists()) temporary.delete()
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            if (error is IOException) throw error
            throw IOException("Guest artifact import failed: ${error.message ?: error.javaClass.simpleName}", error)
        }
    }
}
