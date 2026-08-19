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

2. **PC server** (`pc/server.py`) — menerima paket UDP, mem-parsing JSON,
   dan memetakannya ke virtual Xbox 360 controller lewat `vgamepad`
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

### Kenapa UDP, bukan lewat kabel USB/ADB?
Versi awal ini pilih WiFi (UDP) karena paling simpel untuk prototyping:
tidak perlu setup `adb forward`, device authorization, dsb — cukup satu
jaringan WiFi yang sama. UDP dipilih di atas TCP karena untuk input
real-time, paket yang telat lebih baik dilewati saja daripada di-retransmit
(retransmit TCP justru menambah latency yang terasa saat aiming).
Opsi ADB-over-USB sebagai mode alternatif (latency lebih rendah & lebih
stabil) ada di roadmap.

### Kenapa JSON, bukan format biner?
JSON dipilih di versi baseline ini karena mudah dibaca & didebug saat
prototyping (tinggal `print()` paket mentah untuk lihat apa yang salah).
Trade-off-nya ukuran paket lebih besar & parsing lebih lambat dibanding
biner. Untuk tuning latency lebih jauh, lihat catatan optimasi di
[PROTOCOL.md](PROTOCOL.md).

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
UdpGamepadSender (loop coroutine terpisah, kirim @ ~120Hz)
        │  JSON via UDP
        ▼
server.py: GyroPadServer.apply_state()
        │  gyaw/gpitch ditambahkan ke right stick jika gactive=true
        ▼
vgamepad.VX360Gamepad.update()
        │
        ▼
   Game menerima input sebagai controller Xbox biasa
```

## Kenapa mengirim snapshot berkala, bukan setiap event?

Event stick/gyro bisa datang puluhan-ratusan kali per detik. Kalau tiap
event langsung dikirim sebagai paket UDP terpisah, jaringan bisa kebanjiran
dan malah menambah latency (bufferbloat). `UdpGamepadSender` sebagai
gantinya membaca *snapshot* state terakhir di interval tetap (default
120Hz) — pendekatan umum di banyak remote-input tool (mis. Moonlight,
Sunshine, parsec-input).
