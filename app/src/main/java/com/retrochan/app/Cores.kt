package com.retrochan.app

object Cores {
    private val map = mapOf(
        "nes" to "fceumm", "fds" to "fceumm",
        "sfc" to "snes9x", "smc" to "snes9x",
        "gb" to "gambatte", "gbc" to "gambatte",
        "gba" to "mgba",
        "md" to "genesis_plus_gx", "gen" to "genesis_plus_gx", "smd" to "genesis_plus_gx",
        "sms" to "genesis_plus_gx", "gg" to "genesis_plus_gx",
        "pce" to "mednafen_pce_fast"
    )
    private fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
    fun forFile(name: String): String? = map[ext(name)]

    fun systemName(name: String) = when (ext(name)) {
        "nes" -> "NES"; "fds" -> "Famicom Disk"; "sfc", "smc" -> "Super Nintendo"
        "gb" -> "Game Boy"; "gbc" -> "Game Boy Color"; "gba" -> "Game Boy Advance"
        "md", "gen", "smd" -> "Sega Genesis"; "sms" -> "Master System"; "gg" -> "Game Gear"
        "pce" -> "PC Engine"; else -> "?"
    }
    fun badge(name: String) = when (forFile(name)) {
        "fceumm" -> "NES"; "snes9x" -> "SNES"; "gambatte" -> "GB"; "mgba" -> "GBA"
        "genesis_plus_gx" -> "SEGA"; "mednafen_pce_fast" -> "PCE"; else -> "?"
    }
    fun color(name: String): Int = when (forFile(name)) {
        "fceumm" -> 0xFFE05A6D.toInt(); "snes9x" -> 0xFF8F7CFF.toInt(); "gambatte" -> 0xFF5FB36B.toInt()
        "mgba" -> 0xFF5B6CFF.toInt(); "genesis_plus_gx" -> 0xFF3DA5F4.toInt(); "mednafen_pce_fast" -> 0xFFE8903C.toInt()
        else -> 0xFF555555.toInt()
    }
}
