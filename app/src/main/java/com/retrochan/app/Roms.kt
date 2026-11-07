package com.retrochan.app

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.zip.ZipInputStream

object Roms {
    private val supportExt = setOf("bin", "img", "sub", "ccd", "sbi")   // pendamping .cue
    fun dir(ctx: Context) = File(ctx.filesDir, "roms").apply { mkdirs() }
    fun systemDir(ctx: Context) = File(ctx.filesDir, "system").apply { mkdirs() }

    private fun ext(n: String) = n.substringAfterLast('.', "").lowercase()
    private fun acceptable(n: String) = Cores.coreFor(n) != null || ext(n) in supportExt
    fun isRom(f: File) = Cores.coreFor(f.name, f.length()) != null

    /** Simpan ROM (atau .zip berisi ROM). Return nama ROM atau null bila tidak didukung/gagal. */
    fun importStream(ctx: Context, rawName: String, input: InputStream): String? {
        val name = File(rawName).name
        if (ext(name) == "zip") return importZip(ctx, input)
        if (!acceptable(name)) return null
        val f = copy(File(dir(ctx), name), input) ?: return null
        return if (isRom(f) || ext(name) in supportExt) name else { f.delete(); null }
    }

    private fun importZip(ctx: Context, input: InputStream): String? {
        var first: String? = null
        ZipInputStream(input.buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = File(e.name).name
                if (!e.isDirectory && acceptable(n)) {
                    val f = copy(File(dir(ctx), n), z, closeInput = false)
                    if (f != null && first == null && isRom(f)) first = n
                }
                e = z.nextEntry
            }
        }
        return first
    }

    /** Salin aman: tulis ke .part dulu lalu rename, supaya tidak ada file setengah jadi. */
    private fun copy(dst: File, src: InputStream, closeInput: Boolean = true): File? {
        val tmp = File(dst.path + ".part")
        return try {
            tmp.outputStream().use { src.copyTo(it, 64 * 1024) }
            if (tmp.length() == 0L) { tmp.delete(); return null }
            dst.delete()
            if (tmp.renameTo(dst)) dst else { tmp.delete(); null }
        } catch (e: Exception) {
            tmp.delete(); null
        } finally {
            if (closeInput) runCatching { src.close() }
        }
    }

    /** Unduh dari link langsung. Panggil dari thread background. */
    fun download(ctx: Context, url: String): String? {
        var u = url.trim()
        repeat(6) {
            val c = URL(u).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "Retro-Chan")
                val code = c.responseCode
                if (code in 300..399) { u = URL(URL(u), c.getHeaderField("Location")).toString(); return@repeat }
                if (code != 200) return null
                val cd = c.getHeaderField("Content-Disposition")
                val fromHeader = cd?.substringAfter("filename=", "")?.trim('"', ' ', ';')?.ifEmpty { null }
                val raw = fromHeader ?: URL(u).path.substringAfterLast('/')
                val name = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw).ifEmpty { "game.rom" }
                return importStream(ctx, name, c.inputStream)
            } finally { c.disconnect() }
        }
        return null
    }
}
