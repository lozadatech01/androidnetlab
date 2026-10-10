package dev.lozadatech.androidnetlab

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var metrics: TextView
    private lateinit var vmList: LinearLayout
    private lateinit var status: TextView
    private val startTime = System.currentTimeMillis()
    private val vmDir by lazy { File(filesDir, "vms").apply { mkdirs() } }
    private val refresh = object : Runnable {
        override fun run() { updateMetrics(); handler.postDelayed(this, 5000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(12, 18, 30)
        window.navigationBarColor = android.graphics.Color.rgb(12, 18, 30)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(18))
            setBackgroundColor(0xFF0C121E.toInt())
        }
        root.addView(label("ANDROID NETLAB", 12f, 0xFF70D6C1.toInt(), true))
        root.addView(label("VM Library", 27f, 0xFFF4F7FC.toInt(), true).apply { setPadding(0, dp(4), 0, dp(2)) })
        root.addView(label("Import a virtual machine disk, keep it locally, and create copies.", 14f, 0xFF9EADC2.toInt()))
        root.addView(actionButton("＋  Import VM disk") { pickVm() })
        status = label("VM files are stored in this app's private storage.", 12f, 0xFF9EADC2.toInt())
        root.addView(status.apply { setPadding(0, dp(10), 0, dp(8)) })
        val scrollContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollContent.addView(label("YOUR VIRTUAL MACHINES", 12f, 0xFF70D6C1.toInt(), true).apply { setPadding(0, dp(10), 0, dp(8)) })
        vmList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollContent.addView(vmList)
        scrollContent.addView(label("DEVICE PERFORMANCE", 12f, 0xFF70D6C1.toInt(), true).apply { setPadding(0, dp(18), 0, dp(8)) })
        metrics = label("Collecting measurements…", 14f, 0xFFE5ECF5.toInt()).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12)); setBackgroundColor(0xFF182438.toInt())
        }
        scrollContent.addView(metrics)
        val scroll = ScrollView(this).apply { addView(scrollContent) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) })
        setContentView(root)
        refreshVmList()
        updateMetrics()
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() { io.shutdown(); super.onDestroy() }

    private fun pickVm() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    @Deprecated("Deprecated API retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val displayName = queryDisplayName(uri) ?: "imported-vm.img"
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext !in SUPPORTED_EXTENSIONS) {
            Toast.makeText(this, "Unsupported type. Choose qcow2, img, raw, vdi, vhd, vhdx, or iso.", Toast.LENGTH_LONG).show()
            return
        }
        val expectedSize = querySize(uri)
        if (expectedSize != null && !hasSpace(expectedSize)) {
            status.text = "Not enough free app storage to import this file. Free up space and try again."
            return
        }
        status.text = "Importing $displayName… Keep the app open."
        io.execute {
            try {
                val target = uniqueFile(vmDir, sanitize(displayName))
                copyUriToFile(uri, target)
                runOnUiThread {
                    status.text = "Imported ${target.name} into app-private storage."
                    refreshVmList(); updateMetrics()
                    Toast.makeText(this, "VM disk imported", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Import failed: ${e.message ?: e.javaClass.simpleName}"
                    Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        val temp = File(target.parentFile, target.name + ".partial")
        try {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Android could not open this file." }
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (vmDir.usableSpace < count + STORAGE_RESERVE_BYTES) throw IllegalStateException("Storage is nearly full. Free up space and retry.")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun createCopy(source: File) {
        if (!hasSpace(source.length())) {
            status.text = "Not enough free app storage to duplicate ${source.name}. Free up space first."
            return
        }
        status.text = "Copying ${source.name}…"
        io.execute {
            try {
                val requested = "${source.nameWithoutExtension}-copy.${source.extension}"
                val target = uniqueFile(vmDir, requested)
                val temp = File(vmDir, target.name + ".partial")
                try {
                    source.inputStream().use { input ->
                        FileOutputStream(temp).use { output ->
                            val buffer = ByteArray(COPY_BUFFER_BYTES)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (vmDir.usableSpace < count + STORAGE_RESERVE_BYTES) throw IllegalStateException("Storage is nearly full. Free up space and retry.")
                        output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                } catch (e: Exception) {
                    temp.delete()
                    throw e
                }
                runOnUiThread {
                    status.text = "Created independent disk copy: ${target.name}"
                    refreshVmList(); updateMetrics()
                    Toast.makeText(this, "VM disk copy created", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Copy failed: ${e.message ?: e.javaClass.simpleName}" }
            }
        }
    }

    private fun refreshVmList() {
        if (!::vmList.isInitialized) return
        vmList.removeAllViews()
        val files = vmDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".partial") }?.sortedBy { it.name.lowercase(Locale.ROOT) }.orEmpty()
        if (files.isEmpty()) {
            vmList.addView(label("No VM disks imported yet. Import a disk image to begin.", 13f, 0xFF9EADC2.toInt()).apply {
                setPadding(dp(12), dp(14), dp(12), dp(14)); setBackgroundColor(0xFF182438.toInt())
            })
            return
        }
        files.forEach { file ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setBackgroundColor(0xFF182438.toInt())
            }
            card.addView(label(file.name, 15f, 0xFFF4F7FC.toInt(), true))
            card.addView(label("${humanSize(file.length())} · ${file.extension.uppercase(Locale.ROOT)} image", 12f, 0xFF9EADC2.toInt()).apply { setPadding(0, dp(4), 0, dp(8)) })
            card.addView(actionButton("Create copy") { createCopy(file) })
            vmList.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
        }
    }

    private fun querySize(uri: Uri): Long? = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it >= 0 } else null
    }

    private fun hasSpace(bytesNeeded: Long): Boolean =
        vmDir.usableSpace > bytesNeeded && vmDir.usableSpace - bytesNeeded >= STORAGE_RESERVE_BYTES

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun uniqueFile(directory: File, requested: String): File {
        var result = File(directory, requested)
        var index = 2
        while (result.exists()) {
            val base = requested.substringBeforeLast('.', requested)
            val ext = requested.substringAfterLast('.', "")
            result = File(directory, if (ext.isEmpty()) "$base-$index" else "$base-$index.$ext")
            index++
        }
        return result
    }

    private fun sanitize(name: String): String {
        val cleaned = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().take(100)
        return cleaned.ifBlank { "imported-vm.img" }
    }

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GiB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MiB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KiB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun updateMetrics() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val appMem = Debug.MemoryInfo()
        Debug.getMemoryInfo(appMem)
        val runtime = Runtime.getRuntime()
        val appUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val pssMb = appMem.totalPss / 1024.0
        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        val vmFiles = vmDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".partial") }.orEmpty()
        val diskBytes = vmFiles.sumOf { it.length() }
        metrics.text = "DEVICE MEMORY\nTotal RAM: ${mem.totalMem.mb()} MiB\nAvailable RAM: ${mem.availMem.mb()} MiB\nLow-memory state: ${if (mem.lowMemory) "YES" else "No"}\n\n" +
            "APP MEMORY\nApp PSS: ${String.format(Locale.US, "%.1f", pssMb)} MiB\nRuntime heap used: $appUsed MiB\nRuntime heap max: ${runtime.maxMemory() / (1024 * 1024)} MiB\n\n" +
            "VM LIBRARY\nStored disk files: ${vmFiles.size}\nTotal disk data: ${humanSize(diskBytes)}\nFree app storage: ${humanSize(vmDir.usableSpace)}\nSession uptime: ${elapsed}s\n\n" +
            "RUNTIME\nVM execution is not integrated yet. Imported images are stored and can be duplicated, but cannot boot from this prototype yet."
    }

    private fun Long.mb(): Long = this / (1024L * 1024L)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(dp(4).toFloat(), 1f)
    }
    private fun actionButton(title: String, action: () -> Unit) = Button(this).apply {
        text = title; setOnClickListener { action() }
    }

    companion object {
        private const val REQUEST_IMPORT = 101
        private const val STORAGE_RESERVE_BYTES = 16L * 1024 * 1024
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private val SUPPORTED_EXTENSIONS = setOf("qcow2", "img", "raw", "vdi", "vhd", "vhdx", "iso")
    }
}
