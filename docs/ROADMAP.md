# Roadmap GyroPad

Catatan fitur yang direncanakan ke depan, beserta pertimbangan desain
kasarnya. Belum ada yang diimplementasikan di kode saat ini kecuali
disebutkan lain - dokumen ini tempat "parkir" ide supaya tidak hilang,
sekaligus starting point kalau ada kontributor yang mau bantu kerjakan.

## 1. PC server sebagai driver ringan (tidak perlu buka terminal/app)

**Masalah saat ini:** `server.py` harus dijalankan manual lewat terminal
dan jendelanya harus tetap terbuka selama main.

**Arah pengembangan:**
- Ubah jadi aplikasi **system tray** (pakai library seperti `pystray` atau
  `infi.systray`) - jalan di background, cukup ada ikon kecil di taskbar,
  tanpa jendela/terminal yang harus dibiarkan terbuka.
- Bundle jadi `.exe` standalone (pakai `PyInstaller`) supaya user tidak
  perlu install Python & dependency manual.
- Opsi **auto-start bersama Windows** (lewat entry di Startup folder atau
  Task Scheduler), supaya tinggal nyala PC, server sudah siap.
- Perlu dipikirkan: notifikasi error (mis. ViGEmBus belum terpasang) tetap
  harus terlihat walau tidak ada jendela terminal - kemungkinan lewat
  Windows toast notification (lihat poin 2).

## 2. Popup + bunyi notifikasi saat perangkat tersambung

**Yang diinginkan:** saat HP berhasil connect ke PC, muncul popup +
bunyi, dan pengaturan bunyi ini bisa dikustomisasi user tapi **reset ke
default saat aplikasi di-uninstall** (bukan tersimpan permanen di sistem).

**Arah pengembangan (sisi PC):**
- Popup: Windows toast notification (`win10toast` atau `plyer`), muncul
  singkat di pojok kanan bawah saat `GamepadCore` menerima paket state
  pertama kali dari HP (event yang sama seperti log
  `"[GyroPad] Terhubung dengan ..."` yang sudah ada di `server.py`).
- Bunyi: file `.wav` custom yang bisa diganti user, disimpan di **folder
  instalasi aplikasi sendiri** (bukan di `%APPDATA%` atau registry global)
  - supaya kalau aplikasi di-uninstall (folder instalasinya dihapus),
    setting custom ikut hilang otomatis tanpa perlu uninstaller khusus
    membersihkan lokasi lain.
- Pertimbangan: kalau nanti ada instalasi via installer resmi (bukan cuma
  zip manual), pastikan proses uninstall-nya memang menghapus folder
  konfigurasi ini juga.

## 3. Indikator status koneksi terpisah: PC↔HP vs Gamepad↔HP

**Masalah saat ini:** app cuma menunjukkan satu status umum ("Mengirim ke
..."). Kalau ada masalah, user tidak langsung tahu apakah masalahnya di
sisi PC (WiFi/USB putus) atau di sisi gamepad (iPega ke-disconnect dari
Bluetooth HP).

**Arah pengembangan:**
- Tambah dua indikator terpisah di UI:
  - **PC ↔ HP**: sudah ada dasarnya lewat `GyroPadTransport` (status
    dari `onStatusChanged`/`onError`) - tinggal dipisah visualnya dari
    status gamepad.
  - **Gamepad ↔ HP**: pantau lewat `InputManager.InputDeviceListener`
    (`onInputDeviceAdded`/`onInputDeviceRemoved`) untuk mendeteksi kapan
    device dengan `SOURCE_GAMEPAD`/`SOURCE_JOYSTICK` terpasang/lepas -
    ini bisa mendeteksi gamepad ke-disconnect dari Bluetooth tanpa perlu
    nunggu user coba gerakkan stick dulu.
- Tampilkan sebagai dua badge kecil (mis. lingkaran hijau/merah) dengan
  label singkat, supaya sekali lihat langsung tahu bagian mana yang
  bermasalah saat troubleshooting.

## 4. Sistem achievement (lokal saja, buat seru-seruan)

**Yang diinginkan:** achievement dalam app, TIDAK disimpan/dikirim ke
internet - murni fitur hiburan lokal.

**Arah pengembangan:**
- Simpan progress di `SharedPreferences` lokal (tidak ada network call
  sama sekali untuk fitur ini, sesuai permintaan).
- Contoh ide achievement (bisa disesuaikan lagi nanti):
  - "Halo Dunia" - berhasil connect ke PC pertama kali
  - "Bidikan Tajam" - pakai gyro-aim selama total 1 jam akumulasi
  - "Tanpa Putus" - koneksi stabil (tanpa reconnect) selama 30 menit main
  - "Multi-jalur" - pernah pakai mode WiFi maupun USB
- Tampilkan sebagai halaman/tab terpisah di app dengan progress bar
  sederhana per achievement.

## 5. Pengaturan sensitivitas gyro & haptic feedback yang lebih lengkap

**Yang sudah ada:** slider sensitivitas gyro tunggal (`GyroManager.sensitivity`).

**Arah pengembangan:**
- Pisahkan sensitivitas **yaw** (kiri-kanan) dan **pitch** (atas-bawah) -
  beberapa orang lebih suka salah satu sumbu lebih cepat dari yang lain.
- Tambah pengaturan haptic terpisah dari sensitivitas gyro:
  - Toggle on/off rumble secara keseluruhan
  - Slider "kekuatan getar" (mengalikan `RumbleState.combinedIntensity()`
    sebelum dikonversi ke amplitude `VibrationEffect`, lihat
    `MainActivity.applyRumble()`)
  - Tombol "Tes Getar" buat langsung coba tanpa perlu buka game dulu
- Simpan semua preferensi ini di `SharedPreferences` supaya tidak perlu
  diatur ulang tiap buka app.

## 6. Mode kamera belakang + crosshair AR (buat gaya FPS, mirip scope pistol)

**Yang diinginkan:** aktifkan kamera belakang HP dengan crosshair di
tengah, sehingga megang HP terasa seperti memegang pistol/scope asli saat
main game FPS.

**Penting - batasan yang perlu dipahami dulu:** ini adalah efek visual
**di layar HP itu sendiri**, terpisah total dari apa yang terjadi di
game/PC. HP tidak "melihat" isi game; kamera belakang cuma menampilkan
pemandangan dunia nyata di sekitar kamu, dengan crosshair digambar di
atasnya. Efeknya lebih ke **imersi fisik** (rasanya seperti membidik
sungguhan lewat scope) daripada fitur yang terhubung ke logika game.

**Arah pengembangan:**
- Pakai **CameraX** untuk live preview kamera belakang sebagai layar
  penuh, dengan `CrosshairView` (yang sudah ada) sebagai overlay di
  atasnya.
- Butuh permission `android.permission.CAMERA` baru.
- Mode toggle terpisah, mis. tombol "Mode Scope" - saat aktif, layar
  utama (kontrol IP/status/dll) disembunyikan sementara, diganti tampilan
  kamera + crosshair.
- Pertimbangan teknis:
  - **Baterai & panas**: kamera + layar nyala terus bisa cukup boros,
    perlu ada cara cepat keluar dari mode ini.
  - **Zoom**: bisa pakai API zoom digital bawaan CameraX
    (`CameraControl.setZoomRatio()`), dipetakan ke tombol tertentu di
    gamepad (mis. klik stick) untuk "zoom in" ala scope sniper.
  - Karena ini murni visual/imersi, tidak memengaruhi data yang dikirim
    ke PC - `ControllerState` dan protokol jaringan tidak perlu berubah
    sama sekali untuk fitur ini.

## Sudah lebih dulu ada di roadmap (dari versi sebelumnya)

- [ ] Ganti JSON dengan format biner supaya latency lebih rendah
- [ ] Kalibrasi & auto-recenter gyro yang lebih canggih (mis. deteksi diam)
- [ ] UI mapping tombol yang bisa dikustomisasi
- [ ] Simpan profil sensitivitas per-game
- [ ] Dukungan Linux (uinput) selain ViGEmBus/Windows
- [ ] Overlay HUD sungguhan di layar PC (bukan cuma preview di app HP)

Lihat juga [TESTING.md](TESTING.md) untuk cara menguji latency & tombol -
berguna dipakai ulang setelah fitur-fitur di atas jadi diimplementasikan.
