package dev.lozadatech.androidnetlab.next

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val bg = 0xFF080B12.toInt()
    private val surface = 0xFF111827.toInt()
    private val surface2 = 0xFF1D2939.toInt()
    private val blue = 0xFF60A5FA.toInt()
    private val white = 0xFFF3F6FB.toInt()
    private val muted = 0xFF9CAFC4.toInt()
    private var page = 1
    private var windowCount = 2
    private var activeWindow = 1
    private var keyboardMode = 0
    private var showKeyboard = true
    private var layoutMode = 0
    private var profile = "Alpine Linux"
    private var ramMb = 512
    private var cpuCount = 1
    private var diskGb = 4
    private var networkMode = "User NAT"
    private var keepAwake = true
    private var autoStart = false
    private val customProfiles = mutableListOf<String>()
    private val builtInProfiles = listOf("Alpine Linux", "Debian Linux", "Buildroot Minimal", "Custom ARM64 VM")
    private val prefs by lazy { getSharedPreferences("androidnetlab_vm_profiles_v1", Context.MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var pageHost: LinearLayout
    private lateinit var status: TextView
    private lateinit var commandInput: EditText
    private var commandDraft = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        loadState()
        if (keepAwake) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(if (landscape()) 4 else 12), dp(if (landscape()) 3 else 8),
                dp(if (landscape()) 4 else 12), dp(4))
        }
        if (!landscape()) {
            root.addView(label("AndroidNetLab", 20, white, true))
            root.addView(label("VIRTUAL MACHINE WORKSPACE", 9, blue, true))
        }
        pageHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(pageHost)
        root.addView(navigation())
        setContentView(root)
        drawPage()
    }

    private fun navigation() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(5), 0, 0)
        val names = listOf("Overview", "Multi", "VMs", "Settings")
        names.forEachIndexed { index, name ->
            addView(Button(this@MainActivity).apply {
                text = name
                textSize = if (landscape()) 9f else 10f
                isAllCaps = false
                setTextColor(if (page == index) blue else muted)
                setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (page == index) 0xFF203653.toInt() else surface))
                minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
                setPadding(dp(2), dp(3), dp(2), dp(3))
                setOnClickListener { if (page != index) { hideNativeKeyboard(); page = index; render() } }
            }, LinearLayout.LayoutParams(0, dp(if (landscape()) 36 else 42), 1f).apply { marginEnd = dp(4) })
        }
    }

    private fun drawPage() {
        if (::commandInput.isInitialized) commandDraft = commandInput.text.toString()
        saveState()
        pageHost.removeAllViews()
        when (page) {
            0 -> drawOverview()
            1 -> drawMultiWindow()
            2 -> drawVmManager()
            else -> drawSettings()
        }
    }

    private fun drawOverview() {
        pageHost.addView(label("OVERVIEW", 10, muted, true).apply { setPadding(0, dp(5), 0, dp(6)) })
        pageHost.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(surface)
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            addView(label("WORKSPACE CANVAS", 12, muted, true).apply { gravity = Gravity.CENTER })
            addView(label("One large blank window", 10, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
        })
        pageHost.addView(label("Network topology and live VM status will appear here after runtime integration.", 10, muted).apply { setPadding(dp(3), dp(6), dp(3), dp(4)) })
    }

    private fun drawMultiWindow() {
        pageHost.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(5))
            addView(label("PANELS", 10, muted, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            (1..4).forEach { count ->
                addView(chip(if (windowCount == count) "✓ $count" else "$count") { windowCount = count; activeWindow = activeWindow.coerceAtMost(count); drawPage() },
                    LinearLayout.LayoutParams(dp(34), dp(32)).apply { marginStart = dp(3) })
            }
            addView(chip(if (showKeyboard) "Keys off" else "Keys on") { showKeyboard = !showKeyboard; hideNativeKeyboard(); drawPage() },
                LinearLayout.LayoutParams(-2, dp(32)).apply { marginStart = dp(4) })
        })
        val panels = LinearLayout(this).apply {
            orientation = if (effectiveStacked()) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        (1..windowCount).forEach { number ->
            val selected = number == activeWindow
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(6))
                setBackgroundColor(if (selected) 0xFF162438.toInt() else surface)
                isClickable = true
                isFocusable = true
                setOnClickListener { activeWindow = number; drawPage() }
            }
            panel.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(label("VM $number", 12, if (selected) blue else white, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                addView(label(if (selected) "ACTIVE" else "SELECT", 8, muted, true))
            })
            panel.addView(label("Console canvas", 11, 0xFF86EFAC.toInt()).apply { setPadding(0, dp(10), 0, dp(4)) })
            panel.addView(label("Runtime not connected", 9, muted))
            val stacked = effectiveStacked()
            val lp = LinearLayout.LayoutParams(if (stacked) -1 else 0, if (stacked) 0 else -1, 1f)
            if (number > 1) { if (stacked) lp.topMargin = dp(5) else lp.marginStart = dp(5) }
            panels.addView(panel, lp)
        }
        pageHost.addView(panels)
        if (showKeyboard) drawKeyboard(pageHost)
        status = label("UI preview only • QEMU runtime not connected", 9, muted)
        pageHost.addView(status)
    }

    private fun drawKeyboard(host: LinearLayout) {
        host.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(3))
            addView(label("INPUT → VM $activeWindow", 10, blue, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            addView(chip(if (keyboardMode == 0) "App keys" else "Native keys") {
                keyboardMode = 1 - keyboardMode
                drawPage()
                if (keyboardMode == 1) {
                    commandInput.requestFocus()
                    commandInput.post { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(commandInput, InputMethodManager.SHOW_IMPLICIT) }
                } else hideNativeKeyboard()
            })
        })
        commandInput = EditText(this).apply {
            hint = if (keyboardMode == 0) "Command input (runtime later)" else "Tap to open Android keyboard"
            setHintTextColor(muted); setTextColor(white); textSize = 12f; setSingleLine(true)
            setPadding(dp(8), dp(4), dp(8), dp(4)); setBackgroundColor(surface)
            setText(commandDraft)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { commandDraft = s?.toString() ?: "" }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
            setOnClickListener { if (keyboardMode == 1) (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }
        }
        host.addView(commandInput, LinearLayout.LayoutParams(-1, dp(if (landscape()) 30 else 38)))
        if (keyboardMode == 1) return
        if (windowCount > 1) host.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(3)); setBackgroundColor(0xFF243449.toInt())
            addView(label("FOCUS", 8, 0xFFBFDBFE.toInt(), true))
            (1..windowCount).forEach { number ->
                addView(chip(if (number == activeWindow) "✓ VM $number" else "VM $number") { activeWindow = number; drawPage() }, LinearLayout.LayoutParams(0, dp(28), 1f).apply { marginStart = dp(3) })
            }
        })
        listOf(listOf("Esc", "Tab", "Ctrl", "Alt", "↑", "⌫"), listOf("/", "-", "|", ":", "←", "↓", "→"), listOf("Home", "End", "PgUp", "PgDn", "Enter", "Space")).forEach { keys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            keys.forEach { key ->
                row.addView(chip(key) {
                    when (key) {
                        "Space" -> commandInput.append(" ")
                        "⌫" -> { val v = commandInput.text; if (v.isNotEmpty()) v.delete(v.length - 1, v.length) }
                        "Tab" -> commandInput.append("\t")
                        "Esc" -> commandInput.append("\u001b")
                        "Enter" -> toast("Input preview only; runtime not connected")
                        "Ctrl", "Alt" -> toast("$key modifier preview only")
                        else -> commandInput.append(key)
                    }
                }, LinearLayout.LayoutParams(0, dp(if (landscape()) 28 else 34), 1f).apply { marginEnd = dp(3); topMargin = dp(2) })
            }
            host.addView(row)
        }
    }

    private fun drawVmManager() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(8)) }
        body.addView(label("VM MANAGER", 11, muted, true))
        body.addView(label("Guest profiles and resource controls • UI preview only", 10, muted).apply { setPadding(0, dp(4), 0, dp(6)) })
        body.addView(section("VM PROFILES"))
        (builtInProfiles + customProfiles).forEach { name ->
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), dp(7), dp(7), dp(7)); setBackgroundColor(if (profile == name) 0xFF1B304B.toInt() else surface)
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    addView(label(name, 11, if (profile == name) blue else white, true))
                    addView(label(if (name == "Alpine Linux") "Lightweight starter profile" else "ARM64 profile preview", 9, muted))
                })
                addView(chip(if (profile == name) "✓ Selected" else "Select") {
                    if (profile != name) { saveProfileSettings(profile); profile = name; loadProfileSettings(name); saveState(); drawPage() }
                })
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        }
        body.addView(section("RESOURCES"))
        body.addView(stepper("Guest memory", "$ramMb MB", { ramMb = (ramMb - 128).coerceAtLeast(128); drawPage() }, { ramMb = (ramMb + 128).coerceAtMost(2048); drawPage() }))
        body.addView(stepper("Virtual CPUs", "$cpuCount core(s)", { cpuCount = (cpuCount - 1).coerceAtLeast(1); drawPage() }, { cpuCount = (cpuCount + 1).coerceAtMost(4); drawPage() }))
        body.addView(stepper("Virtual disk", "$diskGb GB", { diskGb = (diskGb - 2).coerceAtLeast(2); drawPage() }, { diskGb = (diskGb + 2).coerceAtMost(32); drawPage() }))
        body.addView(section("NETWORK ADAPTER"))
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("User NAT", "Isolated", "Bridge*").forEach { mode ->
                addView(chip(if (networkMode == mode) "✓ $mode" else mode) { networkMode = mode; drawPage() },
                    LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) })
            }
        })
        body.addView(label("*Bridge support depends on Android/device networking.", 9, muted).apply { setPadding(0, dp(5), 0, dp(7)) })
        body.addView(chip("+ Create VM profile") { showCreateProfileDialog() }, LinearLayout.LayoutParams(-1, dp(40)))
        val diskFile = virtualDiskFile(profile)
        val diskStatus = if (diskFile.isFile) "Disk exists • ${formatBytes(diskFile.length())}" else "No virtual disk created for this profile"
        body.addView(label(diskStatus, 10, if (diskFile.isFile) 0xFF86EFAC.toInt() else muted).apply { setPadding(dp(2), dp(6), dp(2), dp(3)) })
        body.addView(chip(if (diskFile.isFile) "Virtual disk already exists" else "Create virtual disk (${diskGb} GB)") {
            if (diskFile.isFile) toast("Disk already exists; existing data was preserved.") else createVirtualDisk()
        }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(5) })
        if (diskFile.isFile) {
            body.addView(chip("Sync saved profile metadata") { syncProfileMetadata() },
                LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(4) })
            body.addView(label("Updates profile.txt only; virtual disk contents are not changed.", 9, muted)
                .apply { setPadding(dp(2), dp(3), dp(2), dp(4)) })
        }
        body.addView(chip("▶ Start selected VM") { toast("Disk/profile are local, but QEMU is not installed yet. No guest was started.") }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(5) })
        pageHost.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        pageHost.addView(label("Selected: $profile • $ramMb MB • $cpuCount CPU • $diskGb GB", 9, muted).apply { setPadding(0, dp(3), 0, 0) })
    }

    private fun loadState() {
        val p = getSharedPreferences("androidnetlab_vm_profiles_v1", Context.MODE_PRIVATE)
        profile = p.getString("selected_profile", profile) ?: profile
        ramMb = p.getInt("ram_mb", ramMb).coerceIn(128, 2048)
        cpuCount = p.getInt("cpu_count", cpuCount).coerceIn(1, 4)
        diskGb = p.getInt("disk_gb", diskGb).coerceIn(2, 32)
        networkMode = p.getString("network_mode", networkMode) ?: networkMode
        if (networkMode !in listOf("User NAT", "Isolated", "Bridge*")) networkMode = "User NAT"
        keepAwake = p.getBoolean("keep_awake", keepAwake)
        autoStart = p.getBoolean("auto_start", autoStart)
        layoutMode = p.getInt("layout_mode", layoutMode).coerceIn(0, 2)
        windowCount = p.getInt("window_count", windowCount).coerceIn(1, 4)
        activeWindow = p.getInt("active_window", activeWindow).coerceIn(1, windowCount)
        page = p.getInt("selected_page", page).coerceIn(0, 3)
        showKeyboard = p.getBoolean("show_keyboard", showKeyboard)
        keyboardMode = p.getInt("keyboard_mode", keyboardMode).coerceIn(0, 1)
        val saved = p.getString("custom_profiles", "") ?: ""
        customProfiles.clear()
        saved.split("\\n").map { it.trim() }.filter { it.isNotEmpty() && it !in builtInProfiles }.distinct().forEach { customProfiles.add(it) }
        if (profile !in builtInProfiles && profile !in customProfiles) profile = builtInProfiles.first()
        loadProfileSettings(profile)
    }

    private fun saveState() {
        saveProfileSettings(profile)
        prefs.edit().putString("selected_profile", profile).putInt("ram_mb", ramMb)
            .putInt("cpu_count", cpuCount).putInt("disk_gb", diskGb).putString("network_mode", networkMode)
            .putBoolean("keep_awake", keepAwake).putBoolean("auto_start", autoStart)
            .putInt("layout_mode", layoutMode).putInt("window_count", windowCount)
            .putInt("active_window", activeWindow).putInt("selected_page", page)
            .putBoolean("show_keyboard", showKeyboard).putInt("keyboard_mode", keyboardMode)
            .putString("custom_profiles", customProfiles.joinToString("\\n")).apply()
    }

    private fun profileKey(name: String, field: String): String {
        val encodedName = android.util.Base64.encodeToString(
            name.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        return "vm_profile.v2.$encodedName.$field"
    }

    private fun legacyProfileKey(name: String, field: String) = "vm_profile.${name.hashCode()}.$field"

    private fun loadProfileSettings(name: String) {
        val defaults = when (name) {
            "Alpine Linux" -> intArrayOf(512, 1, 4)
            "Debian Linux" -> intArrayOf(1024, 1, 6)
            "Buildroot Minimal" -> intArrayOf(256, 1, 2)
            "Custom ARM64 VM" -> intArrayOf(512, 1, 4)
            else -> intArrayOf(ramMb, cpuCount, diskGb)
        }
        // Preserve the old single-profile settings during migration for the previously selected profile.
        ramMb = prefs.getInt(profileKey(name, "ram_mb"), prefs.getInt(legacyProfileKey(name, "ram_mb"), if (name == profile) prefs.getInt("ram_mb", defaults[0]) else defaults[0])).coerceIn(128, 2048)
        cpuCount = prefs.getInt(profileKey(name, "cpu_count"), prefs.getInt(legacyProfileKey(name, "cpu_count"), if (name == profile) prefs.getInt("cpu_count", defaults[1]) else defaults[1])).coerceIn(1, 4)
        diskGb = prefs.getInt(profileKey(name, "disk_gb"), prefs.getInt(legacyProfileKey(name, "disk_gb"), if (name == profile) prefs.getInt("disk_gb", defaults[2]) else defaults[2])).coerceIn(2, 32)
        val legacyNetwork = if (name == profile) prefs.getString("network_mode", "User NAT") else null
        networkMode = prefs.getString(profileKey(name, "network_mode"), prefs.getString(legacyProfileKey(name, "network_mode"), legacyNetwork ?: if (name == "Buildroot Minimal") "Isolated" else "User NAT")) ?: "User NAT"
        if (networkMode !in listOf("User NAT", "Isolated", "Bridge*")) networkMode = "User NAT"
    }

    private fun saveProfileSettings(name: String) {
        prefs.edit()
            .putInt(profileKey(name, "ram_mb"), ramMb)
            .putInt(profileKey(name, "cpu_count"), cpuCount)
            .putInt(profileKey(name, "disk_gb"), diskGb)
            .putString(profileKey(name, "network_mode"), networkMode)
            .apply()
    }

    private fun showCreateProfileDialog() {
        val input = EditText(this).apply { hint = "e.g. Alpine Test VM"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Create VM profile")
            .setMessage("Save a local configuration profile. This does not create a disk or start QEMU.")
            .setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Save profile") { _, _ ->
                val name = input.text.toString().trim()
                when {
                    name.isBlank() -> toast("Enter a profile name.")
                    name.length > 40 -> toast("Keep the name under 40 characters.")
                    (builtInProfiles + customProfiles).any { it.equals(name, ignoreCase = true) } -> toast("That profile already exists.")
                    else -> {
                        saveProfileSettings(profile)
                        customProfiles.add(name)
                        profile = name
                        saveProfileSettings(name)
                        saveState()
                        drawPage()
                        toast("Profile saved on this device.")
                    }
                }
            }.show()
    }
    private fun virtualDiskFile(name: String): java.io.File {
        // Keep the original path scheme so disks created by earlier app versions remain discoverable.
        val safeName = name.lowercase().replace(Regex("[^a-z0-9._-]+"), "-").ifBlank { "vm" }
        return java.io.File(filesDir, "vms/$safeName/disk.img")
    }

    private fun formatBytes(bytes: Long): String {
        val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(java.util.Locale.ROOT, "%.2f GiB logical", gib)
    }

    private fun profileMetadataText() =
        "name=$profile\\narchitecture=aarch64\\nmachine=virt\\nmemory_mib=$ramMb\\nvcpus=$cpuCount\\ndisk_gib=$diskGb\\nnetwork=$networkMode\\nformat=raw\\n"

    private fun syncProfileMetadata() {
        val disk = virtualDiskFile(profile)
        if (!disk.isFile) { toast("No virtual disk exists for this profile."); drawPage(); return }
        val metadata = java.io.File(disk.parentFile, "profile.txt")
        val tempMetadata = java.io.File(disk.parentFile, "profile.txt.tmp")
        try {
            tempMetadata.writeText(profileMetadataText())
            if (!tempMetadata.renameTo(metadata)) throw java.io.IOException("Could not finalize metadata file")
            toast("Profile metadata synchronized.")
        } catch (e: Exception) {
            if (tempMetadata.exists()) tempMetadata.delete()
            toast("Metadata sync failed: ${e.message ?: "storage error"}")
        }
        drawPage()
    }

    private fun createVirtualDisk() {
        val disk = virtualDiskFile(profile)
        val vmDir = disk.parentFile ?: throw java.io.IOException("Invalid VM storage path")
        val tempDisk = java.io.File(vmDir, "disk.img.creating")
        val requestedBytes = diskGb.toLong() * 1024L * 1024L * 1024L
        try {
            if (disk.exists()) { toast("Disk already exists; existing data was preserved."); drawPage(); return }
            if (tempDisk.exists()) { toast("An incomplete disk creation file exists; inspect app storage before retrying."); return }
            if (requestedBytes > filesDir.usableSpace - 64L * 1024L * 1024L) { toast("Not enough app storage. Free space or reduce disk size."); return }
            if (!vmDir.exists() && !vmDir.mkdirs()) throw java.io.IOException("Could not create VM storage directory")
            if (!tempDisk.createNewFile()) throw java.io.IOException("Could not reserve temporary disk file")
            java.io.RandomAccessFile(tempDisk, "rw").use { it.setLength(requestedBytes) }
            val metadata = java.io.File(vmDir, "profile.txt")
            val tempMetadata = java.io.File(vmDir, "profile.txt.tmp")
            tempMetadata.writeText(profileMetadataText())
            if (disk.exists() || !tempDisk.renameTo(disk)) throw java.io.IOException("Could not finalize virtual disk")
            if (!tempMetadata.renameTo(metadata)) throw java.io.IOException("Disk created, but metadata finalization failed; use Sync saved profile metadata to recover")
            toast("Created private raw disk: ${diskGb} GB logical size.")
            drawPage()
        } catch (e: Exception) {
            if (tempDisk.exists()) tempDisk.delete()
            toast("Disk creation failed: ${e.message ?: "storage error"}")
            drawPage()
        }
    }
    private fun drawSettings() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(8)) }
        body.addView(label("LAB SETTINGS", 11, muted, true))
        body.addView(label("Workspace preferences and planned runtime options.", 10, muted).apply { setPadding(0, dp(4), 0, dp(6)) })
        body.addView(section("WORKSPACE"))
        body.addView(toggleRow("Keep screen awake", "Prevent timeout during a lab session", keepAwake) {
            keepAwake = !keepAwake
            if (keepAwake) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            drawPage()
        })
        body.addView(toggleRow("Auto-start last VM", "Startup behavior preview; no VM boots yet", autoStart) { autoStart = !autoStart; drawPage() })
        body.addView(section("PANEL LAYOUT"))
        listOf("Auto (portrait stacked)", "Side by side", "Stacked").forEachIndexed { index, title ->
            body.addView(chip(if (layoutMode == index) "✓ $title" else title) { layoutMode = index; drawPage() },
                LinearLayout.LayoutParams(-1, dp(36)).apply { bottomMargin = dp(4) })
        }
        body.addView(section("RUNTIME (PLANNED)"))
        body.addView(infoCard("QEMU runtime", "Not connected yet", "Guest boot, disk images, snapshots and virtual networking are later milestones."))
        body.addView(section("ABOUT"))
        body.addView(infoCard("AndroidNetLab • v0.1 UI", "Four workspace destinations", "Overview, Multi-window, VM Manager and Lab Settings are navigable."))
        pageHost.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun section(name: String) = label(name, 9, blue, true).apply { setPadding(0, dp(10), 0, dp(6)) }

    private fun stepper(name: String, value: String, decrement: () -> Unit, increment: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(5), dp(5), dp(5)); setBackgroundColor(surface)
        addView(label(name, 11, white).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        addView(chip("−", decrement), LinearLayout.LayoutParams(dp(36), dp(32)))
        addView(label(value, 10, blue, true).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(72), -2) })
        addView(chip("+", increment), LinearLayout.LayoutParams(dp(36), dp(32)))
    }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) } }

    private fun toggleRow(name: String, detail: String, value: Boolean, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(9), dp(8), dp(8), dp(8)); setBackgroundColor(surface)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(label(name, 11, white, true)); addView(label(detail, 9, muted).apply { setPadding(0, dp(3), 0, 0) })
        })
        addView(chip(if (value) "ON" else "OFF", action))
    }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) } }

    private fun infoCard(title: String, subtitle: String, detail: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(9), dp(10), dp(9)); setBackgroundColor(surface)
        addView(label(title, 12, white, true)); addView(label(subtitle, 10, blue, true).apply { setPadding(0, dp(3), 0, dp(3)) }); addView(label(detail, 10, muted))
    }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) } }

    private fun effectiveStacked() = when (layoutMode) { 1 -> false; 2 -> true; else -> !landscape() }
    private fun landscape() = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    private fun hideNativeKeyboard() { try { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0) } catch (_: Exception) {} }
    private fun toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()

    private fun chip(caption: String, action: () -> Unit) = Button(this).apply {
        text = caption; textSize = 10f; isAllCaps = false; setTextColor(white)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(surface2))
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(dp(5), 0, dp(5), 0); setOnClickListener { action() }
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}