package dev.lozadatech.androidnetlab.next

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(0xFF10151D.toInt())
        }
        content.addView(label("AndroidNetLab", 30, 0xFFF2F6FC.toInt(), true))
        content.addView(label("QEMU-first • Android ARM64", 15, 0xFF9DB4CD.toInt()))
        content.addView(space(24))
        content.addView(panel("RUNTIME STATUS", "Not integrated yet",
            "This is the clean app foundation. It does not claim to boot a VM until Android-compatible QEMU is packaged and verified."))
        content.addView(space(14))
        content.addView(panel("NEXT MILESTONE", "Boot one real Linux guest",
            "Build QEMU for Android ARM64, launch it inside the app sandbox, boot the minimal AArch64 guest, and capture its serial console."))
        content.addView(space(14))
        content.addView(panel("DESIGN RULE", "Independent VMs first",
            "Each VM must have its own guest kernel, RAM, and writable disk state. Virtual Ethernet links come after reliable boot and shutdown."))
        content.addView(space(24))
        content.addView(label("Development build • runtime work in progress", 12, 0xFF8090A5.toInt()))
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun panel(title: String, headline: String, detail: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        setBackgroundColor(0xFF1B2532.toInt())
        addView(label(title, 11, 0xFF8FB8E8.toInt(), true))
        addView(space(8))
        addView(label(headline, 20, 0xFFF2F6FC.toInt(), true))
        addView(space(8))
        addView(label(detail, 14, 0xFFC1CDDA.toInt()))
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.START
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun space(heightDp: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
