# TODO — Aura TV Home

Daftar rencana perbaikan dan pengembangan berikutnya untuk **Aura TV Home**.

---

## 1. Auto-Masuk Aura TV Home Saat TV Baru Nyala / Restart (Smart TV Non-STB, Tanpa ADB)

### Masalah
- Pada Smart TV Android TV / Google TV asli (TCL, Xiaomi, Sony, Hisense, Polytron, Coocaa, dll.) yang **launcher bawaannya tidak di-disable via ADB**, saat TV baru dinyalakan (*cold boot*) atau di-restart, sistem masuk ke launcher bawaan TV dulu. User harus menekan tombol **Home** di remote agar masuk ke Aura TV Home.
- Pada launcher seperti **Projectivy Launcher**, TV bisa langsung otomatis masuk ke launcher setelah menyala/restart karena menggunakan *Accessibility Service* ("Override current launcher") yang mencegat kemunculan stock launcher.

### Akar Masalah di Kode Saat Ini
- `launcher/app/src/main/res/xml/home_key_service.xml` sudah mengaktifkan `typeWindowStateChanged`.
- Namun `HomeKeyService.onAccessibilityEvent()` (`launcher/app/src/main/java/com/gathin/tvhome/HomeKeyService.java`) saat ini **hanya** menangani otomatisasi *Restart* & *Force Stop*, belum mendeteksi/menimpa stock launcher yang muncul ke layar.
- `BootReceiver` + `RECEIVE_BOOT_COMPLETED` sebelumnya dihapus di commit `10df07c` untuk menghindari *hard-block* Google Play Protect.

### Rencana Perbaikan (Aman Play Protect — 0 Permission Baru)
- [ ] Di `HomeKeyService.java`:
  - [ ] Deteksi dinamis semua package `CATEGORY_HOME` selain Aura TV Home lewat `PackageManager.queryIntentActivities(ACTION_MAIN + CATEGORY_HOME)`, ditambah fallback daftar stock TV launcher umum (`com.google.android.apps.tv.launcherx`, `com.google.android.tvlauncher`, `com.mitv.tvhome`, `com.tcl.cyberui`, dll.).
  - [ ] Saat `onServiceConnected()` (otomatis jalan waktu TV selesai boot/restart) dan saat `onAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED)`: jika window foreground adalah stock launcher, otomatis luncurkan `MainActivity` Aura TV Home.
  - [ ] Pastikan **tidak** mencegat jika foreground adalah Settings, SystemUI/dialog sistem, Live TV/HDMI input, atau aplikasi biasa, serta beri *debounce* agar tidak *flicker* / *loop*.
- [ ] Di popup Pengaturan (`MainActivity.java`):
  - [ ] Tambahkan toggle **"Timpa Launcher Bawaan"** (default **ON** saat Aksesibilitas aktif) supaya user tetap bisa masuk ke launcher bawaan sementara bila perlu.
- [ ] Pastikan `AndroidManifest.xml` **tidak** menambah permission baru apa pun (tetap cocok dengan baseline aman v3.7.5 / v3.8.0-id rev 69).

---

## 2. Perbaikan Tombol Pengaturan (Settings / Gear) di Remote TV Tidak Berfungsi

### Masalah
- Sebagian pengguna Smart TV mengeluhkan tombol **Pengaturan (Gear / Settings)** di remote TV tidak berfungsi saat memakai Aura TV Home.

### Akar Masalah di Kode Saat Ini
- Di `HomeKeyService.onKeyEvent()` (`launcher/app/src/main/java/com/gathin/tvhome/HomeKeyService.java`, sekitar baris 495–508):
  - `HomeKeyService` mencegat dan menelan (`return true`) `KeyEvent.KEYCODE_SETTINGS` secara global (baik `ACTION_DOWN` maupun `ACTION_UP`), sehingga **Quick Settings / Menu Gambar & Suara bawaan TV** terblokir dan tidak pernah menerima tombol tersebut.
  - Lalu ia hanya memanggil `startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(FLAG_ACTIVITY_NEW_TASK))` **tanpa `try-catch`** dan **tanpa fallback OEM** (tidak seperti `MainActivity.systemIntent()`). Di banyak firmware TV (Google TV, MIUI TV, Android TV 11+), intent mentah dari background service ini gagal/diabaikan sehingga tombol terasa mati.

### Rencana Perbaikan
- [ ] Di `HomeKeyService.onKeyEvent()`:
  - [ ] **Jangan cegat/telan `KeyEvent.KEYCODE_SETTINGS`** (`return false`) agar firmware TV asli bisa menangani tombol Gear/Settings secara native (Quick Settings, Picture/Sound, Google TV Dashboard, dll.).
  - [ ] Hanya cegat jika tombol cocok dengan `settings_key_scancodes` khusus (remote proyektor/OEM yang tidak dipetakan Android), dibungkus `try-catch` + fallback OEM (`com.xiaomi.mitv.settings`).
- [ ] Di `MainActivity.dispatchKeyEvent()` (`launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java`):
  - [ ] Tambahkan fallback aman untuk `KEYCODE_SETTINGS` hanya saat berada di beranda Aura TV Home **dan** sistem tidak menanganinya (misalnya pada perangkat yang stock launcher-nya sudah di-disable via ADB): buka `systemIntent(Settings.ACTION_SETTINGS)`.

---

## Catatan Verifikasi Saat Dikerjakan Nanti
- [ ] Uji di Smart TV asli (non-ADB / launcher bawaan masih aktif + Aksesibilitas ON): restart TV & matikan total → pastikan langsung masuk Aura TV Home.
- [ ] Uji tombol **Settings / Gear** di remote TV: pastikan Quick Settings / menu Pengaturan TV tetap berfungsi normal di dalam Aura TV Home maupun di dalam aplikasi lain (YouTube, Live TV, dll.).
- [ ] Uji di perangkat dengan launcher bawaan di-disable via ADB: pastikan tidak ada bentrokan dan tombol Settings tetap bekerja.
- [ ] Cek instalasi APK release terhadap Google Play Protect (pastikan tidak ada tambahan permission di `AndroidManifest.xml`).
