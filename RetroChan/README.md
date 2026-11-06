# Retro-Chan 🎮
Emulator retro ringan: NES, SNES, GB/GBC, GBA, Genesis, Master System, Game Gear, PC Engine.

## 1. Buat keystore (SEKALI SAJA, simpan baik-baik!)
    keytool -genkeypair -v -keystore retro-chan.jks -alias retrochan \
      -keyalg RSA -keysize 2048 -validity 36500
    base64 -w0 retro-chan.jks      # (Mac: base64 -i retro-chan.jks)

## 2. GitHub → Settings → Secrets and variables → Actions
| Secret | Isi |
|---|---|
| KEYSTORE_BASE64 | output base64 di atas |
| KEYSTORE_PASSWORD | password keystore |
| KEY_ALIAS | retrochan |
| KEY_PASSWORD | password key |

## 3. Build
Push ke `main` → tab Actions → unduh artifact **Retro-Chan.apk**.
Atau buat tag `v1.0.0` → APK muncul di Releases.
versionCode otomatis naik tiap build + kunci yang sama = **update tanpa install ulang**.

⚠️ Jika keystore hilang/diganti, APK tidak bisa di-update (harus uninstall). BACKUP file .jks!
