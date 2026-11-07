package com.retrochan.app

import android.content.Context
import java.io.File

object Cores {
    // ekstensi -> nama core libretro (file: lib<core>_libretro_android.so)
    private val extMap = mapOf(
        "nes" to "fceumm", "fds" to "fceumm", "unf" to "fceumm", "unif" to "fceumm",
        "sfc" to "snes9x", "smc" to "snes9x", "swc" to "snes9x", "fig" to "snes9x",
        "gb" to "gambatte", "gbc" to "gambatte",
        "gba" to "mgba",
        "md" to "genesis_plus_gx", "gen" to "genesis_plus_gx", "smd" to "genesis_plus_gx",
        "sms" to "genesis_plus_gx", "gg" to "genesis_plus_gx", "sg" to "genesis_plus_gx",
        "32x" to "picodrive",
        "pce" to "mednafen_pce_fast", "sgx" to "mednafen_supergrafx",
        "a26" to "stella", "a78" to "prosystem", "lnx" to "handy",
        "ngp" to "mednafen_ngp", "ngc" to "mednafen_ngp",
        "ws" to "mednafen_wswan", "wsc" to "mednafen_wswan",
        "cue" to "pcsx_rearmed", "chd" to "pcsx_rearmed", "pbp" to "pcsx_rearmed", "iso" to "pcsx_rearmed", "m3u" to "pcsx_rearmed",
        "z64" to "mupen64plus_next_gles3", "n64" to "mupen64plus_next_gles3", "v64" to "mupen64plus_next_gles3"
    )

    @Volatile private var installed: Set<String> = emptySet()
    @Volatile private var ready = false

    /** Baca core apa saja yang benar-benar ada di APK. Dipanggil sekali per proses. */
    fun init(ctx: Context) {
        if (ready) return
        installed = File(ctx.applicationInfo.nativeLibraryDir).list()?.toSet() ?: emptySet()
        ready = true
    }

    private fun isInstalled(core: String) = "lib${core}_libretro_android.so" in installed
    private fun resolve(core: String) =
        if (core == "stella" && !isInstalled("stella") && isInstalled("stella2014")) "stella2014" else core
    private fun base(core: String) = if (core == "stella2014") "stella" else core
    private fun ext(name: String) = name.substringAfterLast('.', "").lowercase()

    /** Core untuk file ini, atau null bila format tidak dikenal / core tidak ada di APK. */
    fun coreFor(name: String, size: Long = -1): String? {
        val e = ext(name)
        val core = if (e == "bin") when {
            size in 0..524_288 -> "stella"            // ROM Atari 2600 kecil
            size in 0..16_777_216 -> "genesis_plus_gx" // ROM Genesis
            else -> null                               // file pendukung CD (bin)
        } else extMap[e]
        return core?.let { resolve(it) }?.takeIf { isInstalled(it) }
    }

    fun isHeavy(core: String?) = core == "pcsx_rearmed" || core == "mupen64plus_next_gles3"

    fun systemName(name: String, size: Long = -1) = when (ext(name)) {
        "nes", "unf", "unif" -> "NES"; "fds" -> "Famicom Disk"; "sfc", "smc", "swc", "fig" -> "Super Nintendo"
        "gb" -> "Game Boy"; "gbc" -> "Game Boy Color"; "gba" -> "Game Boy Advance"
        "md", "gen", "smd" -> "Sega Genesis"; "sms" -> "Master System"; "gg" -> "Game Gear"; "sg" -> "SG-1000"
        "32x" -> "Sega 32X"; "pce" -> "PC Engine"; "sgx" -> "SuperGrafx"
        "a26" -> "Atari 2600"; "a78" -> "Atari 7800"; "lnx" -> "Atari Lynx"
        "ngp" -> "Neo Geo Pocket"; "ngc" -> "Neo Geo Pocket Color"; "ws" -> "WonderSwan"; "wsc" -> "WonderSwan Color"
        "cue", "chd", "pbp", "iso", "m3u" -> "PlayStation"; "z64", "n64", "v64" -> "Nintendo 64"
        "bin" -> if (size in 0..524_288) "Atari 2600" else "Sega Genesis"
        else -> "?"
    }

    fun badge(core: String?) = when (core?.let { base(it) }) {
        "fceumm" -> "NES"; "snes9x" -> "SNES"; "gambatte" -> "GB"; "mgba" -> "GBA"
        "genesis_plus_gx" -> "SEGA"; "picodrive" -> "32X"; "mednafen_pce_fast" -> "PCE"; "mednafen_supergrafx" -> "SGX"
        "stella" -> "2600"; "prosystem" -> "7800"; "handy" -> "LYNX"; "mednafen_ngp" -> "NGP"; "mednafen_wswan" -> "WS"
        "pcsx_rearmed" -> "PS1"; "mupen64plus_next_gles3" -> "N64"; else -> "?"
    }

    fun color(core: String?): Int = when (core?.let { base(it) }) {
        "fceumm" -> 0xFFE05A6D; "snes9x" -> 0xFF8F7CFF; "gambatte" -> 0xFF5FB36B; "mgba" -> 0xFF5B6CFF
        "genesis_plus_gx", "picodrive" -> 0xFF3DA5F4; "mednafen_pce_fast", "mednafen_supergrafx" -> 0xFFE8903C
        "stella", "prosystem" -> 0xFFB5651D; "handy" -> 0xFFD9A400; "mednafen_ngp" -> 0xFF2FA89A; "mednafen_wswan" -> 0xFF9C5FD6
        "pcsx_rearmed" -> 0xFF607D8B; "mupen64plus_next_gles3" -> 0xFF2E9E4F; else -> 0xFF555555
    }.toInt()

    private val titles = linkedMapOf(
        "fceumm" to "NES / Famicom", "snes9x" to "Super Nintendo", "gambatte" to "Game Boy / Color",
        "mgba" to "Game Boy Advance", "genesis_plus_gx" to "Sega Genesis / SMS / Game Gear / SG-1000",
        "picodrive" to "Sega 32X", "mednafen_pce_fast" to "PC Engine", "mednafen_supergrafx" to "PC Engine SuperGrafx",
        "stella" to "Atari 2600", "prosystem" to "Atari 7800", "handy" to "Atari Lynx",
        "mednafen_ngp" to "Neo Geo Pocket / Color", "mednafen_wswan" to "WonderSwan / Color",
        "pcsx_rearmed" to "PlayStation 1 (butuh BIOS)", "mupen64plus_next_gles3" to "Nintendo 64 (berat, sebaiknya pakai gamepad)"
    )

    /** Daftar sistem yang tersedia di APK ini + ekstensinya. */
    fun summary(): List<String> {
        val byCore = extMap.entries.groupBy({ it.value }, { it.key })
        return titles.mapNotNull { (core, title) ->
            if (!isInstalled(resolve(core))) return@mapNotNull null
            val exts = (byCore[core] ?: emptyList()).joinToString(" ") { ".$it" }
            val extra = if (core == "stella" || core == "genesis_plus_gx") " .bin" else ""
            "✓ $title\n    $exts$extra"
        }
    }
}
