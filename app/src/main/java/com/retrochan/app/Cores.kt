package com.retrochan.app

object Cores {
    // ekstensi ROM -> nama core libretro (file: lib<core>_libretro_android.so)
    private val map = mapOf(
        "nes" to "fceumm", "fds" to "fceumm",
        "sfc" to "snes9x", "smc" to "snes9x",
        "gb" to "gambatte", "gbc" to "gambatte",
        "gba" to "mgba",
        "md" to "genesis_plus_gx", "gen" to "genesis_plus_gx", "smd" to "genesis_plus_gx",
        "sms" to "genesis_plus_gx", "gg" to "genesis_plus_gx",
        "pce" to "mednafen_pce_fast"
    )
    val extensions get() = map.keys
    fun forFile(name: String): String? = map[name.substringAfterLast('.', "").lowercase()]
    fun systemName(name: String) = when (forFile(name)) {
        "fceumm" -> "NES"; "snes9x" -> "SNES"; "gambatte" -> "Game Boy"; "mgba" -> "GBA"
        "genesis_plus_gx" -> "Sega"; "mednafen_pce_fast" -> "PC Engine"; else -> "?"
    }
}
