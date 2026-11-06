package com.retrochan.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import java.io.File

class GameActivity : AppCompatActivity() {
    private lateinit var retro: GLRetroView
    private lateinit var romFile: File
    private val saveDir get() = File(filesDir, "saves/${romFile.nameWithoutExtension}").apply { mkdirs() }
    private val sramFile get() = File(saveDir, "game.srm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        romFile = File(intent.getStringExtra("rom")!!)
        val core = Cores.forFile(romFile.name) ?: return finish()
        val corePath = File(applicationInfo.nativeLibraryDir, "lib${core}_libretro_android.so").absolutePath

        val data = GLRetroViewData(this).apply {
            coreFilePath = corePath
            gameFilePath = romFile.absolutePath
            systemDirectory = File(filesDir, "system").apply { mkdirs() }.absolutePath
            savesDirectory = saveDir.absolutePath
            if (sramFile.exists()) saveRAMState = sramFile.readBytes()
        }
        retro = GLRetroView(this, data)
        lifecycle.addObserver(retro)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(retro, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(buildControls(), LinearLayout.LayoutParams(-1, dp(230)))
        setContentView(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun btn(label: String, key: Int, size: Int = 56) = Button(this).apply {
        text = label; textSize = 13f; setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#3A3158"))
        layoutParams = ViewGroup.MarginLayoutParams(dp(size), dp(size)).apply { setMargins(4, 4, 4, 4) }
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { retro.sendKeyEvent(KeyEvent.ACTION_DOWN, key); v.alpha = .5f }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { retro.sendKeyEvent(KeyEvent.ACTION_UP, key); v.alpha = 1f }
            }
            true
        }
    }

    private fun spacer() = Space(this).apply { layoutParams = ViewGroup.MarginLayoutParams(dp(56), dp(56)) }

    private fun grid(vararg cells: View) = GridLayout(this).apply {
        columnCount = 3; cells.forEach { addView(it) }
    }

    private fun buildControls(): View {
        val f = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#14111F")) }
        val dpad = grid(spacer(), btn("▲", KeyEvent.KEYCODE_DPAD_UP), spacer(),
            btn("◀", KeyEvent.KEYCODE_DPAD_LEFT), spacer(), btn("▶", KeyEvent.KEYCODE_DPAD_RIGHT),
            spacer(), btn("▼", KeyEvent.KEYCODE_DPAD_DOWN), spacer())
        val face = grid(btn("X", KeyEvent.KEYCODE_BUTTON_X), btn("Y", KeyEvent.KEYCODE_BUTTON_Y), spacer(),
            btn("B", KeyEvent.KEYCODE_BUTTON_B), spacer(), btn("A", KeyEvent.KEYCODE_BUTTON_A),
            spacer(), spacer(), spacer())
        f.addView(dpad, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.BOTTOM).apply { setMargins(dp(10), 0, 0, dp(10)) })
        f.addView(face, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply { setMargins(0, 0, dp(10), dp(10)) })

        val top = LinearLayout(this)
        top.addView(btn("L", KeyEvent.KEYCODE_BUTTON_L1, 48)); top.addView(btn("R", KeyEvent.KEYCODE_BUTTON_R1, 48))
        f.addView(top, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(4), dp(10), 0) })
        val topL = LinearLayout(this)
        topL.addView(Button(this).apply { text = "☰ Menu"; setOnClickListener { showMenu() } })
        f.addView(topL, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(10), dp(4), 0, 0) })

        val mid = LinearLayout(this)
        mid.addView(btn("SEL", KeyEvent.KEYCODE_BUTTON_SELECT, 44)); mid.addView(btn("START", KeyEvent.KEYCODE_BUTTON_START, 44))
        f.addView(mid, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply { bottomMargin = dp(10) })
        return f
    }

    private fun slot(n: Int) = File(saveDir, "state$n.sav")

    private fun showMenu() {
        val items = arrayOf("Simpan State 1", "Simpan State 2", "Simpan State 3",
            "Muat State 1", "Muat State 2", "Muat State 3", "Reset", "Keluar")
        AlertDialog.Builder(this).setTitle("Retro-Chan").setItems(items) { _, i ->
            when (i) {
                in 0..2 -> { slot(i + 1).writeBytes(retro.serializeState()); toast("State ${i + 1} tersimpan") }
                in 3..5 -> if (slot(i - 2).exists()) { retro.unserializeState(slot(i - 2).readBytes()); toast("State ${i - 2} dimuat") } else toast("Slot kosong")
                6 -> retro.reset()
                7 -> finish()
            }
        }.show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onPause() {
        super.onPause()
        runCatching { sramFile.writeBytes(retro.serializeSRAM()) } // save in-game otomatis
    }
}
