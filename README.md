# Retro-Chan 🎮
Emulator retro ringan: NES, SNES, GB/GBC, GBA, Genesis, SMS, Game Gear, PC Engine.

## Build (otomatis, tanpa setting apa pun)
1. Push project ke GitHub (branch `main`).
2. Tab Actions → tunggu selesai → unduh artifact **Retro-Chan.apk**.

Build pertama membuat kunci tanda tangan otomatis dan menyimpannya di `keystore/retro-chan.jks`.
Build berikutnya memakai kunci yang sama + versionCode naik otomatis => update tanpa install ulang.

⚠️ Gunakan repo **Private** (kunci ada di repo). Jangan hapus folder `keystore/`.
