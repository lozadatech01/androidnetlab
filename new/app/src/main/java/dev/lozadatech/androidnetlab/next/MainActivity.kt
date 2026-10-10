package dev.lozadatech.androidnetlab.next

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
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
    private var windowCount = 2
    private var activeWindow = 1
    private var keyboardMode = KeyboardMode.APP
    private var showKeyboard = true
    private var layoutMode = LayoutMode.AUTO
    private lateinit var root: LinearLayout
    private lateinit var panels: LinearLayout
    private lateinit var keyboardHost: LinearLayout
    private lateinit var commandInput: EditText
    private lateinit var status: TextView

    private enum class KeyboardMode { APP, NATIVE }
    private enum class LayoutMode { AUTO, SIDE_BY_SIDE, STACKED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        render()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(topBar())
        root.addView(toolbar())
        status = text("Runtime unavailable • console layout preview only", 11, muted)
        status.setPadding(dp(2), dp(6), dp(2), dp(8))
        root.addView(status)
        panels = LinearLayout(this).apply {
            orientation = if (effectiveStacked()) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(panels)
        keyboardHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        root.addView(keyboardHost)
        setContentView(root)
        renderPanels()
        renderKeyboard()
    }

    private fun topBar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(text("AndroidNetLab", 21, white, true))
            addView(text("VM WORKSPACE", 10, blue, true))
        })
        addView(chip("Network overview") {
            status.text = "Network overview is planned; real QEMU networking is not connected yet."
        })
    }

    private fun toolbar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(12), 0, dp(4))
        addView(text("WINDOWS", 10, muted, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        for (count in 1..4) {
            addView(chip("$count") {
                windowCount = count
                activeWindow = activeWindow.coerceAtMost(count)
                renderPanels()
                renderKeyboard()
            }, LinearLayout.LayoutParams(dp(42), dp(38)).apply {
                marginStart = dp(4)
            })
        }
        addView(chip(if (showKeyboard) "Hide keys" else "Show keys") {
            showKeyboard = !showKeyboard
            if (!showKeyboard) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(root.windowToken, 0)
            }
            renderKeyboard()
        }, LinearLayout.LayoutParams(-2, dp(38)).apply { marginStart = dp(5) })
    }

    private fun renderPanels() {
        panels.removeAllViews()
        panels.orientation = if (effectiveStacked()) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        for (i in 1..windowCount) {
            val selected = i == activeWindow
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(8))
                setBackgroundColor(if (selected) 0xFF162438.toInt() else surface)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activeWindow = i
                    renderPanels()
                    renderKeyboard()
                    status.text = "VM $i selected • input will target this console when the runtime is integrated"
                }
            }
            panel.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(text("VM $i", 12, if (selected) blue else white, true).apply {
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
                addView(text(if (selected) "ACTIVE" else "TAP TO FOCUS", 9, if (selected) blue else muted, true))
            })
            panel.addView(space(10))
            panel.addView(text(if (selected) "Console selected" else "Console idle", 12, 0xFFA7F3D0.toInt()))
            panel.addView(space(5))
            panel.addView(text("QEMU runtime not connected", 10, muted))
            panel.addView(space(4))
            panel.addView(text("Tap panel to change input focus.", 10, muted))
            val params = LinearLayout.LayoutParams(
                if (effectiveStacked()) -1 else 0,
                if (effectiveStacked()) 0 else -1,
                1f
            )
            if (i > 1) {
                if (effectiveStacked()) params.topMargin = dp(5) else params.marginStart = dp(5)
            }
            panels.addView(panel, params)
        }
    }

    private fun renderKeyboard() {
        keyboardHost.removeAllViews()
        if (!showKeyboard) return
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(6))
            addView(text("INPUT → VM $activeWindow", 11, blue, true).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(chip(if (keyboardMode == KeyboardMode.APP) "App keyboard" else "Native keyboard") {
                keyboardMode = if (keyboardMode == KeyboardMode.APP) KeyboardMode.NATIVE else KeyboardMode.APP
                if (keyboardMode == KeyboardMode.NATIVE) {
                    showKeyboard = true
                    renderKeyboard()
                    commandInput.requestFocus()
                    commandInput.post {
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                            .showSoftInput(commandInput, InputMethodManager.SHOW_IMPLICIT)
                    }
                } else {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(commandInput.windowToken, 0)
                    renderKeyboard()
                }
            })
        }
        keyboardHost.addView(heading)
        commandInput = EditText(this).apply {
            hint = if (keyboardMode == KeyboardMode.APP) "Command input (runtime not connected)" else "Tap to open Android keyboard"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 13f
            singleLine = true
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(surface)
            isFocusableInTouchMode = true
            setOnClickListener {
                if (keyboardMode == KeyboardMode.NATIVE) {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
        keyboardHost.addView(commandInput, LinearLayout.LayoutParams(-1, dp(42)))
        if (keyboardMode == KeyboardMode.NATIVE) {
            keyboardHost.addView(text("Android's installed keyboard handles text entry. Panel focus stays in the app.", 10, muted).apply {
                setPadding(2, dp(6), 2, dp(6))
            })
            return
        }
        if (windowCount >= 2) {
            keyboardHost.addView(focusStrip())
        }
        val keyRows = listOf(
            listOf("Esc", "Tab", "Ctrl", "Alt", "↑", "⌫"),
            listOf(" / ", " - ", " | ", " : ", "←", "↓", "→"),
            listOf("Home", "End", "PgUp", "PgDn", "Enter", "Space")
        )
        keyRows.forEach { row ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(2), 0, dp(2))
            }
            row.forEach { key ->
                line.addView(chip(key.trim()) {
                    when (key.trim()) {
                        "Enter" -> status.text = "Input prepared for VM $activeWindow • runtime not connected"
                        "Space" -> commandInput.append(" ")
                        "⌫" -> {
                            val s = commandInput.text
                            if (s.isNotEmpty()) s.delete(s.length - 1, s.length)
                        }
                        "↑", "↓", "←", "→" -> commandInput.append(key.trim())
                        "Tab" -> commandInput.append("\t")
                        "Esc" -> commandInput.append("\u001b")
                        "Ctrl" -> status.text = "Ctrl modifier selected in prototype; guest key injection is not wired yet."
                        "Alt" -> status.text = "Alt modifier selected in prototype; guest key injection is not wired yet."
                        else -> commandInput.append(key)
                    }
                }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(3) })
            }
            keyboardHost.addView(line)
        }
    }

    private fun focusStrip(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(5), dp(3), dp(5), dp(3))
        setBackgroundColor(0xFF243449.toInt())
        addView(text("FOCUS", 9, 0xFFBFDBFE.toInt(), true).apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) }
        })
        for (i in 1..windowCount) {
            addView(chip("VM $i") {
                activeWindow = i
                renderPanels()
                renderKeyboard()
                status.text = "VM $i selected without closing the app keyboard"
            }, LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(3) })
        }
    }

    private fun effectiveStacked(): Boolean = when (layoutMode) {
        LayoutMode.STACKED -> true
        LayoutMode.SIDE_BY_SIDE -> false
        LayoutMode.AUTO -> resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    }

    private fun chip(caption: String, action: () -> Unit) = Button(this).apply {
        text = caption
        textSize = 10f
        isAllCaps = false
        setTextColor(white)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(surface2))
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(5), 0, dp(5), 0)
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
