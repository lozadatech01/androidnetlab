package dev.lozadatech.androidnetlab.next

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import dev.lozadatech.androidnetlab.next.runtime.GuestArtifactStore
import dev.lozadatech.androidnetlab.next.runtime.QemuBinaryProbe
import dev.lozadatech.androidnetlab.next.runtime.QemuCommandBuilder
import dev.lozadatech.androidnetlab.next.runtime.VmState
import dev.lozadatech.androidnetlab.next.runtime.validateVmProfileName

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
    private val importQemuRequestCode = 7128
    private val importKernelRequestCode = 7129
    private val importRootfsRequestCode = 7130
    private var qemuProbeVerified = false
    private var qemuProbeRunning = false
    private var guestArtifactImportRunning = false
    private var qemuProbeDetail = "No Android ARM64 QEMU binary imported."
    @Volatile private var activeVmState = VmState.UNAVAILABLE
    private var activeVmStateDetail = "No guest has been started."
    @Volatile private var activeGuestProcess: Process? = null
    @Volatile private var activeGuestInput: java.io.OutputStream? = null
    @Volatile private var stopRequested = false
    private var activeGuestProfile: String? = null
    private val guestConsoleLog = StringBuilder()
    private var guestConsoleTextView: TextView? = null
    private var guestConsoleScrollView: ScrollView? = null
    private var guestConsoleDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        loadState()
        if (qemuBinaryFile().isFile) {
            qemuProbeDetail = "A QEMU candidate is installed in app-private storage. Run the smoke test before relying on it."
        }
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
        pageHost.addView(infoCard("Selected VM profile", profile, "$ramMb MiB memory • $cpuCount vCPU • $diskGb GiB virtual disk"))
        pageHost.addView(infoCard("Network adapter", networkMode, when (networkMode) {
            "Isolated" -> "Saved configuration only; guest networking is not active."
            "Bridge*" -> "Planned option only; Android bridge networking is not implemented."
            else -> "Saved configuration only; NAT networking is not active."
        }))
        val disk = virtualDiskFile(profile)
        val diskState = when {
            hasLegacyDiskPathCollision(profile) -> "Blocked: storage path conflict"
            disk.isFile -> "Disk file exists • ${formatBytes(disk.length())}"
            else -> "No disk file created"
        }
        pageHost.addView(infoCard("Local storage", diskState, "A disk file is not an installed operating system or a bootable VM."))
        pageHost.addView(infoCard(
            "QEMU runtime",
            qemuRuntimeSubtitle(),
            runtimeStatusDetail()
        ))
        pageHost.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(chip("Manage VMs") { page = 2; render() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
            addView(chip("Multi-window") { page = 1; render() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(4) })
        })
        pageHost.addView(label("This overview reports saved settings and local files; it does not report live guest telemetry.", 9, muted).apply { setPadding(dp(3), dp(6), dp(3), dp(4)) })
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
            addView(chip(if (showKeyboard) "Hide keys" else "Show keys") { showKeyboard = !showKeyboard; hideNativeKeyboard(); drawPage() },
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
            panel.addView(label("Console preview", 11, 0xFF86EFAC.toInt()).apply { setPadding(0, dp(10), 0, dp(4)) })
            panel.addView(label("No live console • runtime not connected", 9, muted))
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
        body.addView(label("Guest profiles, local storage, and runtime diagnostics", 10, muted).apply { setPadding(0, dp(4), 0, dp(6)) })
        body.addView(section("VM PROFILES"))
        (builtInProfiles + customProfiles).forEach { name ->
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), dp(7), dp(7), dp(7)); setBackgroundColor(if (profile == name) 0xFF1B304B.toInt() else surface)
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    addView(label(name, 11, if (profile == name) blue else white, true))
                    addView(label(profileSummary(name), 9, muted))
                    addView(label(profileDiskSummary(name), 9, profileDiskSummaryColor(name)).apply { setPadding(0, dp(2), 0, 0) })
                })
                addView(chip(if (profile == name) "✓ Selected" else "Select") {
                    if (profile != name) {
                        if (activeVmState == VmState.STARTING || activeVmState == VmState.RUNNING || activeVmState == VmState.STOPPING || activeGuestProcess != null) {
                            toast("Stop the active guest before switching VM profiles.")
                        } else {
                            saveProfileSettings(profile); profile = name; loadProfileSettings(name); saveState(); drawPage()
                        }
                    }
                })
                if (name in customProfiles) {
                    addView(chip("Delete") { confirmDeleteProfile(name) },
                        LinearLayout.LayoutParams(-2, dp(34)).apply { marginStart = dp(4) })
                }
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
        val diskPathCollision = hasLegacyDiskPathCollision(profile)
        val diskFile = virtualDiskFile(profile)
        val diskStatus = when {
            diskPathCollision -> "Storage conflict: another profile maps to the same legacy disk path"
            diskFile.isFile -> "Disk exists • ${formatBytes(diskFile.length())}"
            else -> "No virtual disk created for this profile"
        }
        body.addView(label(diskStatus, 10, if (diskFile.isFile && !diskPathCollision) 0xFF86EFAC.toInt() else if (diskPathCollision) 0xFFFCA5A5.toInt() else muted).apply { setPadding(dp(2), dp(6), dp(2), dp(3)) })
        if (diskFile.isFile && !diskPathCollision) {
            val metadataFile = java.io.File(diskFile.parentFile, "profile.txt")
            val metadataTemp = java.io.File(diskFile.parentFile, "profile.txt.tmp")
            val metadataBackup = java.io.File(diskFile.parentFile, "profile.txt.bak")
            val hasRecoveryFiles = metadataTemp.exists() || metadataBackup.exists()
            val metadataStatus = when {
                hasRecoveryFiles -> "Metadata recovery files present • inspect storage before syncing"
                !metadataFile.isFile -> "Profile metadata missing • sync or recreate metadata"
                runCatching { metadataFile.readText() == profileMetadataText() }.getOrDefault(false) -> "Profile metadata is synchronized"
                else -> "Profile metadata is stale • sync to apply current settings"
            }
            val metadataColor = when {
                hasRecoveryFiles -> 0xFFFCA5A5.toInt()
                metadataStatus == "Profile metadata is synchronized" -> 0xFF86EFAC.toInt()
                else -> 0xFFFCD34D.toInt()
            }
            body.addView(label(metadataStatus, 9, metadataColor).apply { setPadding(dp(2), dp(1), dp(2), dp(3)) })
            if (hasRecoveryFiles) {
                body.addView(label("Automatic sync is blocked to avoid overwriting recoverable metadata. Back up the VM folder before manual repair.", 9, 0xFFFCA5A5.toInt())
                    .apply { setPadding(dp(2), dp(1), dp(2), dp(3)) })
            }
            // Only offer automated recovery when the original metadata is absent and no temp
            // file makes the intended final state ambiguous.
            if (metadataBackup.isFile && !metadataFile.exists() && !metadataTemp.exists()) {
                body.addView(chip("Restore saved metadata backup") { restoreProfileMetadataBackup() },
                    LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(3) })
                body.addView(label("Restores profile.txt.bak without touching disk.img.", 9, muted)
                    .apply { setPadding(dp(2), dp(2), dp(2), dp(3)) })
            }
        }
        if (diskPathCollision) body.addView(label("Disk actions are disabled to prevent profiles from sharing or overwriting a disk. Rename one profile in a future version or manually back up and resolve the old storage path.", 9, 0xFFFCA5A5.toInt()).apply { setPadding(dp(2), dp(2), dp(2), dp(4)) })
        body.addView(chip(if (diskPathCollision) "Disk actions blocked by path conflict" else if (diskFile.isFile) "Virtual disk already exists" else "Create virtual disk (${diskGb} GB)") {
            if (diskPathCollision) toast("Disk action blocked: resolve the legacy storage path conflict first.")
            else if (diskFile.isFile) toast("Disk already exists; existing data was preserved.") else confirmCreateVirtualDisk()
        }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(5) })
        if (diskFile.isFile && !diskPathCollision) {
            body.addView(chip("Sync saved profile metadata") { syncProfileMetadata() },
                LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(4) })
            body.addView(label("Updates profile.txt only; virtual disk contents are not changed.", 9, muted)
                .apply { setPadding(dp(2), dp(3), dp(2), dp(4)) })
        }
        body.addView(section("GUEST BOOT FILES"))
        val kernelFile = guestKernelFile()
        val rootfsFile = guestRootfsBaseFile()
        val kernelValid = GuestArtifactStore.isAarch64KernelImage(kernelFile)
        val rootfsValid = GuestArtifactStore.isExt2Filesystem(rootfsFile)
        body.addView(infoCard(
            "Buildroot ARM64 kernel",
            if (kernelValid) "Imported • ${formatMiB(kernelFile.length())}" else "Missing or invalid",
            if (kernelValid) kernelFile.name else "Import the Image file from the Linux guest CI artifact."
        ))
        body.addView(chip("Import kernel Image") { confirmImportGuestArtifact(kernel = true) },
            LinearLayout.LayoutParams(-1, dp(38)))
        body.addView(infoCard(
            "Buildroot root filesystem",
            if (rootfsValid) "Imported • ${formatMiB(rootfsFile.length())}" else "Missing or invalid",
            if (rootfsValid) "A private writable copy is made for each VM profile." else "Import rootfs.ext2 from the Linux guest CI artifact."
        ))
        body.addView(chip("Import rootfs.ext2") { confirmImportGuestArtifact(kernel = false) },
            LinearLayout.LayoutParams(-1, dp(38)))
        body.addView(label("First guest boot is headless and serial-only. No virtual NIC is attached yet; the saved network mode remains inactive.", 9, muted)
            .apply { setPadding(dp(2), dp(4), dp(2), dp(5)) })
        body.addView(section("QEMU RUNTIME CHECK"))
        body.addView(infoCard("Android ARM64 executable", qemuRuntimeSubtitle(), runtimeStatusDetail()))
        body.addView(chip(
            when {
                qemuProbeRunning -> "QEMU smoke test running…"
                qemuBinaryFile().isFile -> "Retest / replace QEMU binary"
                else -> "Import & test QEMU ARM64 binary"
            }
        ) { if (!qemuProbeRunning && activeGuestProcess == null) confirmImportQemuBinary() }, LinearLayout.LayoutParams(-1, dp(40)))
        body.addView(label(
            "The smoke test runs --version, -machine help, and -accel help in the app sandbox.",
            9, muted
        ).apply { setPadding(dp(2), dp(4), dp(2), dp(5)) })
        body.addView(chip(
            if (activeVmState == VmState.STARTING || activeVmState == VmState.RUNNING || activeVmState == VmState.STOPPING || activeGuestProcess != null) {
                "■ Stop ${activeGuestProfile ?: "running guest"}"
            } else {
                "▶ Start selected VM"
            }
        ) {
            if (activeVmState == VmState.STARTING || activeVmState == VmState.RUNNING || activeVmState == VmState.STOPPING || activeGuestProcess != null) stopSelectedVm()
            else startSelectedVm()
        }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(5) })
        if (activeVmState == VmState.STARTING || activeVmState == VmState.RUNNING || activeVmState == VmState.STOPPING || activeGuestProcess != null) {
            body.addView(chip("Open live serial console") { showGuestConsoleDialog() },
                LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(4) })
        }
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
        saved.split("\\n").map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 40 && builtInProfiles.none { builtIn -> builtIn.equals(it, ignoreCase = true) } }
            .forEach { candidate ->
                if (customProfiles.none { existing -> existing.equals(candidate, ignoreCase = true) }) {
                    customProfiles.add(candidate)
                }
            }
        if ((builtInProfiles + customProfiles).none { it.equals(profile, ignoreCase = true) }) {
            profile = builtInProfiles.first()
        } else {
            profile = (builtInProfiles + customProfiles).first { it.equals(profile, ignoreCase = true) }
        }
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
        val input = EditText(this).apply {
            hint = "e.g. Alpine Test VM"
            setSingleLine(true)
            filters = arrayOf(android.text.InputFilter.LengthFilter(40))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        AlertDialog.Builder(this).setTitle("Create VM profile")
            .setMessage("Save a local configuration profile. This does not create a disk or start QEMU. Names cannot contain control characters or line breaks.")
            .setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Save profile") { _, _ ->
                val name = input.text.toString().trim()
                val validationError = validateVmProfileName(name, builtInProfiles + customProfiles)
                if (validationError != null) {
                    toast(validationError)
                } else {
                    saveProfileSettings(profile)
                    customProfiles.add(name)
                    profile = name
                    saveProfileSettings(name)
                    saveState()
                    drawPage()
                    toast("Profile saved on this device.")
                }
            }.show()
    }
    private fun legacyDiskSlug(name: String) =
        name.lowercase().replace(Regex("[^a-z0-9._-]+"), "-").ifBlank { "vm" }

    private fun legacyVirtualDiskFile(name: String) =
        java.io.File(filesDir, "vms/${legacyDiskSlug(name)}/disk.img")

    private fun hasLegacyDiskPathCollision(name: String): Boolean {
        val slug = legacyDiskSlug(name)
        return (builtInProfiles + customProfiles).any { other ->
            !other.equals(name, ignoreCase = true) && legacyDiskSlug(other) == slug
        }
    }

    private fun confirmDeleteProfile(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete profile?")
            .setMessage("Remove the custom profile \"$name\" and its saved settings? Any disk.img and profile.txt files will be kept in app storage and will NOT be deleted. If another profile has a conflicting legacy path, its disk access may remain blocked until that conflict is resolved.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete profile") { _, _ ->
                // Keep a legacy disk path blocked while two profile names could refer to it.
                // Removing one of those profiles would otherwise silently reassign its disk.
                if (hasLegacyDiskPathCollision(name) && legacyVirtualDiskFile(name).isFile) {
                    toast("Cannot delete this profile safely: a legacy disk path is shared by similarly named profiles. Back up and resolve the disk conflict first.")
                    return@setPositiveButton
                }
                customProfiles.removeAll { it.equals(name, ignoreCase = true) }
                prefs.edit()
                    .remove(profileKey(name, "ram_mb"))
                    .remove(profileKey(name, "cpu_count"))
                    .remove(profileKey(name, "disk_gb"))
                    .remove(profileKey(name, "network_mode"))
                    .remove(legacyProfileKey(name, "ram_mb"))
                    .remove(legacyProfileKey(name, "cpu_count"))
                    .remove(legacyProfileKey(name, "disk_gb"))
                    .remove(legacyProfileKey(name, "network_mode"))
                    .apply()
                if (profile.equals(name, ignoreCase = true)) {
                    profile = builtInProfiles.first()
                    loadProfileSettings(profile)
                }
                saveState()
                drawPage()
                toast("Profile deleted. Disk files were kept.")
            }
            .show()
    }

    private fun virtualDiskFile(name: String): java.io.File {
        // Preserve a non-conflicting legacy disk path; new profiles receive a stable unique suffix.
        val legacy = legacyVirtualDiskFile(name)
        if (legacy.isFile && !hasLegacyDiskPathCollision(name)) return legacy
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(name.toByteArray(Charsets.UTF_8))
            .take(6).joinToString("") { "%02x".format(it) }
        return java.io.File(filesDir, "vms/${legacyDiskSlug(name)}-$digest/disk.img")
    }

    private fun formatBytes(bytes: Long): String {
        val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(java.util.Locale.ROOT, "%.2f GiB logical", gib)
    }

    private fun profileMetadataText() =
        "name=$profile\narchitecture=aarch64\nmachine=virt\nmemory_mib=$ramMb\nvcpus=$cpuCount\ndisk_gib=$diskGb\nnetwork=$networkMode\nformat=raw\n"

    private fun writeProfileMetadata(vmDir: java.io.File) {
        val metadata = java.io.File(vmDir, "profile.txt")
        val temp = java.io.File(vmDir, "profile.txt.tmp")
        val backup = java.io.File(vmDir, "profile.txt.bak")
        if (temp.exists()) throw java.io.IOException("An unfinished metadata file exists; inspect storage before retrying")
        if (backup.exists()) throw java.io.IOException("A metadata backup exists; inspect storage before retrying")
        var movedOriginal = false
        try {
            // Keep the write itself inside the recovery block so partial writes are cleaned up.
            temp.writeText(profileMetadataText())
            if (metadata.exists()) {
                if (!metadata.renameTo(backup)) throw java.io.IOException("Could not safely back up existing metadata")
                movedOriginal = true
            }
            if (!temp.renameTo(metadata)) throw java.io.IOException("Could not finalize metadata file")
            if (movedOriginal && backup.exists() && !backup.delete()) {
                throw java.io.IOException("Metadata was updated, but its backup remains; inspect storage before retrying")
            }
        } catch (e: Exception) {
            if (!metadata.exists() && movedOriginal && backup.exists()) backup.renameTo(metadata)
            if (temp.exists() && !temp.delete()) {
                e.addSuppressed(java.io.IOException("Could not remove partial metadata temporary file"))
            }
            throw e
        }
    }

    private fun restoreProfileMetadataBackup() {
        if (hasLegacyDiskPathCollision(profile)) {
            toast("Recovery blocked by a legacy storage path conflict.")
            drawPage()
            return
        }
        val disk = virtualDiskFile(profile)
        val vmDir = disk.parentFile
        if (!disk.isFile || vmDir == null) {
            toast("Recovery blocked: the expected virtual disk was not found.")
            drawPage()
            return
        }
        val metadata = java.io.File(vmDir, "profile.txt")
        val temp = java.io.File(vmDir, "profile.txt.tmp")
        val backup = java.io.File(vmDir, "profile.txt.bak")
        if (!backup.isFile || metadata.exists() || temp.exists()) {
            toast("Recovery state changed; no files were modified.")
            drawPage()
            return
        }
        if (!backup.renameTo(metadata)) {
            toast("Could not restore metadata backup; files were preserved.")
        } else {
            toast("Saved metadata backup restored. Review its settings before syncing.")
        }
        drawPage()
    }

    private fun syncProfileMetadata() {
        if (hasLegacyDiskPathCollision(profile)) {
            toast("Metadata sync blocked by a legacy storage path conflict.")
            drawPage()
            return
        }
        val disk = virtualDiskFile(profile)
        if (!disk.isFile) { toast("No virtual disk exists for this profile."); drawPage(); return }
        try {
            writeProfileMetadata(disk.parentFile ?: throw java.io.IOException("Invalid VM storage path"))
            toast("Profile metadata synchronized.")
        } catch (e: Exception) {
            toast("Metadata sync failed: ${e.message ?: "storage error"}")
        }
        drawPage()
    }

    private fun confirmCreateVirtualDisk() {
        val requestedBytes = diskGb.toLong() * 1024L * 1024L * 1024L
        AlertDialog.Builder(this)
            .setTitle("Create empty virtual disk?")
            .setMessage("Create a $diskGb GiB raw disk file for \"$profile\"? This reserves a logical disk size of ${formatBytes(requestedBytes)} in app-private storage. It does not install an operating system or make the VM bootable. Existing disks are never overwritten.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create disk") { _, _ -> createVirtualDisk() }
            .show()
    }

    private fun createVirtualDisk() {
        if (hasLegacyDiskPathCollision(profile)) {
            toast("Disk creation blocked: this profile conflicts with an existing legacy storage path.")
            drawPage()
            return
        }
        val disk = virtualDiskFile(profile)
        val vmDir = disk.parentFile ?: throw java.io.IOException("Invalid VM storage path")
        val tempDisk = java.io.File(vmDir, "disk.img.creating")
        val requestedBytes = diskGb.toLong() * 1024L * 1024L * 1024L
        try {
            if (disk.exists()) { toast("Disk already exists; existing data was preserved."); drawPage(); return }
            if (tempDisk.exists()) { toast("An incomplete disk creation file exists; inspect app storage before retrying."); return }
            if (java.io.File(vmDir, "profile.txt.tmp").exists() || java.io.File(vmDir, "profile.txt.bak").exists()) {
                toast("Metadata recovery files exist; inspect app storage before creating a disk.")
                return
            }
            if (requestedBytes > filesDir.usableSpace - 64L * 1024L * 1024L) { toast("Not enough app storage. Free space or reduce disk size."); return }
            if (!vmDir.exists() && !vmDir.mkdirs()) throw java.io.IOException("Could not create VM storage directory")
            if (!tempDisk.createNewFile()) throw java.io.IOException("Could not reserve temporary disk file")
            java.io.RandomAccessFile(tempDisk, "rw").use { it.setLength(requestedBytes) }
            if (java.io.File(vmDir, "profile.txt.tmp").exists()) throw java.io.IOException("An unfinished metadata file exists; inspect storage before retrying")
            if (disk.exists() || !tempDisk.renameTo(disk)) throw java.io.IOException("Could not finalize virtual disk")
            try {
                writeProfileMetadata(vmDir)
            } catch (metadataError: Exception) {
                throw java.io.IOException("Disk created, but metadata finalization failed; use Sync saved profile metadata to recover", metadataError)
            }
            toast("Created private raw disk: ${diskGb} GB logical size.")
            drawPage()
        } catch (e: Exception) {
            if (tempDisk.exists()) tempDisk.delete()
            toast("Disk creation failed: ${e.message ?: "storage error"}")
            drawPage()
        }
    }
    private fun profileSummary(name: String): String {
        val defaults = when (name) {
            "Alpine Linux" -> intArrayOf(512, 1, 4)
            "Debian Linux" -> intArrayOf(1024, 1, 6)
            "Buildroot Minimal" -> intArrayOf(256, 1, 2)
            "Custom ARM64 VM" -> intArrayOf(512, 1, 4)
            else -> intArrayOf(512, 1, 4)
        }
        val memory = prefs.getInt(profileKey(name, "ram_mb"), prefs.getInt(legacyProfileKey(name, "ram_mb"), if (name == profile) ramMb else defaults[0]))
        val cpus = prefs.getInt(profileKey(name, "cpu_count"), prefs.getInt(legacyProfileKey(name, "cpu_count"), if (name == profile) cpuCount else defaults[1]))
        val disk = prefs.getInt(profileKey(name, "disk_gb"), prefs.getInt(legacyProfileKey(name, "disk_gb"), if (name == profile) diskGb else defaults[2]))
        val network = prefs.getString(profileKey(name, "network_mode"), prefs.getString(legacyProfileKey(name, "network_mode"), if (name == profile) networkMode else if (name == "Buildroot Minimal") "Isolated" else "User NAT")) ?: "User NAT"
        val selected = if (name == profile) " • selected" else ""
        return "$memory MiB • $cpus vCPU • $disk GiB • $network$selected"
    }

    private fun profileDiskSummary(name: String): String {
        if (hasLegacyDiskPathCollision(name)) return "Disk blocked • legacy storage path conflict"
        val disk = virtualDiskFile(name)
        return if (disk.isFile) "Disk file • ${formatBytes(disk.length())}" else "No disk file yet"
    }

    private fun profileDiskSummaryColor(name: String): Int {
        if (hasLegacyDiskPathCollision(name)) return 0xFFFCA5A5.toInt()
        return if (virtualDiskFile(name).isFile) 0xFF86EFAC.toInt() else muted
    }

    private fun qemuBinaryFile() = java.io.File(filesDir, "runtime/qemu-system-aarch64")
    private fun guestKernelFile() = java.io.File(filesDir, "guests/buildroot-aarch64/Image")
    private fun guestRootfsBaseFile() = java.io.File(filesDir, "guests/buildroot-aarch64/rootfs.ext2")

    private fun qemuRuntimeSubtitle(): String = when {
        qemuProbeRunning -> "Smoke test running"
        activeVmState == VmState.STARTING -> "Starting guest • ${activeGuestProfile ?: profile}"
        activeVmState == VmState.RUNNING -> "Guest running • ${activeGuestProfile ?: profile}"
        activeVmState == VmState.STOPPING -> "Stopping guest • ${activeGuestProfile ?: profile}"
        activeVmState == VmState.FAILED -> "Guest launch/boot failed"
        qemuProbeVerified -> "Executable smoke test passed"
        qemuBinaryFile().isFile -> "Candidate installed • not validated"
        else -> "Not installed"
    }

    private fun runtimeStatusDetail(): String = when (activeVmState) {
        VmState.STARTING, VmState.RUNNING, VmState.STOPPING, VmState.FAILED -> activeVmStateDetail
        else -> buildString {
            append(qemuProbeDetail)
            append("\nKernel: ")
            append(if (GuestArtifactStore.isAarch64KernelImage(guestKernelFile())) "AArch64 Image ready" else "not imported")
            append("\nRootfs: ")
            append(if (GuestArtifactStore.isExt2Filesystem(guestRootfsBaseFile())) "ext2 image ready" else "not imported")
        }
    }

    private fun formatMiB(bytes: Long): String =
        String.format(java.util.Locale.ROOT, "%.1f MiB", bytes.toDouble() / (1024.0 * 1024.0))

    private fun isGuestActive(): Boolean =
        activeVmState == VmState.STARTING || activeVmState == VmState.RUNNING ||
            activeVmState == VmState.STOPPING || activeGuestProcess != null

    private fun confirmImportQemuBinary() {
        if (isGuestActive() || qemuProbeRunning) {
            toast("Stop the guest and finish the current runtime action first.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Import Android ARM64 QEMU?")
            .setMessage(
                "Choose qemu-system-aarch64-android-arm64 from the AndroidNetLab QEMU CI artifact. " +
                    "The app copies it into private storage and runs only --version, -machine help, and -accel help. " +
                    "Only import a binary from a source you trust. Android may forbid executing a binary from app-private storage. " +
                    "This test does not boot a guest or enable networking."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Choose file") { _, _ -> launchQemuFilePicker() }
            .show()
    }

    private fun confirmImportGuestArtifact(kernel: Boolean) {
        if (isGuestActive() || guestArtifactImportRunning) {
            toast("Stop the guest and finish the current runtime action first.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(if (kernel) "Import Buildroot ARM64 kernel?" else "Import Buildroot rootfs?")
            .setMessage(
                if (kernel) {
                    "Choose the Image file from the AndroidNetLab Linux guest CI artifact. The app validates the AArch64 Image header."
                } else {
                    "Choose rootfs.ext2 from the AndroidNetLab Linux guest CI artifact. The app validates the ext2 magic and keeps a separate writable copy for each VM profile."
                }
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Choose file") { _, _ -> launchGuestArtifactFilePicker(kernel) }
            .show()
    }

    private fun launchQemuFilePicker() = launchDocumentPicker(importQemuRequestCode)

    private fun launchGuestArtifactFilePicker(kernel: Boolean) =
        launchDocumentPicker(if (kernel) importKernelRequestCode else importRootfsRequestCode)

    @Suppress("DEPRECATION")
    private fun launchDocumentPicker(requestCode: Int) {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivityForResult(intent, requestCode)
        } catch (error: Exception) {
            toast("Could not open file picker: ${error.message ?: "unknown error"}")
        }
    }

    @Deprecated("Uses the platform document picker for compatibility with the native Activity UI")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in listOf(importQemuRequestCode, importKernelRequestCode, importRootfsRequestCode)) return
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: run { toast("No file was selected."); return }

        when (requestCode) {
            importQemuRequestCode -> importQemuCandidate(uri)
            importKernelRequestCode -> importGuestArtifact(uri, kernel = true)
            importRootfsRequestCode -> importGuestArtifact(uri, kernel = false)
        }
    }

    private fun importQemuCandidate(uri: android.net.Uri) {
        if (isGuestActive()) {
            toast("Stop the guest before replacing QEMU.")
            return
        }
        qemuProbeRunning = true
        qemuProbeVerified = false
        qemuProbeDetail = "Importing candidate and testing it inside AndroidNetLab…"
        if (::pageHost.isInitialized) drawPage()

        Thread({
            try {
                val input = contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("The selected file could not be opened.")
                val executable = input.use { QemuBinaryProbe.install(it, filesDir) }
                val result = QemuBinaryProbe.probe(executable)
                runOnUiThread {
                    qemuProbeRunning = false
                    qemuProbeVerified = result.success
                    qemuProbeDetail = result.detail
                    if (!result.sha256.isNullOrBlank()) qemuProbeDetail += "\nSHA-256: ${result.sha256}"
                    if (result.success) {
                        activeVmState = VmState.STOPPED
                        activeVmStateDetail = "QEMU executable passed its app-sandbox smoke test; no guest is running."
                    } else {
                        activeVmState = VmState.UNAVAILABLE
                        activeVmStateDetail = result.detail
                    }
                    if (!isFinishing && ::pageHost.isInitialized) drawPage()
                    toast(if (result.success) "QEMU executable smoke test passed." else "QEMU smoke test failed; see diagnostic details.")
                }
            } catch (error: Exception) {
                runOnUiThread {
                    qemuProbeRunning = false
                    qemuProbeVerified = false
                    qemuProbeDetail = "QEMU import/test failed: ${error.message ?: error.javaClass.simpleName}"
                    activeVmState = VmState.UNAVAILABLE
                    activeVmStateDetail = qemuProbeDetail
                    if (!isFinishing && ::pageHost.isInitialized) drawPage()
                    toast("QEMU import failed.")
                }
            }
        }, "qemu-binary-import-probe").start()
    }

    private fun importGuestArtifact(uri: android.net.Uri, kernel: Boolean) {
        if (isGuestActive() || guestArtifactImportRunning) {
            toast("Stop the guest and finish any current import first.")
            return
        }
        guestArtifactImportRunning = true
        if (::pageHost.isInitialized) drawPage()
        Thread({
            try {
                val input = contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("The selected file could not be opened.")
                val installed = input.use {
                    if (kernel) GuestArtifactStore.installKernel(it, filesDir)
                    else GuestArtifactStore.installRootfs(it, filesDir)
                }
                runOnUiThread {
                    guestArtifactImportRunning = false
                    activeVmStateDetail = if (kernel) {
                        "AArch64 kernel Image imported: ${formatMiB(installed.length())}."
                    } else {
                        "Buildroot ext2 rootfs imported: ${formatMiB(installed.length())}. A separate writable copy is made per profile."
                    }
                    if (!isFinishing && ::pageHost.isInitialized) drawPage()
                    toast(if (kernel) "AArch64 kernel Image imported." else "Buildroot rootfs imported.")
                }
            } catch (error: Exception) {
                runOnUiThread {
                    guestArtifactImportRunning = false
                    activeVmStateDetail = "Guest artifact import failed: ${error.message ?: error.javaClass.simpleName}"
                    if (!isFinishing && ::pageHost.isInitialized) drawPage()
                    toast("Guest artifact import failed.")
                }
            }
        }, if (kernel) "guest-kernel-import" else "guest-rootfs-import").start()
    }

    private fun startSelectedVm() {
        if (isGuestActive()) {
            showGuestConsoleDialog()
            return
        }
        if (qemuProbeRunning || guestArtifactImportRunning) {
            toast("Wait for the current runtime operation to finish.")
            return
        }
        if (!qemuProbeVerified || !qemuBinaryFile().isFile) {
            AlertDialog.Builder(this)
                .setTitle("QEMU is not validated")
                .setMessage("Import the Android ARM64 QEMU candidate and pass the app-sandbox smoke test before starting a guest.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Import / test QEMU") { _, _ -> confirmImportQemuBinary() }
                .show()
            return
        }

        val kernel = guestKernelFile()
        val baseRootfs = guestRootfsBaseFile()
        val kernelReady = GuestArtifactStore.isAarch64KernelImage(kernel)
        val rootfsReady = GuestArtifactStore.isExt2Filesystem(baseRootfs)
        if (!kernelReady || !rootfsReady) {
            val builder = AlertDialog.Builder(this)
                .setTitle("Guest files are missing")
                .setMessage("Import both the Buildroot AArch64 kernel Image and rootfs.ext2 from the Linux guest CI artifact.")
                .setNegativeButton("Cancel", null)
            if (!kernelReady) builder.setPositiveButton("Import kernel") { _, _ -> confirmImportGuestArtifact(kernel = true) }
            if (!rootfsReady) builder.setNeutralButton("Import rootfs") { _, _ -> confirmImportGuestArtifact(kernel = false) }
            builder.show()
            return
        }
        if (hasLegacyDiskPathCollision(profile)) {
            toast("Guest start blocked by a profile storage path conflict.")
            return
        }

        val selectedProfile = profile
        val selectedMemory = ramMb
        val selectedCpus = cpuCount
        val binary = qemuBinaryFile()
        val disk = virtualDiskFile(selectedProfile)
        val vmDirectory = disk.parentFile ?: run { toast("Invalid VM storage path."); return }
        val dataDisk = disk.takeIf { it.isFile }

        activeGuestProfile = selectedProfile
        stopRequested = false
        activeVmState = VmState.STARTING
        activeVmStateDetail = "Preparing a private writable rootfs copy, then waiting for the Buildroot serial-ready marker."
        synchronized(guestConsoleLog) {
            guestConsoleLog.setLength(0)
            guestConsoleLog.append("[AndroidNetLab] Starting $selectedProfile in headless serial-only mode.\n")
            guestConsoleLog.append("[AndroidNetLab] Guest networking is disabled in this first boot path.\n")
        }
        drawPage()
        showGuestConsoleDialog()

        Thread({
            var process: Process? = null
            var ready = false
            try {
                val writableRootfs = GuestArtifactStore.rootfsForProfile(baseRootfs, vmDirectory, filesDir.usableSpace)
                if (stopRequested) {
                    stopRequested = false
                    updateGuestState(VmState.STOPPED, "Guest launch cancelled before QEMU started.")
                    return@Thread
                }
                val command = QemuCommandBuilder.build(
                    QemuCommandBuilder.Config(
                        executable = binary,
                        kernelImage = kernel,
                        rootfsImage = writableRootfs,
                        dataDisk = dataDisk,
                        memoryMiB = selectedMemory,
                        vcpus = selectedCpus
                    )
                )
                val startedProcess = ProcessBuilder(command)
                    .directory(filesDir)
                    .redirectErrorStream(true)
                    .start()
                process = startedProcess
                activeGuestProcess = startedProcess
                activeGuestInput = startedProcess.outputStream
                if (stopRequested) startedProcess.destroy()
                appendGuestConsole("[AndroidNetLab] QEMU process started.\n")

                Thread({
                    try {
                        Thread.sleep(180_000L)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    if (activeGuestProcess === startedProcess && activeVmState == VmState.STARTING && !stopRequested) {
                        updateGuestState(VmState.FAILED, "Guest did not reach a Buildroot login/ready marker within 180 seconds; QEMU was stopped.")
                        appendGuestConsole("\n[AndroidNetLab] Boot readiness timed out; stopping QEMU.\n")
                        startedProcess.destroy()
                        Thread({
                            try {
                                if (!startedProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) startedProcess.destroyForcibly()
                            } catch (_: Exception) {
                                startedProcess.destroyForcibly()
                            }
                        }, "qemu-timeout-cleanup").start()
                    }
                }, "qemu-boot-readiness-timeout").apply { isDaemon = true; start() }

                startedProcess.inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        appendGuestConsole(line + "\n")
                        if (!ready && !stopRequested && isGuestReadyMarker(line)) {
                            ready = true
                            updateGuestState(
                                VmState.RUNNING,
                                "Buildroot guest reached its serial-ready marker. One guest is active; no virtual NIC is attached."
                            )
                        }
                    }
                }
                val exitCode = startedProcess.waitFor()
                val wasRequestedStop = stopRequested
                if (activeGuestProcess === startedProcess) activeGuestProcess = null
                if (activeGuestInput === startedProcess.outputStream) activeGuestInput = null
                stopRequested = false
                activeVmState = when {
                    wasRequestedStop -> VmState.STOPPED
                    ready && exitCode == 0 -> VmState.STOPPED
                    else -> VmState.FAILED
                }
                activeVmStateDetail = when {
                    wasRequestedStop -> "QEMU stopped on request. If the guest did not run poweroff first, filesystem writes may not be clean."
                    ready && exitCode == 0 -> "Guest process exited with code 0 after reaching readiness."
                    ready -> "Guest process exited unexpectedly with code $exitCode."
                    else -> "QEMU exited with code $exitCode before the guest readiness marker."
                }
                appendGuestConsole("\n[AndroidNetLab] QEMU exited with code $exitCode.\n")
                if (!isFinishing && ::pageHost.isInitialized) runOnUiThread { drawPage() }
            } catch (error: Exception) {
                process?.destroyForcibly()
                if (activeGuestProcess === process) activeGuestProcess = null
                activeGuestInput = null
                stopRequested = false
                val detail = "QEMU launch failed: ${error.javaClass.simpleName}: ${error.message ?: "no detail"}. Check Android execution policy and imported image files."
                updateGuestState(VmState.FAILED, detail)
                appendGuestConsole("\n[AndroidNetLab] $detail\n")
            }
        }, "qemu-single-guest-runtime").start()
    }

    private fun isGuestReadyMarker(line: String): Boolean =
        line.contains("Welcome to Buildroot", ignoreCase = true) ||
            line.contains("buildroot login:", ignoreCase = true) ||
            Regex("login:\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(line)

    private fun updateGuestState(state: VmState, detail: String) {
        activeVmState = state
        activeVmStateDetail = detail
        if (!isFinishing) runOnUiThread { if (::pageHost.isInitialized) drawPage() }
    }

    private fun appendGuestConsole(chunk: String) {
        runOnUiThread {
            synchronized(guestConsoleLog) {
                if (guestConsoleLog.length + chunk.length > 64 * 1024) {
                    val excess = guestConsoleLog.length + chunk.length - 64 * 1024
                    guestConsoleLog.delete(0, excess.coerceAtMost(guestConsoleLog.length))
                }
                guestConsoleLog.append(chunk)
                guestConsoleTextView?.text = guestConsoleLog.toString()
            }
            guestConsoleScrollView?.post { guestConsoleScrollView?.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun showGuestConsoleDialog() {
        if (guestConsoleDialog?.isShowing == true) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(3), dp(8), dp(4))
        }
        val console = TextView(this).apply {
            setTextColor(0xFFB7F7C7.toInt())
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            text = synchronized(guestConsoleLog) { guestConsoleLog.toString() }
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        guestConsoleTextView = console
        val scroll = ScrollView(this).apply {
            addView(console)
            setBackgroundColor(0xFF05080D.toInt())
        }
        guestConsoleScrollView = scroll
        panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(260), 1f))
        panel.addView(label("Type a guest command and send Enter. Use 'poweroff' inside Buildroot for a clean guest shutdown.", 9, muted)
            .apply { setPadding(0, dp(5), 0, dp(4)) })
        val input = EditText(this).apply {
            hint = "Serial input (e.g. poweroff)"
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            textSize = 12f
        }
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(input, LinearLayout.LayoutParams(0, dp(42), 1f))
            addView(chip("Send") {
                val value = input.text.toString()
                if (value.isNotBlank()) {
                    sendGuestConsoleInput(value)
                    input.text.clear()
                }
            }, LinearLayout.LayoutParams(dp(72), dp(42)).apply { marginStart = dp(5) })
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                val value = input.text.toString()
                if (value.isNotBlank()) {
                    sendGuestConsoleInput(value)
                    input.text.clear()
                }
                true
            } else false
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Serial console • ${activeGuestProfile ?: profile}")
            .setView(panel)
            .setPositiveButton("Stop VM", null)
            .setNegativeButton("Close", null)
            .create()
        guestConsoleDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { stopSelectedVm() }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { dialog.dismiss() }
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.96f).toInt(),
                (resources.displayMetrics.heightPixels * 0.78f).toInt()
            )
        }
        dialog.setOnDismissListener {
            if (guestConsoleDialog === dialog) {
                guestConsoleDialog = null
                guestConsoleTextView = null
                guestConsoleScrollView = null
            }
        }
        dialog.show()
    }

    private fun sendGuestConsoleInput(command: String) {
        val output = activeGuestInput
        if (activeGuestProcess == null || output == null) {
            toast("No active guest serial input.")
            return
        }
        try {
            output.write((command + "\n").toByteArray(Charsets.UTF_8))
            output.flush()
            appendGuestConsole("> $command\n")
        } catch (error: Exception) {
            appendGuestConsole("[AndroidNetLab] Could not send serial input: ${error.message ?: "I/O error"}\n")
        }
    }

    private fun stopSelectedVm() {
        if (!isGuestActive()) {
            toast("No active guest to stop.")
            return
        }
        stopRequested = true
        updateGuestState(VmState.STOPPING, "Stop requested. Prefer 'poweroff' inside the guest before forcing the process to exit.")
        appendGuestConsole("\n[AndroidNetLab] Stop requested.\n")
        val process = activeGuestProcess
        if (process == null) return // Startup thread will observe stopRequested before launch.
        process.destroy()
        Thread({
            try {
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            } catch (_: Exception) {
                process.destroyForcibly()
            }
        }, "qemu-stop-cleanup").start()
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
        body.addView(section("RUNTIME DIAGNOSTICS"))
        body.addView(infoCard("QEMU executable", qemuRuntimeSubtitle(), qemuProbeDetail))
        body.addView(chip("Import / retest Android ARM64 QEMU") { if (!qemuProbeRunning) confirmImportQemuBinary() },
            LinearLayout.LayoutParams(-1, dp(40)))
        body.addView(label("An executable smoke-test pass is not a guest-boot pass. Guest kernel/rootfs launch, live serial I/O, reliable stop/restart, and isolated networking still need integration and device testing.", 9, muted)
            .apply { setPadding(dp(2), dp(4), dp(2), dp(5)) })
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