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

## 7. Gyro asli lewat DS4 + Steam Input (dipertimbangkan, belum dikerjakan)

**Konteks:** dibahas saat mengerjakan integrasi Steam - lihat
[STEAM.md](STEAM.md) untuk perbandingan lengkap. Keputusan saat ini:
GyroPad tetap emulasi Xbox 360 dengan gyro dicampur ke stick kanan di
server (sudah jalan, tidak butuh Steam Input apapun), BUKAN emulasi DS4
dengan gyro asli diteruskan ke Steam.

**Kenapa belum dikerjakan:** `vgamepad` (library yang dipakai server)
mendukung emulasi DS4, tapi API publiknya tidak mengekspos field
gyro/accelerometer DS4 ke Python - field itu ada di level driver
ViGEmBus (`DS4_REPORT_EX.wGyroX/Y/Z`, `wAccelX/Y/Z`), tapi wrapper
Python-nya tidak meneruskannya. Untuk benar-benar mengirim gyro asli,
perlu bypass `vgamepad` dan bicara langsung ke DLL `ViGEmClient` lewat
`ctypes`: bikin struct `DS4_REPORT_EX` manual dengan layout byte yang
persis sama dengan versi C-nya, lalu panggil
`vigem_target_ds4_update_ex` langsung.

**Kenapa ini sepadan dipertimbangkan lagi nanti:** pendekatan DS4 asli
membuka mode "gyro sebagai mouse" di Steam Input (gerakan kamera relatif
langsung, tanpa lewat kurva respons analog stick) - beberapa orang
merasa ini lebih presisi buat aiming FPS dibanding simulasi stick yang
dipakai GyroPad sekarang.

**Risiko/kompleksitas kalau dikerjakan:**
- Struct `DS4_REPORT_EX` harus di-mirror persis byte-per-byte dari
  `ViGEmClient/include/ViGEm/Common.h` (C struct) ke `ctypes.Structure`
  Python - salah alignment/padding sedikit saja bisa bikin data korup
  tanpa error yang jelas.
- Harus menjalankan inisialisasi ViGEmBus (`vigem_alloc`, `vigem_connect`,
  target alloc) sendiri secara paralel dengan yang sudah dilakukan
  `vgamepad`, atau mengakses instance internal `vgamepad` yang sifatnya
  private/tidak didokumentasikan (bisa berubah sewaktu-waktu antar versi
  `vgamepad` tanpa peringatan).
- Perlu testing ekstra memastikan Steam benar-benar mengenali target
  sebagai controller ber-gyro (biasanya berdasarkan VID/PID + capability
  descriptor yang dilaporkan saat enumerasi).

Kalau nanti ada yang mau coba kerjakan ini, mulai dari membaca
`ViGEmClient/include/ViGEm/Common.h` (struct `DS4_REPORT_EX`) dan
`ViGEmClient/include/ViGEm/Client.h` (signature `vigem_target_ds4_update_ex`)
di repo resmi [ViGEmClient](https://github.com/ViGEm/ViGEmClient).

## Sudah lebih dulu ada di roadmap (dari versi sebelumnya)

- [x] Ganti JSON dengan format biner supaya latency lebih rendah — **selesai di v0.3**, lihat [PROTOCOL.md](PROTOCOL.md)
- [x] Kalibrasi & auto-recenter gyro yang lebih canggih (mis. deteksi diam) — **selesai di v0.4**, lihat bagian "Kalibrasi bias & auto-koreksi drift" di [ARCHITECTURE.md](ARCHITECTURE.md)
- [ ] UI mapping tombol yang bisa dikustomisasi
- [x] Simpan profil sensitivitas per-game — **selesai di v0.5**, lihat bagian "Profil sensitivitas per-game" di [ARCHITECTURE.md](ARCHITECTURE.md)
- [ ] Dukungan Linux (uinput) selain ViGEmBus/Windows
- [ ] Overlay HUD sungguhan di layar PC (bukan cuma preview di app HP)

Lihat juga [TESTING.md](TESTING.md) untuk cara menguji latency & tombol -
berguna dipakai ulang setelah fitur-fitur di atas jadi diimplementasikan.
