<div align="center">

# 📺 Aura TV Home

**Launcher Android TV / STB yang Cepat, Ringan, Modern, dan Elegan**  
*Mendukung Tema Frosted Glass, Neumorphism, dan Dark Glow dengan Live Wallpaper, Widget Sepak Bola, Cuaca Presisi, & Navigasi Remote Halus.*

[![Build & Release APK](https://github.com/luthpunk/aura-tv-home/actions/workflows/build-apk.yml/badge.svg)](https://github.com/luthpunk/aura-tv-home/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/luthpunk/aura-tv-home?color=0284C7&label=Rilis%20Terbaru)](https://github.com/luthpunk/aura-tv-home/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Google%20TV%20%7C%20STB-10B981)](#)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

<br/>

[![Trakteer Kopi di Saweria](https://img.shields.io/badge/☕_Trakteer_Kopi-Saweria-FF813F?style=for-the-badge&logo=kofi&logoColor=white)](https://saweria.co/Luthpunk)

> *Suka dengan launcher ini? Dukung pengembang dengan mentraktir secangkir kopi melalui [**Saweria**](https://saweria.co/Luthpunk).* ☕✨

<br/>

[**📥 Unduh APK Terbaru (aura-tv-home.apk)**](https://github.com/luthpunk/aura-tv-home/releases/latest/download/aura-tv-home.apk) • [**Fitur**](#-fitur-utama) • [**Tampilan Layar**](#-tampilan-layar-tema) • [**Pemasangan**](#-cara-pemasangan-di-android-tv--stb) • [**Pembaruan OTA**](#-pembaruan-otomatis-ota) • [**Kredit & Lisensi**](#-kredit--lisensi)

---

</div>

## 🖼️ Tampilan Layar (Tema)

Aura TV Home menyediakan 3 pilihan gaya visual modern yang dapat diubah langsung dari menu **Setelan**:

### 1. Neumorphism
Gaya desain *soft UI* modern dengan bayangan timbul dan cekung halus yang nyaman dipandang.
![Tema Neumorphism](docs/screenshots/neumorphism.png)

### 2. Frosted Glass
Tampilan kaca buram (*acrylic blur*) premium ala Apple TV dengan efek kilau pencahayaan ambient dinamis.
![Tema Frosted Glass](docs/screenshots/frosted_glass.png)

### 3. Dark Glow
Tema gelap dengan aksen kontras neon cyber minimalis dan teks putih bersih yang hemat energi pada layar TV.
![Tema Dark Glow](docs/screenshots/dark_glow.png)

---

## ✨ Fitur Utama

- **🚀 Ringan, Irit RAM & Super Cepat:** Dioptimalkan khusus untuk chipset prosesor STB/Android TV (Amlogic, Allwinner, Rockchip) agar perpindahan aplikasi dan respon remote terasa instan tanpa jeda (*zero stutter*).
- **📦 App Drawer Berbasis RecyclerView & Memory Caching:**
  - Membuka daftar semua aplikasi secara instan (*zero disk I/O lag*).
  - Menggunakan arsitektur daur ulang *RecyclerView* yang sangat hemat RAM dan mulus 60 FPS pada remote D-pad.
  - Tahan tombol **OK** pada aplikasi untuk membuka detail aplikasi / copot pemasangan (*uninstall*).
- **⚽ Widget Jadwal & Skor Sepak Bola (Football Schedule Widget):**
  - **Mode Kartu Interaktif:** Menampilkan 3 jadwal pertandingan terdekat lengkap dengan badge skor langsung (LIVE). Kartu dapat diperluas (*expand*) secara *smooth* untuk melihat jadwal lengkap.
  - **Mode Kapsul Minimal:** Teks berjalan (*ticker marquee*) ringkas yang hemat ruang di layar beranda.
  - Filter liga terpopuler dunia: Premier League, La Liga, Serie A, Bundesliga, Liga Champions, dan lainnya.
- **🎬 Dukungan Live Wallpaper (Video MP4) & Wallpaper 4K:**
  - Mendukung wallpaper video bergerak (*seamless loop*) dengan akselerasi perangkat keras yang hemat daya.
  - Efek kaca buram (*frosted glass*) di dock dan bilah menu otomatis bersinkronisasi secara dinamis dengan cuplikan video yang sedang berputar.
  - Beragam kategori wallpaper terintegrasi: Pesona Nusantara, Anime HD, Alam, Lautan, Minimalis Gelap, hingga Live Wallpaper.
- **🎵 Dynamic Now Playing Pill (Pemutar Musik Kapsul):**
  - Kapsul status dinamis setinggi 42dp yang sejajar dengan bilah menu atas (hanya muncul saat musik diputar).
  - Menampilkan cover album asli, judul lagu berjalan (*marquee*), nama artis, serta tombol kontrol instan `[ ⏯ Play/Pause ]` dan `[ ⏭ Next ]`.
  - Mendukung Spotify TV, YouTube TV, SmartTube, Apple Music, VLC, pemutar lagu lokal USB, dan aplikasi media lainnya.
- **🌤️ Widget Cuaca Kompak & Presisi:**
  - Menampilkan lokasi kota, suhu besar, ikon vektor cuaca, dan kondisi cuaca saat ini.
  - **Slot Ramalan 3 Jam ke Depan:** Memantau ramalan cuaca pada rentang waktu `+3 jam`, `+6 jam`, dan `+9 jam` secara akurat dari Open-Meteo.
  - Dua pil informasi di bagian bawah: **AQI (Indeks Kualitas Udara)** dan **Peluang Hujan (%)**.
- **📱 Dock Baris Fleksibel (5, 6, atau 7 Aplikasi):**
  - Pengaturan jumlah kolom aplikasi sebaris sesuai preferensi layar Anda (5, 6, atau 7 aplikasi).
  - Sebaran bayangan 3D (*dual shadow*) dan efek fokus otomatis menyesuaikan secara proporsional dengan ukuran kartu.
  - Tinggi intipan baris kedua seragam dan presisi di semua pilihan kolom.
- **⚡ Smart Boost & Info TV Modern:**
  - Menampilkan status RAM terpakai, ruang penyimpanan internal, model perangkat, dan versi Android TV.
  - Tombol **Bersihkan RAM Sekarang** untuk melegakan memori STB dengan sekali klik.
- **🧭 Navigasi D-Pad Remote Halus:** Animasi fokus *Glider* yang presisi mengalir di antara kartu aplikasi dan tombol menu atas.
- **🔄 Pembaruan Langsung di TV (OTA Update):** Pengecekan otomatis versi baru langsung dari repositori GitHub ini. Cukup tekan tombol update di TV dan APK akan terinstal langsung.

---

## 📥 Cara Pemasangan di Android TV / STB

Unduh file APK rilis terbaru:  
👉 **[Unduh aura-tv-home.apk](https://github.com/luthpunk/aura-tv-home/releases/latest/download/aura-tv-home.apk)**

### Metode 1: 1-Klik Otomatis Lewat Tab Boost di Aura TV Tools (Sangat Direkomendasikan ⭐)
Tidak perlu mengunduh file APK manual atau mengetik perintah terminal. Anda cukup menggunakan aplikasi pendamping [**Aura TV Tools**](https://github.com/luthpunk/aura-tv-tools) dari ponsel Android Anda:

1. Pasang & buka aplikasi [**Aura TV Tools**](https://github.com/luthpunk/aura-tv-tools) di ponsel Android Anda.
2. Sambungkan ke Android TV / STB Anda (pilih TV dari daftar pencarian Wi-Fi otomatis atau masukkan IP TV).
3. Masuk ke tab **🚀 Boost TV**.
4. Pada kartu **Aura TV Home**, tekan tombol **"Pasang Aura TV Home"**.
5. **Selesai secara otomatis!** Aplikasi Aura TV Tools akan langsung:
   - Mengunduh file APK versi rilis terbaru `aura-tv-home.apk` dari repositori GitHub ini.
   - Mengirim dan memasang APK ke TV / STB melalui jalur nirkabel ADB.
   - Menetapkan Aura TV Home sebagai launcher beranda utama default (`set-home-activity` & role Home).
   - Memberikan izin sistem aman (`WRITE_SECURE_SETTINGS`).
   - Membuka dan menampilkan Aura TV Home langsung di layar TV Anda.  
   *(Jika suatu saat Anda ingin kembali ke launcher bawaan pabrik TV, cukup klik tombol "Pulihkan launcher bawaan" pada menu yang sama).*

---

### Metode 2: Pasang Manual Lewat Tab Aplikasi di Aura TV Tools
Jika Anda telah mengunduh file APK ke ponsel dan ingin memasangnya secara manual:
1. Buka [**Aura TV Tools**](https://github.com/luthpunk/aura-tv-tools) dan sambungkan ke TV.
2. Masuk ke tab **Aplikasi** > klik tombol **＋ Pasang APK dari ponsel**.
3. Pilih file `aura-tv-home.apk` yang tersimpan di memori ponsel.
4. Tekan tombol **Home** pada remote TV, pilih **Aura TV Home**, lalu pilih **"Selalu" (Always)**.

---

### Metode 3: Lewat ADB (PC / Laptop / Termux / Terminal)

Jika tombol Home di remote STB Anda dikunci oleh launcher bawaan pabrik (seperti launcher IndiHome, Android TV Core, atau Google TV Launcher), Anda bisa menjadikannya default permanen lewat ADB:

1. **Instal APK ke STB:**
   ```bash
   adb connect <IP_STB>:5555
   adb install -r aura-tv-home.apk
   ```

2. **Jadikan Default Langsung Lewat Perintah:**
   ```bash
   # Buka Aura TV Home langsung ke layar
   adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity
   ```

3. **(Opsional) Nonaktifkan Launcher Bawaan STB jika Masih Muncul:**
   Jika tombol Home masih kembali ke launcher pabrik, nonaktifkan launcher bawaannya (bisa diaktifkan kembali kapan saja):

   - **Android TV / Google TV Standar:**
     ```bash
     adb shell pm disable-user --user 0 com.google.android.tvlauncher
     # Atau untuk Google TV UI baru:
     adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
     ```
   - **STB Operator (ZTE / Fiberhome / IndiHome):**
     ```bash
     adb shell pm disable-user --user 0 com.zte.iptvclient.android.launcher
     # Atau:
     adb shell pm disable-user --user 0 com.fiberhome.myindihome
     ```

   *(Untuk mengaktifkannya kembali jika sewaktu-waktu dibutuhkan, cukup ganti kata `disable-user --user 0` menjadi `enable`).*

4. **⚡ Aktifkan Izin Pemutar Musik (Now Playing) via ADB:**
   Pada sistem Android TV / Google TV, Google menyembunyikan menu pengaturan izin notifikasi dari antarmuka TV Leanback. Agar launcher dapat membaca judul lagu, nama artis, dan cover album secara otomatis, jalankan perintah ini sekali setelah pemasangan:

   ```bash
   # Aktifkan izin listener musik ke sistem Android TV
   adb shell cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaNotificationListener

   # (Opsional) Berikan izin pengelolaan setelan aman agar tersinkronisasi
   adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
   ```

---

## ☕ Dukung Pengembang

Jika Anda merasa proyek ini bermanfaat dan membantu mempercantik pengalaman menonton TV di rumah Anda, pertimbangkan untuk memberikan dukungan:

<div align="center">

[![Saweria](https://img.shields.io/badge/Saweria-Trakteer%20Kopi-FF813F?style=for-the-badge&logo=kofi&logoColor=white)](https://saweria.co/Luthpunk)

**[👉 saweria.co/Luthpunk](https://saweria.co/Luthpunk)**

</div>

Dukungan Anda sangat berarti untuk terus mengembangkan fitur-fitur baru dan pemeliharaan aplikasi ini. Terima kasih banyak! ❤️

---

## 🔄 Pembaruan Otomatis (OTA)

Aura TV Home memiliki modul pembaruan in-app terintegrasi. Metadata versi rilis tersimpan pada:
```
https://raw.githubusercontent.com/luthpunk/aura-tv-home/main/release/version.json
```
Setiap kali rilis baru diterbitkan di repositori ini, pengguna akan mendapatkan notifikasi pembaruan langsung di layar TV.

---

## 📜 Kredit & Lisensi

Proyek ini dibangun dan dikembangkan dengan basis fondasi utama dari repositori open-source:
- **Basis Proyek Utama:** [**CleanYourTV**](https://github.com/Gathin23/cleanyourtv) oleh **Gathin23**  
  *Terima kasih yang sebesar-besarnya kepada pengembang asli atas arsitektur launcher Android TV yang luar biasa.*

### 🛠️ Pustaka & Aset Pihak Ketiga Lainnya:
- **Jadwal & Skor Bola:** Football Data API & Feed Olahraga Terbuka.
- **Prakiraan Cuaca:** [Open-Meteo API](https://open-meteo.com/) *(Non-komersial, lisensi terbuka)*.
- **Kualitas Udara:** Data APIMS / JAS Malaysia & Open-Meteo Air Quality.
- **Tipografi:** Font [Outfit](https://fonts.google.com/specimen/Outfit) oleh Rodrigo Fuenzalida & Omnibus-Type *(SIL Open Font License 1.1)*.
- **Ikonografi:** Google Material Icons & Komponen Vektor SVG berlisensi Apache 2.0.

### 📄 Lisensi
Perangkat lunak ini dirilis di bawah lisensi **Apache License 2.0**. Lihat file [LICENSE](LICENSE) untuk rincian selengkapnya.
