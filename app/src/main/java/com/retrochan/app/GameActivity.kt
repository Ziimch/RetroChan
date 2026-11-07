package com.retrochan.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import java.io.File

class GameActivity : AppCompatActivity() {
    private lateinit var retro: GLRetroView
    private lateinit var controls: ControlsView
    private lateinit var romFile: File
    private val prefs by lazy { getSharedPreferences("retrochan", Context.MODE_PRIVATE) }
    private val saveDir get() = File(filesDir, "saves/${romFile.nameWithoutExtension}").apply { mkdirs() }
    private val sramFile get() = File(saveDir, "game.srm")

    private var speed = 1
    private var muted = false
    private var lastSram: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cores.init(this)
        romFile = File(intent.getStringExtra("rom") ?: return finish())
        val core = Cores.coreFor(romFile.name, romFile.length()) ?: run { toast("Format/core tidak tersedia"); return finish() }
        val corePath = File(applicationInfo.nativeLibraryDir, "lib${core}_libretro_android.so").absolutePath
        if (!File(corePath).exists()) { toast("Core $core tidak ditemukan di APK"); return finish() }
        runCatching { File(filesDir, "run.flag").writeText(romFile.nameWithoutExtension) }

        val data = GLRetroViewData(this).apply {
            coreFilePath = corePath
            gameFilePath = romFile.absolutePath
            systemDirectory = File(filesDir, "system").apply { mkdirs() }.absolutePath
            savesDirectory = saveDir.absolutePath
            shader = when (prefs.getInt("shader", 0)) { 1 -> ShaderConfig.CRT; 2 -> ShaderConfig.LCD; else -> ShaderConfig.Default }
            if (sramFile.exists()) { lastSram = sramFile.readBytes(); saveRAMState = lastSram }
        }
        retro = GLRetroView(this, data)
        lifecycle.addObserver(retro)

        controls = ControlsView(this,
            { k, down -> retro.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, k) },
            { showMenu() },
            { setSpeed(if (speed == 1) 2 else 1) })
        applyControlPrefs()

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

    // ---------- Pengaturan kontrol ----------
    private fun applyControlPrefs() {
        controls.scale = floatArrayOf(0.85f, 1f, 1.15f)[prefs.getInt("scale_idx", 1)]
        controls.alphaLevel = intArrayOf(80, 120, 180)[prefs.getInt("alpha_idx", 1)]
        controls.haptic = prefs.getBoolean("haptic", true)
        controls.leftHanded = prefs.getBoolean("lefty", false)
        controls.turbo = prefs.getBoolean("turbo", false)
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

    // ---------- Kecepatan & suara ----------
    private fun setSpeed(n: Int) {
        speed = n
        retro.frameSpeed = n
        controls.ffActive = n > 1
    }

    // ---------- Menu ----------
    private fun slot(n: Int) = File(saveDir, "state$n.sav")
    private fun quick() = File(saveDir, "quick.sav")
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun onOff(b: Boolean) = if (b) "Hidup" else "Mati"

    private fun showMenu() {
        controls.releaseAll()
        val items = arrayOf(
            "▶  Lanjutkan",
            "⚡  Simpan Cepat",
            "⚡  Muat Cepat",
            "💾  Simpan State (3 slot)",
            "📂  Muat State (3 slot)",
            "⏩  Kecepatan: ${speed}x",
            "🔊  Suara: ${onOff(!muted)}",
            "🔄  Orientasi Layar",
            "⚙  Pengaturan Kontrol & Layar",
            "↺  Reset Game",
            "✖  Keluar")
        AlertDialog.Builder(this).setTitle(romFile.nameWithoutExtension).setItems(items) { _, i ->
            when (i) {
                1 -> saveState(quick())
                2 -> loadState(quick())
                3 -> pickSlot("Simpan ke slot") { n -> saveState(slot(n)) }
                4 -> pickSlot("Muat dari slot") { n -> loadState(slot(n)) }
                5 -> { setSpeed(speed % 3 + 1); toast("Kecepatan ${speed}x") }
                6 -> { muted = !muted; retro.audioEnabled = !muted; toast("Suara ${onOff(!muted)}") }
                7 -> pickOrientation()
                8 -> showSettings()
                9 -> retro.reset()
                10 -> finish()
            }
        }.show()
    }

    private fun saveState(f: File) {
        val ok = runCatching {
            val b = retro.serializeState()
            check(b.isNotEmpty())
            val t = File(f.path + ".tmp"); t.writeBytes(b); f.delete(); check(t.renameTo(f))
        }.isSuccess
        toast(if (ok) "Tersimpan" else "Core ini tidak mendukung save state")
    }

    private fun loadState(f: File) {
        if (!f.exists()) { toast("Slot kosong"); return }
        val ok = runCatching { retro.unserializeState(f.readBytes()) }.getOrDefault(false)
        toast(if (ok) "Dimuat" else "Gagal memuat state")
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

    private fun cycle(key: String, n: Int, def: Int) = prefs.edit().putInt(key, (prefs.getInt(key, def) + 1) % n).apply()
    private fun toggle(key: String, def: Boolean) = prefs.edit().putBoolean(key, !prefs.getBoolean(key, def)).apply()

    private fun showSettings() {
        val items = arrayOf(
            "Ukuran tombol: ${arrayOf("Kecil", "Sedang", "Besar")[prefs.getInt("scale_idx", 1)]}",
            "Transparansi (lanskap): ${arrayOf("Tinggi", "Sedang", "Rendah")[prefs.getInt("alpha_idx", 1)]}",
            "Getar tombol: ${onOff(prefs.getBoolean("haptic", true))}",
            "Posisi tombol: ${if (prefs.getBoolean("lefty", false)) "Kidal" else "Normal"}",
            "Turbo A/B (tahan tombol): ${onOff(prefs.getBoolean("turbo", false))}",
            "Filter layar: ${arrayOf("Normal", "CRT", "LCD")[prefs.getInt("shader", 0)]}  (berlaku saat game dibuka ulang)")
        AlertDialog.Builder(this).setTitle("Pengaturan").setItems(items) { _, i ->
            when (i) {
                0 -> cycle("scale_idx", 3, 1)
                1 -> cycle("alpha_idx", 3, 1)
                2 -> toggle("haptic", true)
                3 -> toggle("lefty", false)
                4 -> toggle("turbo", false)
                5 -> cycle("shader", 3, 0)
            }
            applyControlPrefs(); showSettings()
        }.setPositiveButton("Selesai", null).show()
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
        runCatching {
            val b = retro.serializeSRAM()
            if (b.isNotEmpty() && !b.contentEquals(lastSram)) {
                val t = File(sramFile.path + ".tmp"); t.writeBytes(b); sramFile.delete(); t.renameTo(sramFile); lastSram = b
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::retro.isInitialized) handler.postDelayed(autoSave, 30_000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoSave)
        if (isFinishing) File(filesDir, "run.flag").delete()   // keluar normal
        if (!::retro.isInitialized) return
        controls.releaseAll()
        saveSram()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) saveSram()
    }

    override fun onDestroy() {
        super.onDestroy()
        // proses game dimatikan agar state native core bersih untuk game berikutnya
        if (isFinishing) android.os.Process.killProcess(android.os.Process.myPid())
    }
}
