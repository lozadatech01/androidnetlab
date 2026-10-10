package dev.lozadatech.androidnetlab.next.runtime

import java.io.File
import java.io.FileInputStream
import java.io.DataInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Experimental install/probe path for a user-supplied Android ARM64 QEMU executable.
 *
 * This does not start a guest VM. It only verifies that the imported file is an
 * AArch64 Android ELF and that Android can execute its harmless help commands
 * from the app-private files directory. Device policy may reject that execution;
 * callers must report the failure rather than treating the binary as available.
 */
object QemuBinaryProbe {
    private const val MAX_BINARY_BYTES = 160L * 1024L * 1024L
    private const val MAX_OUTPUT_CHARS = 48 * 1024
    private const val DEFAULT_TIMEOUT_MS = 15_000L

    data class Result(
        val success: Boolean,
        val detail: String,
        val sha256: String? = null
    )

    /**
     * Copy an imported candidate into app-private storage without replacing an
     * existing copy until the incoming file has been fully copied and validated.
     */
    @Throws(IOException::class)
    fun install(source: InputStream, appFilesDir: File): File {
        val runtimeDir = File(appFilesDir, "runtime")
        if (!runtimeDir.isDirectory && !runtimeDir.mkdirs()) {
            throw IOException("Could not create the private runtime directory.")
        }

        val destination = File(runtimeDir, "qemu-system-aarch64")
        val temporary = File(runtimeDir, "qemu-system-aarch64.importing")
        val backup = File(runtimeDir, "qemu-system-aarch64.previous")
        if (temporary.exists() && !temporary.delete()) {
            throw IOException("An incomplete import exists; inspect app storage before retrying.")
        }

        var total = 0L
        try {
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BINARY_BYTES) {
                        throw IOException("QEMU binary exceeds the 160 MiB import limit.")
                    }
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }

            if (total < 20L) throw IOException("Selected file is too small to be an ELF executable.")
            val header = ByteArray(20)
            DataInputStream(FileInputStream(temporary)).use { it.readFully(header) }
            if (!isAarch64Elf(header)) {
                throw IOException("Selected file is not a 64-bit little-endian Android ARM64 ELF executable.")
            }
            if (!temporary.setReadable(true, true) || !temporary.setExecutable(true, true)) {
                throw IOException("Could not set private executable permissions on the imported file.")
            }

            if (backup.exists() && !backup.delete()) {
                throw IOException("Could not clear the previous-runtime backup.")
            }
            val hadDestination = destination.exists()
            if (hadDestination && !destination.renameTo(backup)) {
                throw IOException("Could not preserve the currently installed runtime before replacement.")
            }
            if (!temporary.renameTo(destination)) {
                if (hadDestination && backup.exists()) backup.renameTo(destination)
                throw IOException("Could not finalize the imported runtime file.")
            }
            if (backup.exists()) backup.delete()
            return destination
        } catch (error: Exception) {
            if (temporary.exists()) temporary.delete()
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            if (error is IOException) throw error
            throw IOException("Runtime import failed: ${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    /** Header check used by the importer and unit tests. */
    fun isAarch64Elf(header: ByteArray): Boolean =
        header.size >= 20 &&
            header[0] == 0x7f.toByte() &&
            header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[4] == 2.toByte() && // ELFCLASS64
            header[5] == 1.toByte() && // ELFDATA2LSB
            (header[18].toInt() and 0xff) == 0xb7 && // EM_AARCH64 low byte
            (header[19].toInt() and 0xff) == 0x00

    /**
     * Executes only QEMU metadata/help commands. It does not launch a guest or
     * create a VM disk. The probe fails if the Android app sandbox denies exec.
     */
    fun probe(executable: File, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result {
        if (!executable.isFile) return Result(false, "QEMU executable is not installed.")
        if (!executable.canExecute()) return Result(false, "QEMU file is present but not marked executable.")

        val version = runCommand(executable, listOf("--version"), timeoutMs)
        if (!version.ok) {
            return Result(
                false,
                "Android could not run QEMU from app-private storage. This may be an Android executable-policy/SELinux restriction.\n${version.detail}",
                sha256(executable)
            )
        }

        val machines = runCommand(executable, listOf("-machine", "help"), timeoutMs)
        if (!machines.ok || !machines.output.contains("virt", ignoreCase = true)) {
            return Result(
                false,
                "QEMU started, but the expected generic 'virt' machine was not confirmed.\nQEMU version: ${version.output.trim()}\n${machines.detail}",
                sha256(executable)
            )
        }

        val accelerators = runCommand(executable, listOf("-accel", "help"), timeoutMs)
        if (!accelerators.ok || !accelerators.output.contains("tcg", ignoreCase = true)) {
            return Result(
                false,
                "QEMU and the 'virt' machine responded, but TCG was not confirmed.\nQEMU version: ${version.output.trim()}\n${accelerators.detail}",
                sha256(executable)
            )
        }

        val machineEvidence = machines.output.lineSequence()
            .filter { it.contains("virt", ignoreCase = true) }
            .take(8)
            .joinToString("\n")
        return Result(
            true,
            "Android app-sandbox executable smoke test passed.\n${version.output.trim()}\nTCG: present\nMachine evidence:\n${machineEvidence}\nNo guest has been booted.",
            sha256(executable)
        )
    }

    private data class CommandResult(
        val ok: Boolean,
        val output: String,
        val detail: String
    )

    private fun runCommand(executable: File, arguments: List<String>, timeoutMs: Long): CommandResult {
        val output = StringBuilder()
        val process = try {
            ProcessBuilder(listOf(executable.absolutePath) + arguments)
                .redirectErrorStream(true)
                .start()
        } catch (error: Exception) {
            return CommandResult(false, "", "${arguments.joinToString(" ")} failed: ${error.javaClass.simpleName}: ${error.message}")
        }

        val reader = Thread({
            try {
                process.inputStream.bufferedReader().use { stream ->
                    val buffer = CharArray(2048)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        synchronized(output) {
                            if (output.length < MAX_OUTPUT_CHARS) {
                                val allowed = minOf(count, MAX_OUTPUT_CHARS - output.length)
                                output.append(buffer, 0, allowed)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Process exit/forced termination closes the stream.
            }
        }, "qemu-probe-output").apply { isDaemon = true }
        reader.start()

        val finished = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            return CommandResult(false, "", "Interrupted while running ${arguments.joinToString(" ")}.")
        }
        if (!finished) {
            process.destroyForcibly()
            reader.join(1_000L)
            return CommandResult(false, snapshot(output), "${arguments.joinToString(" ")} timed out after ${timeoutMs} ms.")
        }
        reader.join(1_000L)
        val captured = snapshot(output)
        return if (process.exitValue() == 0) {
            CommandResult(true, captured, captured)
        } else {
            CommandResult(false, captured, "${arguments.joinToString(" ")} exited with code ${process.exitValue()}.\n${captured}")
        }
    }

    private fun snapshot(output: StringBuilder): String = synchronized(output) { output.toString() }

    private fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
