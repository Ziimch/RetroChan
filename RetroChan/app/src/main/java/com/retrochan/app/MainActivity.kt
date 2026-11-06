package com.retrochan.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private val romDir get() = File(filesDir, "roms").apply { mkdirs() }

    // Sumber game GRATIS & legal (homebrew / public domain / freeware)
    private val sources = listOf(
        "Libretro - Freeware & Homebrew" to "https://buildbot.libretro.com/assets/cores/",
        "Internet Archive - Homebrew" to "https://archive.org/search?query=homebrew+rom&sort=-downloads",
        "itch.io - Game retro gratis" to "https://itch.io/games/free/tag-nes",
        "Pico/GB Studio - Game GB gratis" to "https://gbstudiocentral.com/",
        "Zophar's Domain - Homebrew" to "https://www.zophar.net/"
    )

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { import(it) }; refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 90, 40, 40) }
        root.addView(TextView(this).apply {
            text = "Retro-Chan"; textSize = 30f; setTextColor(Color.parseColor("#FF7BB5")); typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply { text = "NES • SNES • GB/GBC • GBA • Genesis • SMS • GG • PCE"; setTextColor(Color.GRAY); textSize = 12f })
        val row = LinearLayout(this).apply { setPadding(0, 30, 0, 20) }
        row.addView(Button(this).apply { text = "+ Tambah Game"; setOnClickListener { picker.launch(arrayOf("*/*")) } },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(Button(this).apply { text = "Cari Game"; setOnClickListener { showSources() } },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(row)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun import(uri: Uri) {
        val name = contentResolver.query(uri, null, null, null, null)?.use {
            val i = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst()) it.getString(i) else null
        } ?: return
        if (Cores.forFile(name) == null) { Toast.makeText(this, "Format tidak didukung: $name", Toast.LENGTH_SHORT).show(); return }
        contentResolver.openInputStream(uri)?.use { i -> File(romDir, name).outputStream().use { o -> i.copyTo(o) } }
    }

    private fun refresh() {
        list.removeAllViews()
        val roms = romDir.listFiles()?.filter { Cores.forFile(it.name) != null }?.sortedBy { it.name } ?: emptyList()
        if (roms.isEmpty()) list.addView(TextView(this).apply {
            text = "Belum ada game.\nTekan “+ Tambah Game” untuk memilih ROM dari penyimpanan,\natau “Cari Game” untuk sumber game gratis."
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0, 120, 0, 0)
        })
        roms.forEach { f ->
            list.addView(TextView(this).apply {
                text = "${f.nameWithoutExtension}\n${Cores.systemName(f.name)}"
                setTextColor(Color.WHITE); textSize = 16f; setPadding(30, 28, 30, 28)
                setBackgroundColor(Color.parseColor("#221D33"))
                setOnClickListener { startActivity(Intent(this@MainActivity, GameActivity::class.java).putExtra("rom", f.absolutePath)) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle(f.nameWithoutExtension)
                        .setItems(arrayOf("Hapus game")) { _, _ -> f.delete(); refresh() }.show(); true
                }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = 12 })
        }
    }

    private fun showSources() {
        AlertDialog.Builder(this).setTitle("Sumber game gratis & legal")
            .setItems(sources.map { it.first }.toTypedArray()) { _, i ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sources[i].second)))
            }.show()
    }
}
