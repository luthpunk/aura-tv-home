<div align="center">

# 📺 Aura TV Home

**Launcher Android TV / STB yang Cepat, Modern, Bersih, dan Elegan**  
*Mendukung Tema Frosted Glass, Neumorphism, dan Dark Glow dengan Widget Cuaca Presisi & Navigasi Remote Halus.*

[![Build & Release APK](https://github.com/luthpunk/aura-tv-home/actions/workflows/build-apk.yml/badge.svg)](https://github.com/luthpunk/aura-tv-home/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/luthpunk/aura-tv-home?color=0284C7&label=Rilis%20Terbaru)](https://github.com/luthpunk/aura-tv-home/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Google%20TV%20%7C%20STB-10B981)](#)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

[**📥 Unduh APK Terbaru (aura-tv-home.apk)**](https://github.com/luthpunk/aura-tv-home/releases/latest/download/aura-tv-home.apk) • [**Fitur**](#-fitur-utama) • [**Tampilan Layar**](#-tampilan-layar-tema) • [**Pembaruan OTA**](#-pembaruan-otomatis-ota) • [**Kredit & Lisensi**](#-kredit--lisensi)

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

- **🚀 Ringan & Cepat:** Dioptimalkan khusus untuk chipset prosesor STB/Android TV (Amlogic, Allwinner, Rockchip) agar perpindahan aplikasi dan respon remote terasa instan tanpa jeda (*zero stutter*).
- **🌤️ Widget Cuaca Kompak & Presisi:**
  - Menampilkan lokasi, suhu besar, ikon vektor cuaca, dan kondisi cuaca saat ini (*flush left*).
  - **Slot Ramalan 3 Jam ke Depan:** Memantau ramalan cuaca pada rentang waktu `+3 jam`, `+6 jam`, dan `+9 jam` secara akurat dari Open-Meteo.
  - Dua pil informasi di bagian bawah: **AQI (Indeks Kualitas Udara)** dan **Peluang Hujan (%)**.
  - Posisi widget terkunci statis tepat di tengah antara Jam dan Nama Aplikasi Baris 1 tanpa bergeser (*no drift*).
- **🎨 3 Tema Visual:** **Frosted Glass**, **Neumorphism**, dan **Dark Glow**.
- **🧭 Navigasi D-Pad Remote Halus:** Animasi fokus *Glider* yang presisi mengalir di antara kartu aplikasi dan tombol menu atas.
- **🔄 Pembaruan Langsung di TV (OTA Update):** Pengecekan otomatis versi baru langsung dari repositori GitHub ini. Cukup tekan tombol update di TV dan APK akan terinstal langsung.
- **🛠️ Pengaturan Fleksibel:** Pengaturan baris dan kolom aplikasi, sembunyikan/tampilkan aplikasi tertentu, ganti wallpaper dinamis online/lokal, serta kustomisasi tata letak.

---

## 📥 Cara Pemasangan di Android TV / STB

Unduh file APK rilis terbaru:  
👉 **[Unduh aura-tv-home.apk](https://github.com/luthpunk/aura-tv-home/releases/latest/download/aura-tv-home.apk)**

### Metode 1: Lewat Aplikasi Aura TV Tools (Rekomendasi Paling Mudah)
1. Buka aplikasi **Aura TV Tools** di HP Android Anda.
2. Sambungkan ke IP STB Anda (di port `5555`).
3. Masuk ke tab **Aplikasi** > tekan **📂 Pilih & Install APK dari HP** dan pilih file `aura-tv-home.apk`.
4. Tekan tombol **Home** pada remote TV, lalu pilih **Aura TV Home** dan klik **"Selalu" (Always)**.

---

### Metode 2: Lewat ADB (PC / Laptop / Termux / Terminal)

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
- **Prakiraan Cuaca:** [Open-Meteo API](https://open-meteo.com/) *(Non-komersial, lisensi terbuka)*.
- **Kualitas Udara:** Data APIMS / JAS Malaysia & Open-Meteo Air Quality.
- **Tipografi:** Font [Outfit](https://fonts.google.com/specimen/Outfit) oleh Rodrigo Fuenzalida & Omnibus-Type *(SIL Open Font License 1.1)*.
- **Ikonografi:** Google Material Icons & Komponen Vektor SVG berlisensi Apache 2.0.

### 📄 Lisensi
Perangkat lunak ini dirilis di bawah lisensi **Apache License 2.0**. Lihat file [LICENSE](LICENSE) untuk rincian selengkapnya.
