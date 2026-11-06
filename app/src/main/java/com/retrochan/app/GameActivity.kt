package com.retrochan.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import java.io.File

class GameActivity : AppCompatActivity() {
    private lateinit var retro: GLRetroView
    private lateinit var controls: ControlsView
    private lateinit var romFile: File
    private val prefs by lazy { getSharedPreferences("retrochan", Context.MODE_PRIVATE) }
    private val saveDir get() = File(filesDir, "saves/${romFile.nameWithoutExtension}").apply { mkdirs() }
    private val sramFile get() = File(saveDir, "game.srm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        romFile = File(intent.getStringExtra("rom")!!)
        val core = Cores.forFile(romFile.name) ?: return finish()
        val corePath = File(applicationInfo.nativeLibraryDir, "lib${core}_libretro_android.so").absolutePath

        if (!File(corePath).exists()) { toast("Core $core tidak ditemukan di APK"); return finish() }
        prefs.edit().putLong("played_${romFile.name}", System.currentTimeMillis()).apply()

        val data = GLRetroViewData(this).apply {
            coreFilePath = corePath
            gameFilePath = romFile.absolutePath
            systemDirectory = File(filesDir, "system").apply { mkdirs() }.absolutePath
            savesDirectory = saveDir.absolutePath
            if (sramFile.exists()) saveRAMState = sramFile.readBytes()
        }
        retro = GLRetroView(this, data)
        lifecycle.addObserver(retro)

        controls = ControlsView(this,
            { k, down -> retro.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, k) },
            { showMenu() })

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(retro); root.addView(controls)
        setContentView(root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyOrientation()
        applyLayout()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { showMenu() }
        })
    }

    // ---------- Orientasi & layout ----------
    private fun applyOrientation() {
        requestedOrientation = when (prefs.getInt("orient", 0)) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    private fun applyLayout() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val ch = (300 * resources.displayMetrics.density).toInt()
        retro.layoutParams = FrameLayout.LayoutParams(-1, -1).apply { if (!land) bottomMargin = ch }
        controls.layoutParams = if (land) FrameLayout.LayoutParams(-1, -1)
        else FrameLayout.LayoutParams(-1, ch, Gravity.BOTTOM)
        controls.translucent = land
        controls.setBackgroundColor(if (land) Color.TRANSPARENT else 0xFF14111F.toInt())
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (land) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayout()
    }

    // ---------- Menu ----------
    private fun slot(n: Int) = File(saveDir, "state$n.sav")
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun showMenu() {
        val items = arrayOf("💾  Simpan State", "📂  Muat State", "🔄  Orientasi Layar", "↺  Reset Game", "✖  Keluar")
        AlertDialog.Builder(this).setTitle(romFile.nameWithoutExtension).setItems(items) { _, i ->
            when (i) {
                0 -> pickSlot("Simpan ke slot") { n -> slot(n).writeBytes(retro.serializeState()); toast("Tersimpan di slot $n") }
                1 -> pickSlot("Muat dari slot") { n ->
                    if (slot(n).exists()) { retro.unserializeState(slot(n).readBytes()); toast("Slot $n dimuat") } else toast("Slot $n kosong")
                }
                2 -> pickOrientation()
                3 -> retro.reset()
                4 -> finish()
            }
        }.show()
    }

    private fun pickSlot(title: String, action: (Int) -> Unit) {
        val names = Array(3) { n -> "Slot ${n + 1}" + if (slot(n + 1).exists()) "  (terisi)" else "  (kosong)" }
        AlertDialog.Builder(this).setTitle(title).setItems(names) { _, i -> action(i + 1) }.show()
    }

    private fun pickOrientation() {
        val names = arrayOf("Otomatis (ikuti sensor)", "Potret", "Lanskap")
        AlertDialog.Builder(this).setTitle("Orientasi layar")
            .setSingleChoiceItems(names, prefs.getInt("orient", 0)) { d, i ->
                prefs.edit().putInt("orient", i).apply(); applyOrientation(); d.dismiss()
            }.show()
    }

    // ---------- Gamepad fisik / Bluetooth ----------
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val gp = (e.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (e.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (::retro.isInitialized && gp && (KeyEvent.isGamepadButton(e.keyCode) || e.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER)) {
            retro.sendKeyEvent(e.action, e.keyCode); return true
        }
        return super.dispatchKeyEvent(e)
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (::retro.isInitialized && (e.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, e.getAxisValue(MotionEvent.AXIS_HAT_X), e.getAxisValue(MotionEvent.AXIS_HAT_Y))
            retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y))
            return true
        }
        return super.onGenericMotionEvent(e)
    }

    // ---------- Auto-save SRAM (save in-game) ----------
    private val handler = Handler(Looper.getMainLooper())
    private val autoSave = object : Runnable {
        override fun run() { saveSram(); handler.postDelayed(this, 30_000) }
    }

    private fun saveSram() {
        if (!::retro.isInitialized) return
        runCatching { val b = retro.serializeSRAM(); if (b.isNotEmpty()) sramFile.writeBytes(b) }
    }

    override fun onResume() {
        super.onResume()
        if (::retro.isInitialized) handler.postDelayed(autoSave, 30_000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoSave)
        if (!::retro.isInitialized) return
        controls.releaseAll()
        saveSram()
    }
}
