# Arsitektur GyroPad

## Gambaran umum

GyroPad terdiri dari dua komponen yang berjalan terpisah dan berkomunikasi
lewat WiFi:

1. **Android app** (`android/`) — berjalan di HP, punya dua sumber input:
   - Gamepad fisik Bluetooth (iPega 9076), dibaca lewat Android input API
     standar (`MotionEvent` untuk stick/trigger, `KeyEvent` untuk tombol).
     HP tidak "meneruskan" Bluetooth mentah — Android sendiri yang menangani
     pairing HID, aplikasi tinggal mendengarkan event seperti halnya
     keyboard/mouse eksternal.
   - Gyroscope internal HP (`Sensor.TYPE_GYROSCOPE`), dibaca lewat
     `SensorManager`.

   Kedua sumber ini ditulis ke satu objek state bersama (`ControllerState`),
   lalu dikirim berkala ke PC lewat UDP.

2. **PC server** (`pc/server.py`) — menerima paket biner (lihat
   [PROTOCOL.md](PROTOCOL.md)), decode lewat `binary_protocol.py`, dan
   memetakannya ke virtual Xbox 360 controller lewat `vgamepad`
   (wrapper Python untuk driver **ViGEmBus**). Dari sudut pandang Windows/
   game, ini terlihat persis seperti controller Xbox asli yang tercolok.

## Kenapa desainnya begini?

### Kenapa gamepad fisik tetap dipakai, bukan tombol virtual di layar?
Tujuan awal project ini justru mempertahankan feel gamepad fisik (iPega
9076) yang sudah nyaman dipakai, hanya "menambal" satu kekurangannya yaitu
gyro. Solusi seperti Gamepad XO/Joy2Droid menjadikan HP sebagai controller
utuh (tombol virtual di layar) — cocok untuk kasus lain, tapi bukan tujuan
GyroPad.

### Kenapa gyro digabung ke right stick, bukan jadi axis terpisah?
Virtual Xbox 360 controller (yang dipakai ViGEmBus) cuma punya axis
standar Xbox: dua stick, dua trigger, D-pad, tombol. Tidak ada "axis gyro"
native di level driver ini. Cara paling kompatibel dengan game yang sudah
ada (termasuk Monster Hunter World/Rise di PC) adalah menambahkan delta
gyro sebagai pergerakan tambahan di atas right stick — mirip cara kerja
gyro-aiming di controller Switch/DS4 saat dipetakan ulang ke Xbox layer.

### Kenapa integrasi angular velocity, bukan `TYPE_ROTATION_VECTOR`?
`TYPE_ROTATION_VECTOR` memberi orientasi absolut (bagus untuk VR/kompas),
tapi untuk gyro-aim yang kita mau adalah *kecepatan* gerakan kamera relatif
terhadap gerakan tangan — mirip mouse. Maka yang dipakai adalah
`TYPE_GYROSCOPE` (rad/s) yang diintegrasikan jadi delta sudut per frame,
lalu direset setiap kali paket terkirim. Ini menghindari masalah drift
orientasi absolut dan gimbal lock.

### Kalibrasi bias & auto-koreksi drift (sejak v0.4)

Memakai `TYPE_GYROSCOPE` mentah punya konsekuensi: setiap chip gyroscope
punya sedikit **bias/offset bawaan** — nilai yang tetap terbaca sensor
walau HP benar-benar diam. Kalau tidak dikoreksi, bias ini terintegrasi
terus-menerus jadi delta yang membuat aim/crosshair "melayang" pelan ke
satu arah walau tangan diam sempurna. `GyroManager` menangani ini lewat
tiga lapis, berurutan dari yang paling terasa dampaknya:

1. **Kalibrasi awal** (`startCalibration()`) — dipanggil otomatis sekali
   saat `GyroManager.start()` (app baru dibuka), dan bisa dipanggil ulang
   manual lewat tombol "Kalibrasi Ulang" di UI. Selama ~1.5 detik,
   pembacaan sensor mentah dirata-ratakan (asumsi HP diam), hasilnya
   disimpan sebagai `biasYaw`/`biasPitch` dan dikurangkan dari SETIAP
   pembacaan sensor berikutnya, sebelum dipakai untuk apapun.
2. **Auto-koreksi drift berkelanjutan** (`updateStillnessTracking()`) —
   bias hasil kalibrasi awal bisa sedikit melayang lagi seiring waktu
   (mis. karena suhu chip berubah selama HP dipakai lama). Daripada
   menunggu user sadar ada drift lalu kalibrasi ulang manual, GyroManager
   terus memantau: kalau magnitude gerakan di bawah `stillnessThreshold`
   selama `stillnessRequiredSeconds` BERTURUT-TURUT, DAN `state.gyroActive`
   sedang false (supaya tidak salah mengoreksi saat user sengaja menahan
   bidikan diam-diam saat aiming), sisa bacaan sensor pelan-pelan diserap
   ke bias lewat low-pass filter (`driftCorrectionAlpha` kecil, supaya
   koreksinya halus dan tidak terasa sebagai "sentakan").
3. **Deadzone noise** (`noiseDeadzoneRadPerSec`) — setelah bias
   dikurangkan, noise sensor yang sangat kecil (jauh di bawah bias itu
   sendiri) masih bisa lolos dan bikin crosshair "gemetar" halus. Magnitude
   di bawah ambang ini diabaikan total (tidak diakumulasi jadi delta sama
   sekali), berbeda dari koreksi bias yang menggeser baseline.

Ketiganya independen dari format paket jaringan — murni logika lokal di
`GyroManager`, jadi tidak mengubah `PROTOCOL.md` sama sekali (server tidak
tahu dan tidak perlu tahu bahwa kalibrasi ini terjadi).

### Kenapa UDP, bukan lewat kabel USB/ADB?
Versi awal ini pilih WiFi (UDP) karena paling simpel untuk prototyping:
tidak perlu setup `adb forward`, device authorization, dsb — cukup satu
jaringan WiFi yang sama. UDP dipilih di atas TCP karena untuk input
real-time, paket yang telat lebih baik dilewati saja daripada di-retransmit
(retransmit TCP justru menambah latency yang terasa saat aiming).
Opsi ADB-over-USB sebagai mode alternatif (latency lebih rendah & lebih
stabil) ada di roadmap.

### Kenapa format biner, bukan JSON?
Versi awal (v0.1-v0.2) sempat memakai JSON karena mudah dibaca & didebug
saat prototyping (tinggal `print()` paket mentah untuk lihat isinya). Tapi
untuk paket yang dikirim puluhan-ratusan kali per detik, JSON teks
(~150-180 byte/paket dengan semua tanda kutip, kurung kurawal, nama field)
signifikan lebih besar dan lebih lambat di-parse dibanding biner (44 byte
tetap, tinggal baca offset). Sejak v0.3, protokol diganti total ke format
biner — layout byte lengkapnya ada di [PROTOCOL.md](PROTOCOL.md), dan
implementasinya di `BinaryProtocol.kt` (Android) / `binary_protocol.py`
(PC). Trade-off-nya: paket biner jauh lebih sulit dibaca manual saat
debug (tidak bisa `print()` langsung dan dibaca sekilas seperti JSON) -
kalau perlu debug isi paket, cara paling gampang adalah panggil
`decode_state()`/`decode_rumble()` lalu `print()` hasil dict-nya, bukan
`print()` byte mentah.

## Alur data per frame

```
GamepadInputManager.onGenericMotionEvent()/onKeyEvent()
        │  (setiap event dari gamepad fisik)
        ▼
   ControllerState (shared, thread-safe via synchronized block)
        ▲
        │  (setiap event dari sensor gyroscope, hanya saat gyroActive)
GyroManager.onSensorChanged()
        │
        ▼
UdpTransport / TcpAdbTransport (loop coroutine terpisah, kirim @ 120Hz/60Hz)
        │  paket biner 44 byte, via UDP atau TCP
        ▼
server.py: GamepadCore.apply_state()
        │  gyaw/gpitch ditambahkan ke right stick jika gactive=true
        ▼
vgamepad.VX360Gamepad.update()
        │
        ▼
   Game menerima input sebagai controller Xbox biasa
```

## Transport ganda: WiFi (UDP) vs USB (TCP/ADB)

Sejak v0.2, komunikasi HP↔PC diabstraksikan lewat interface
`GyroPadTransport` (`net/GyroPadTransport.kt`), dengan dua implementasi:

- `UdpTransport` — WiFi, satu `DatagramSocket` dipakai dua arah (kirim state,
  terima rumble), karena PC membalas ke alamat pengirim paket terakhir.
- `TcpAdbTransport` — USB, terhubung ke `127.0.0.1:<port>` (hasil tunneling
  `adb reverse`, lihat [SETUP_ADB.md](SETUP_ADB.md)) memakai satu koneksi
  TCP dua arah. Karena format sudah biner dengan ukuran tetap per jenis
  paket (44 byte state, 9 byte rumble), framing-nya cukup "baca persis
  sejumlah itu byte", tanpa perlu delimiter atau length-prefix tambahan.

`adb reverse`/`adb forward` cuma bisa nge-tunnel TCP, itu sebabnya mode USB
tidak bisa memakai UDP seperti mode WiFi — ini keterbatasan ADB, bukan
pilihan desain. `MainActivity` tinggal memilih implementasi mana yang
diinstansiasi berdasarkan radio button yang dipilih user; keduanya
mengimplementasikan interface yang sama sehingga sisa kode (UI, counter
paket, dsb) tidak perlu tahu bedanya.

## Alur rumble (PC → HP)

Ini pelengkap alur input di atas, arahnya kebalikan:

```
Game mengirim rumble ke virtual controller
        │
        ▼
ViGEmBus -> vgamepad.register_notification() callback terpanggil
        │  (large_motor, small_motor sebagai byte 0-255)
        ▼
GamepadCore._handle_rumble_notification() (server.py)
        │  dinormalisasi ke 0.0-1.0
        ▼
UdpServer/TcpServer mengirim paket biner rumble (type=0x02, lihat PROTOCOL.md)
   ke alamat/koneksi HP yang sedang aktif
        │
        ▼
UdpTransport/TcpAdbTransport.onRumbleReceived (Android)
        │
        ▼
MainActivity.applyRumble() -> Vibrator.vibrate(...)
```

Alasan utama fitur ini ada: motor getar fisik di gamepad (mis. iPega 9076)
bisa saja rusak/tidak didukung — dengan jalur ini, getaran dari game tetap
bisa "dirasakan" lewat motor getar HP.

## Crosshair kalibrasi (visual-only, tidak dikirim ke jaringan)

`GyroManager` menyimpan DUA akumulator gyro yang terpisah:

1. `state.gyroYaw` / `state.gyroPitch` — dikirim ke jaringan, direset ke 0
   oleh transport setiap kali paket terkirim (delta akumulatif per-paket).
2. `visualYaw` / `visualPitch` — TIDAK pernah dikirim, diklem ke rentang
   tetap (`visualRange`), dan meluruh balik ke 0 (`decayVisualOffset`) saat
   gyro tidak aktif — dipakai murni untuk menggerakkan `CrosshairView` di
   dalam app sebagai alat kalibrasi sensitivitas.

Dipisah sengaja supaya perilaku tampilan (yang butuh "pegas kembali ke
tengah" biar enak dilihat) tidak mengganggu data yang benar-benar dikirim
ke controller virtual di PC (yang harus murni representasi delta gerakan,
tanpa decay buatan).

Penting: crosshair ini muncul di LAYAR HP, di dalam app GyroPad sendiri —
bukan overlay yang muncul di atas game di layar PC. App Android tidak
punya akses untuk menggambar di atas tampilan PC; kalau suatu saat ingin
HUD sungguhan di layar PC, itu perlu komponen terpisah (mis. overlay window
always-on-top di sisi PC) yang saat ini belum ada di repo ini.

## Kenapa mengirim snapshot berkala, bukan setiap event?

Event stick/gyro bisa datang puluhan-ratusan kali per detik. Kalau tiap
event langsung dikirim sebagai paket UDP terpisah, jaringan bisa kebanjiran
dan malah menambah latency (bufferbloat). `UdpGamepadSender` sebagai
gantinya membaca *snapshot* state terakhir di interval tetap (default
120Hz) — pendekatan umum di banyak remote-input tool (mis. Moonlight,
Sunshine, parsec-input).
