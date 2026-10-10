package dev.lozadatech.androidnetlab

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var metrics: TextView
    private val startTime = System.currentTimeMillis()
    private val refresh = object : Runnable {
        override fun run() { updateMetrics(); handler.postDelayed(this, 2000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(12, 18, 30)
        window.navigationBarColor = android.graphics.Color.rgb(12, 18, 30)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(18))
            setBackgroundColor(android.graphics.Color.rgb(12, 18, 30))
        }
        root.addView(label("ANDROID NETLAB", 13f, 0xFF70D6C1.toInt(), true))
        root.addView(label("Prototype 0.1", 28f, 0xFFF4F7FC.toInt(), true))
        root.addView(label("Device diagnostics · Linux lab baseline", 14f, 0xFF9EADC2.toInt()))
        metrics = label("Collecting measurements…", 15f, 0xFFE5ECF5.toInt())
        metrics.setPadding(dp(14), dp(14), dp(14), dp(14))
        metrics.setBackgroundColor(0xFF182438.toInt())
        val scroll = ScrollView(this).apply { addView(metrics) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(20) })
        root.addView(label("EVENT LOG", 12f, 0xFF70D6C1.toInt(), true).apply { setPadding(0, dp(18), 0, dp(8)) })
        root.addView(label("• App started\n• Runtime engine: not integrated yet\n• Linux guest: not booted\n• Virtual network: not configured", 12f, 0xFFB8C5D8.toInt()).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12)); setBackgroundColor(0xFF182438.toInt())
        })
        setContentView(root)
        updateMetrics()
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }

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
        val cpuMs = Debug.threadCpuTimeNanos() / 1_000_000
        metrics.text = "DEVICE MEMORY\n" +
            "Total RAM: ${mem.totalMem.mb()} MiB\n" +
            "Available RAM: ${mem.availMem.mb()} MiB\n" +
            "Low-memory state: ${if (mem.lowMemory) "YES" else "No"}\n\n" +
            "APP MEMORY\n" +
            "App PSS: ${String.format(Locale.US, "%.1f", pssMb)} MiB\n" +
            "Runtime heap used: $appUsed MiB\n" +
            "Runtime heap max: ${runtime.maxMemory() / (1024 * 1024)} MiB\n\n" +
            "PROCESS / SESSION\n" +
            "Session uptime: ${elapsed}s\n" +
            "Main-thread CPU time: ${cpuMs}ms\n\n" +
            "VIRTUALIZATION\nEngine: Not integrated\nGuest RAM: Not allocated\nGuest state: Not running\nVirtual links: 0\n\n" +
            "Measurements refresh every 2 seconds. Keep the app foregrounded for repeatable tests."
    }

    private fun Long.mb(): Long = this / (1024L * 1024L)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(dp(4).toFloat(), 1f)
    }
}
