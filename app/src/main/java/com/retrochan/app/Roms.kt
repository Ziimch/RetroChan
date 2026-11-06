package com.retrochan.app

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.zip.ZipInputStream

object Roms {
    fun dir(ctx: Context) = File(ctx.filesDir, "roms").apply { mkdirs() }

    /** Simpan ROM. Jika .zip, ambil ROM pertama yang didukung di dalamnya. Return nama file tersimpan atau null. */
    fun importStream(ctx: Context, rawName: String, input: InputStream): String? {
        val name = File(rawName).name
        if (name.lowercase().endsWith(".zip")) {
            ZipInputStream(input.buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val n = File(e.name).name
                    if (!e.isDirectory && Cores.forFile(n) != null) {
                        File(dir(ctx), n).outputStream().use { z.copyTo(it) }
                        return n
                    }
                    e = z.nextEntry
                }
            }
            return null
        }
        if (Cores.forFile(name) == null) return null
        val tmp = File(dir(ctx), "$name.part")
        tmp.outputStream().use { input.copyTo(it) }
        val out = File(dir(ctx), name)
        out.delete(); tmp.renameTo(out)
        return name
    }

    /** Unduh dari link langsung. Return nama file atau null. Dipanggil dari thread background. */
    fun download(ctx: Context, url: String): String? {
        var u = url.trim()
        repeat(5) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Retro-Chan")
            val code = c.responseCode
            if (code in 300..399) { u = URL(URL(u), c.getHeaderField("Location")).toString(); c.disconnect(); return@repeat }
            if (code != 200) { c.disconnect(); return null }
            val cd = c.getHeaderField("Content-Disposition")
            val fromHeader = cd?.substringAfter("filename=", "")?.trim('"', ' ', ';')?.ifEmpty { null }
            val name = URLDecoder.decode(fromHeader ?: URL(u).path.substringAfterLast('/'), "UTF-8").ifEmpty { "game.rom" }
            return c.inputStream.use { importStream(ctx, name, it) }
        }
        return null
    }
}
