# Setup Android App

Panduan ini dites dengan **Xiaomi Redmi A3** + **iPega 9076**, tapi harusnya
berlaku untuk HP Android lain (min. Android 7.0 / API 24) dan gamepad
Bluetooth HID standar lainnya.

## Prasyarat

- [Android Studio](https://developer.android.com/studio) (versi terbaru
  disarankan — project ini pakai Android Gradle Plugin 8.5.x & Kotlin 1.9.x)
- HP Android dengan:
  - Bluetooth (untuk pairing gamepad)
  - WiFi (untuk kirim data ke PC)
  - Sensor gyroscope (cek di `Settings > About phone` atau app seperti
    "Sensor Box"; hampir semua HP modern termasuk Redmi A3 punya ini,
    tapi HP entry-level tertentu kadang tidak menyertakannya)

## Build & pasang

1. Buka folder `android/` di Android Studio (**Open** → pilih folder
   `android/`, bukan folder root repo).
2. Tunggu Gradle sync selesai. Kalau ini pertama kali membuka project ini,
   Android Studio akan otomatis membuatkan `gradle wrapper` yang belum
   disertakan di repo (supaya repo tidak berat oleh binary wrapper).
3. Colokkan HP lewat USB dengan **USB debugging** aktif
   (`Settings > About phone` → tap `MIUI version`/`Build number` 7x untuk
   memunculkan Developer options → aktifkan USB debugging), atau pakai
   emulator (emulator tidak akan punya gyro/gamepad fisik, jadi disarankan
   pakai device fisik).
4. Tekan **Run ▶** di Android Studio, pilih device kamu.
5. App **GyroPad** akan terpasang & terbuka otomatis.

## Pairing gamepad iPega 9076

1. Nyalakan iPega 9076, tekan tombol pairing (biasanya kombinasi
   `HOME + START` atau tombol dedicated, cek manual bawaan gamepad kamu).
2. Di HP: `Settings > Bluetooth`, cari & pasangkan dengan iPega.
3. Setelah terpasang, buka app GyroPad — kamu tidak perlu memilih device
   apapun di dalam app; Android otomatis meneruskan input gamepad yang
   sudah paired sebagai input generik (sama seperti keyboard/mouse
   Bluetooth), dan `MainActivity` sudah didesain untuk menangkapnya.
4. Cek dengan menggerakkan stick — kalau semua bekerja dengan benar, nanti
   setelah terhubung ke server (lihat bawah) kamu akan lihat counter
   "Paket terkirim" naik terus.

## Menghubungkan ke PC

App punya dua mode koneksi, pilih lewat radio button di bagian atas:

### Mode WiFi (UDP) — default

1. Pastikan PC sudah menjalankan `server.py` (lihat
   [SETUP_PC.md](SETUP_PC.md)) dan **HP + PC berada di jaringan WiFi yang
   sama** (mis. sama-sama connect ke router rumah, atau HP connect ke
   hotspot PC).
2. Cari tahu IP lokal PC kamu:
   - Windows: buka Command Prompt, jalankan `ipconfig`, catat `IPv4
     Address` di adapter WiFi/Ethernet yang aktif.
3. Pilih mode **"WiFi (UDP)"**, isi field **IP PC** dengan IP tadi (mis.
   `192.168.1.42`) dan **Port** dengan `25565` (default, samakan dengan
   yang dipakai `server.py`).
4. Tekan **Hubungkan ke PC**. Kalau berhasil, status akan berubah jadi
   "Mengirim ke ...", dan di terminal `server.py` akan muncul log
   `Terhubung dengan <ip HP>`.

### Mode USB (ADB)

Dipakai kalau WiFi tidak stabil, atau memang mau latency lebih rendah lewat
kabel. Butuh setup tambahan di PC (`adb reverse`) — langkah lengkapnya ada
di [SETUP_ADB.md](SETUP_ADB.md). Setelah `server.py --mode tcp` jalan dan
`adb reverse` sudah dijalankan, pilih mode **"USB (ADB)"** di app (field IP
otomatis disembunyikan karena selalu `127.0.0.1`), isi Port yang sama, lalu
**Hubungkan ke PC**.

## Indikator status koneksi

Di bagian atas layar ada dua badge terpisah, berwarna hijau (terhubung)
atau merah (tidak terhubung):

- **● PC** — mencerminkan koneksi ke server PC (WiFi atau USB, tergantung
  mode yang dipilih). Jadi hijau begitu paket pertama berhasil terkirim,
  dan balik merah otomatis kalau ada error jaringan (mis. PC mati,
  `server.py` berhenti, WiFi terputus).
- **● Gamepad** — mencerminkan apakah gamepad Bluetooth fisik (iPega 9076)
  sedang terpasang ke HP, dipantau langsung dari sistem Android (bukan
  dari lalu-lintas data ke PC). Hijau berarti Android mendeteksi ada
  device gamepad terhubung; merah berarti tidak ada, walau app-nya sendiri
  berjalan normal.

Karena dua badge ini independen, kamu bisa langsung tahu bagian mana yang
bermasalah tanpa harus tebak-tebakan: PC merah + Gamepad hijau berarti
masalah di jaringan/server; PC hijau + Gamepad merah berarti iPega
ke-disconnect dari Bluetooth (biasanya gara-gara baterai lemah atau jarak
terlalu jauh) walau HP-nya tetap terhubung baik ke PC.

## Kalibrasi gyro

Setiap kali app dibuka, GyroPad otomatis mengkalibrasi gyro selama ~1.5
detik — **letakkan HP di meja/permukaan datar (jangan dipegang) selama
proses ini**, supaya bias sensor terukur akurat. Status prosesnya terlihat
di teks di bawah slider Sensitivitas ("Mengkalibrasi..." → "Kalibrasi
selesai, siap dipakai").

Kalau selama main kamu merasa aim/crosshair pelan-pelan "melayang" ke satu
arah walau HP dipegang diam (drift), ada dua kemungkinan penyebab & solusi:

- **Drift kecil, muncul bertahap** — biasanya sudah otomatis terkoreksi
  sendiri; GyroPad terus memantau saat HP diam & gyro-aim tidak sedang
  ditahan, lalu pelan-pelan mengoreksi bias tanpa perlu tindakan apapun
  darimu.
- **Drift terasa besar/tiba-tiba** (mis. habis HP kepanasan, atau abis
  dipakai keras) — tekan tombol **"Kalibrasi Ulang (letakkan HP diam)"**,
  taruh HP diam sebentar sampai status berubah jadi "Kalibrasi selesai".

## Menggunakan gyro-aim

- Nyalakan toggle **Aktifkan Gyro** di app.
- Atur **Sensitivitas Gyro** sesuai selera (mulai dari nilai default dulu,
  baru disesuaikan).
- Saat main, **tahan tombol L1** di gamepad fisik untuk mengaktifkan
  gyro-aim sementara (mirip gaya "hold to aim" di Switch/DS4) — gerakkan
  HP untuk menggeser arah bidik, lepas L1 untuk berhenti.
- Tombol besar **"TAHAN untuk GYRO AIM"** di layar app hanya untuk testing
  cepat tanpa perlu gamepad terpasang.

## Profil sensitivitas per-game

Sensitivitas yang enak di satu game belum tentu enak di game lain — GyroPad
bisa menyimpan beberapa preset sensitivitas bernama, tersimpan lokal di HP
(tidak perlu internet/akun).

- **Pindah profil**: pilih dari dropdown **"Profil Sensitivitas"** di atas
  slider — begitu dipilih, slider & sensitivitas gyro langsung mengikuti
  nilai profil itu.
- **Buat profil baru**: atur dulu slider sensitivitas sesuai keinginan
  (mis. sambil coba-coba di panel crosshair), lalu tekan tombol **"+"**,
  beri nama (mis. nama game-nya), tekan **Simpan**. Nilai slider SAAT ITU
  yang tersimpan sebagai profil baru.
- **Edit profil**: tidak ada tombol edit terpisah — cukup pilih profilnya
  dari dropdown, lalu geser slider seperti biasa. Perubahan otomatis
  tersimpan balik ke profil yang sedang aktif, tanpa perlu langkah simpan
  tambahan.
- **Hapus profil**: pilih profil yang mau dihapus dari dropdown, tekan
  **"Hapus"**, konfirmasi. Minimal harus selalu ada satu profil tersisa
  (tidak bisa menghapus profil terakhir).
- Profil yang terakhir aktif akan otomatis dipilih lagi saat app dibuka
  ulang.

## Crosshair kalibrasi & rumble

- Panel **"Preview arah gyro"** di bawah tombol gyro-aim menampilkan
  crosshair yang bergerak mengikuti gerakan HP saat gyro aktif — ini alat
  bantu buat merasakan/mengatur sensitivitas SEBELUM masuk game. Panel ini
  ada di dalam app HP, bukan overlay di atas layar game (app HP memang
  tidak bisa menggambar di atas layar PC).
- Dropdown tepat di atas panel crosshair biar bisa ganti **tema/gaya
  visual**-nya: Klasik, Scope Presisi (ala Monster Hunter), Bracket
  Taktis, Cincin Putus-putus, Chevron Berlian, atau Heksagon — murni
  kosmetik, tidak memengaruhi cara gyro-aim bekerja sama sekali. Pilihan
  tema tersimpan otomatis, tetap sama saat app dibuka lagi nanti.
- Teks **"Rumble dari game"** menunjukkan status getaran yang diterima
  balik dari PC. Ini menggantikan motor getar gamepad fisik yang tidak
  berfungsi — HP akan bergetar setiap kali game mengirim rumble ke virtual
  controller di PC. Tidak semua game/momen memicu rumble, jadi ini normal
  kalau kadang diam saja.

## Troubleshooting

| Masalah | Kemungkinan penyebab |
|---|---|
| Stick/tombol tidak terdeteksi sama sekali | Gamepad belum ter-pairing dengan benar, atau bukan gamepad HID standar (cek di `Settings > Bluetooth`, statusnya harus "Connected", bukan cuma "Paired") |
| "Sensor gyroscope tidak tersedia di device ini" | HP kamu memang tidak punya chip gyroscope fisik — fitur gyro tidak bisa dipakai, tapi gamepad tetap berfungsi normal |
| Status tidak berubah dari "Belum terhubung" / packet count tidak naik | Cek lagi IP PC, pastikan satu jaringan WiFi, cek firewall Windows tidak memblokir port UDP yang dipakai |
| Gyro terasa terlalu sensitif/lambat | Atur slider **Sensitivitas Gyro** di app, atau argumen `--gyro-scale` di `server.py` |
| Mode USB stuck di "Menyambung ke PC lewat USB..." | Lihat troubleshooting khusus di [SETUP_ADB.md](SETUP_ADB.md) |
| HP tidak bergetar sama sekali walau ada rumble di game | Cek izin VIBRATE tidak diblokir di pengaturan aplikasi (`Settings > Apps > GyroPad > Permissions`), dan cek game memang mengirim rumble ke controller Xbox |
| Crosshair/aim melayang ke satu arah walau HP diam | Tekan **"Kalibrasi Ulang"**, letakkan HP diam sampai status "Kalibrasi selesai" muncul — jangan gerakkan HP selama proses (~1.5 detik) |
| Kalibrasi terasa tidak akurat / masih drift setelah kalibrasi ulang | Kemungkinan HP masih sedikit bergerak/tergoyang selama proses ~1.5 detik kalibrasi — ulangi dengan HP benar-benar diam di permukaan datar (bukan dipegang tangan) |
| Sensitivitas balik ke nilai lama setelah pindah profil lalu balik lagi | Ini perilaku normal — tiap profil menyimpan nilainya sendiri-sendiri, geser slider saat profil itu aktif kalau memang mau diubah |
| Tombol "Hapus" tidak berefek / profil tidak hilang | Tidak bisa menghapus profil terakhir yang tersisa (minimal harus ada satu) — buat profil lain dulu sebelum menghapus yang lama |
| Badge "Gamepad" merah padahal iPega menyala & sudah di-pairing | Cek statusnya di `Settings > Bluetooth` — harus "Connected", bukan cuma "Paired"; kadang perlu toggle Bluetooth off/on kalau device stuck di "Paired" saja |
| Badge "PC" tetap merah walau `server.py` sudah jalan | Cek IP/Port sudah benar, satu jaringan WiFi (atau `adb reverse` sudah dijalankan untuk mode USB), dan firewall Windows tidak memblokir |
