package com.retrochan.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var counter: TextView
    private var query = ""
    private val prefs by lazy { getSharedPreferences("retrochan", MODE_PRIVATE) }

    private val cCard = 0xFF221D33.toInt()
    private val cPink = 0xFFFF7BB5.toInt()
    private val cViolet = 0xFF6C4DFF.toInt()
    private val cMuted = 0xFF9A93B5.toInt()

    // Sumber game gratis & legal (homebrew / public domain / freeware)
    private val sources = listOf(
        "Homebrew Hub – game Game Boy gratis" to "https://hh.gbdev.io/",
        "PDRoms – ROM public domain" to "https://pdroms.de/",
        "itch.io – game NES gratis" to "https://itch.io/games/free/tag-nes",
        "Internet Archive – homebrew" to "https://archive.org/search?query=homebrew+rom&sort=-downloads"
    )

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        toast("Mengimpor…")
        thread {
            var ok = 0
            uris.forEach { if (runCatching { importUri(it) }.getOrDefault(false)) ok++ }
            runOnUiThread { refresh(); toast(if (ok > 0) "$ok game ditambahkan" else "Format tidak didukung") }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(10)) }

        // Header: logo + judul
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val icon = ImageView(this).apply {
            setImageDrawable(LayerDrawable(arrayOf(
                ContextCompat.getDrawable(context, R.drawable.ic_launcher_bg)!!,
                ContextCompat.getDrawable(context, R.drawable.ic_launcher_fg)!!)))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, v.width * 0.24f) }
            }
            clipToOutline = true
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(54), dp(54)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        titles.addView(TextView(this).apply { text = "Retro-Chan"; textSize = 26f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
        titles.addView(TextView(this).apply { text = "Mainkan game klasik, kapan saja"; textSize = 12.5f; setTextColor(cMuted) })
        header.addView(titles)
        root.addView(header)

        // Tombol aksi
        val actions = LinearLayout(this).apply { setPadding(0, dp(18), 0, dp(12)) }
        actions.addView(pill("＋  Tambah Game", true) { picker.launch(arrayOf("*/*")) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) })
        actions.addView(pill("⬇  Cari / Unduh", false) { showSources() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) })
        root.addView(actions)

        // Pencarian
        val search = EditText(this).apply {
            hint = "Cari game…"; setHintTextColor(cMuted); setTextColor(Color.WHITE); textSize = 14f
            background = rounded(cCard, 14); setPadding(dp(16), dp(12), dp(16), dp(12)); isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); refresh() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        root.addView(search)
        counter = TextView(this).apply { textSize = 12f; setTextColor(cMuted); setPadding(dp(4), dp(14), 0, dp(6)) }
        root.addView(counter)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun pill(label: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 14f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(14), 0, dp(14))
        background = if (primary) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cPink, cViolet))
            .apply { cornerRadius = dp(16).toFloat() } else rounded(cCard, 16, cViolet)
        setOnClickListener { onClick() }
    }

    private fun importUri(uri: Uri): Boolean {
        val name = contentResolver.query(uri, null, null, null, null)?.use {
            val i = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && it.moveToFirst()) it.getString(i) else null
        } ?: uri.lastPathSegment ?: return false
        return contentResolver.openInputStream(uri)?.use { Roms.importStream(this, name, it) } != null
    }

    private fun refresh() {
        list.removeAllViews()
        val all = Roms.dir(this).listFiles()?.filter { Cores.forFile(it.name) != null } ?: emptyList()
        val roms = all.filter { it.nameWithoutExtension.contains(query, true) }
            .sortedWith(compareByDescending<File> { prefs.getLong("played_${it.name}", 0L) }.thenBy { it.name.lowercase() })
        counter.text = "${roms.size} game"
        if (roms.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (all.isEmpty()) "🎮\n\nBelum ada game\n\nTekan “Tambah Game” untuk memilih ROM milikmu\natau “Cari / Unduh” untuk sumber game gratis."
                else "Game tidak ditemukan"
                setTextColor(cMuted); gravity = Gravity.CENTER; textSize = 14f; setPadding(0, dp(70), 0, 0)
            })
            return
        }
        roms.forEach { list.addView(card(it), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }) }
    }

    private fun card(f: File): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = rounded(cCard, 18)
            setPadding(dp(14), dp(12), dp(14), dp(12)); isClickable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, GameActivity::class.java).putExtra("rom", f.absolutePath)) }
            setOnLongClickListener { confirmDelete(f); true }
        }
        row.addView(TextView(this).apply {
            text = Cores.badge(f.name); gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; textSize = 11f; background = rounded(Cores.color(f.name), 12)
        }, LinearLayout.LayoutParams(dp(50), dp(50)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(8), 0) }
        col.addView(TextView(this).apply {
            text = f.nameWithoutExtension; textSize = 16f; setTextColor(Color.WHITE); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        })
        col.addView(TextView(this).apply {
            text = "${Cores.systemName(f.name)}  •  ${"%.1f".format(f.length() / 1048576.0)} MB"; textSize = 12f; setTextColor(cMuted)
        })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply { text = "▶"; textSize = 18f; setTextColor(cPink) })
        return row
    }

    private fun confirmDelete(f: File) {
        AlertDialog.Builder(this).setTitle(f.nameWithoutExtension)
            .setItems(arrayOf("Hapus game (data save tetap)", "Hapus game + data save")) { _, i ->
                f.delete()
                if (i == 1) File(filesDir, "saves/${f.nameWithoutExtension}").deleteRecursively()
                refresh()
            }.setNegativeButton("Batal", null).show()
    }

    private fun showSources() {
        val items = arrayOf("⬇  Unduh dari link langsung…") + sources.map { it.first }
        AlertDialog.Builder(this).setTitle("Cari / Unduh game").setItems(items) { _, i ->
            if (i == 0) askUrl() else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sources[i - 1].second)))
        }.show()
    }

    private fun askUrl() {
        val input = EditText(this).apply { hint = "https://…/game.gb atau .zip"; inputType = InputType.TYPE_TEXT_VARIATION_URI; setSingleLine() }
        val box = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
        AlertDialog.Builder(this).setTitle("Tempel link unduhan").setView(box)
            .setPositiveButton("Unduh") { _, _ ->
                val url = input.text.toString().trim()
                if (!url.startsWith("http")) { toast("Link tidak valid"); return@setPositiveButton }
                toast("Mengunduh…")
                thread {
                    val name = runCatching { Roms.download(this, url) }.getOrNull()
                    runOnUiThread { refresh(); toast(if (name != null) "Selesai: $name" else "Gagal / format tidak didukung") }
                }
            }.setNegativeButton("Batal", null).show()
    }
}
