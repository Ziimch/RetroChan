# Retro-Chan 🎮
NES • SNES • GB/GBC • GBA • Genesis • SMS • Game Gear • PC Engine

## Build otomatis
Push ke GitHub (branch apa pun) → tab **Actions** → unduh artifact **Retro-Chan.apk**.
Kunci tanda tangan dibuat otomatis sekali dan disimpan di branch `signing-keystore`.
Build berikutnya memakai kunci yang sama + versionCode naik otomatis ⇒ update tanpa install ulang.

⚠️ Gunakan repo **Private**. Jangan hapus branch `signing-keystore`.

## Fitur
Tambah ROM (.zip didukung) • unduh dari link langsung • cari game • kontrol layar multi-touch •
gamepad Bluetooth • 3 slot save state • auto-save SRAM • portrait/landscape.

## Kontrol v2
Setiap jari "mengunci" satu area (D-pad / tombol aksi / tombol tunggal) → tidak salah tekan saat jari bergeser.
D-pad berbasis sudut (diagonal tidak mudah terpicu). Menu ☰ → ⚙ Pengaturan: ukuran, transparansi, getar,
mode kidal, turbo A/B, filter layar (CRT/LCD). Tombol FF = fast-forward 2x. Simpan Cepat / Muat Cepat di menu.

## Format yang didukung (v3)
NES/FDS • SNES • GB/GBC • GBA • Genesis/MD • SMS • Game Gear • SG-1000 • 32X • PC Engine/SuperGrafx •
Atari 2600/7800/Lynx • Neo Geo Pocket • WonderSwan • PlayStation 1 (.cue/.bin/.chd/.iso/.pbp, butuh BIOS) • N64 (berat).
`.zip` otomatis diekstrak. `.bin` dikenali otomatis (Atari 2600 / Genesis / pendamping .cue).
Core yang gagal diunduh saat build dilewati otomatis; aplikasi hanya menampilkan game yang core-nya ada.

## Stabilitas
Game berjalan di proses terpisah (crash native core tidak menutup aplikasi), crash log + tombol bagikan,
deteksi game berhenti tiba-tiba, penulisan save atomik, pengimporan aman (file setengah jadi dibersihkan).
