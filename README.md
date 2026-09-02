# BookLish - EPUB & PDF Reader Android App

Aplikasi pembaca EPUB & PDF offline untuk Android dengan dukungan bolpoin coretan (ukuran fleksibel), stabilo, mode malam, pencarian kata, bookmark, dan daftar isi.

---

## 🚀 Cara Build APK di GitHub (GitHub Actions)

Proyek ini telah dikonfigurasi dengan workflow otomatis **GitHub Actions** (`.github/workflows/android-build.yml`).

### Langkah-langkah:
1. **Push Proyek ke GitHub**:
   - Anda dapat menekan tombol **Export to GitHub** langsung dari menu atas AI Studio / download ZIP lalu push ke repositori GitHub Anda.
2. **Build Otomatis Berjalan**:
   - Setiap kali Anda melakukan `push` ke branch `main` atau `master`, GitHub Actions akan otomatis meng-compile APK.
3. **Download APK**:
   - Buka repositori Anda di GitHub.
   - Klik tab **Actions** di bagian atas.
   - Pilih workflow run terbaru (bernama *Build Android APK*).
   - Gulir ke bawah ke bagian **Artifacts**.
   - Klik **BookLish-debug-apk** untuk mengunduh file `.zip` yang berisi file installer `.apk` siap pasang di HP Android Anda.

---

## 💻 Cara Build APK Manual (Local / Android Studio)

### Menggunakan Command Line:
```bash
./gradlew assembleDebug
```
File APK akan berada di: `app/build/outputs/apk/debug/app-debug.apk`

### Menggunakan Android Studio:
1. Buka folder proyek ini di **Android Studio**.
2. Tunggu sinkronisasi Gradle selesai.
3. Pilih menu **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
4. Klik tautan *locate* pada notifikasi untuk menemukan file APK.
