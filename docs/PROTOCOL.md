# Protokol Data GyroPad

Versi: `0.3` (BINER, dua arah, UDP atau TCP)

> Sebelum v0.3, protokol ini pakai JSON teks. Sudah diganti total ke format
> biner demi latency lebih rendah (paket lebih kecil, parsing lebih cepat).
> Kalau kamu lihat referensi JSON di kode lama/commit lama, itu format usang
> — dokumen ini HANYA mendeskripsikan format biner yang dipakai sekarang.

## Transport

GyroPad mendukung dua mode transport, tapi FORMAT PAKET BINER-nya sama
persis di keduanya — cuma cara framing-nya beda:

| Mode | Protokol | Framing | Kapan dipakai |
|---|---|---|---|
| WiFi | UDP | satu datagram = satu paket biner (batas paket otomatis dari UDP) | default, HP & PC satu jaringan |
| USB/ADB | TCP (via `adb reverse`) | ukuran paket TETAP per arah (44 byte state, 9 byte rumble) — dibaca persis sejumlah itu, tanpa delimiter | lihat [SETUP_ADB.md](SETUP_ADB.md) |

- Default port: `25565` (bisa diganti, lihat `--port` di `server.py` dan
  field Port di app Android — HARUS sama di kedua sisi)
- Rate kirim state (HP → PC): **120 paket/detik** mode UDP, **60 paket/detik**
  mode TCP (`sendRateHz` di `UdpTransport.kt` / `TcpAdbTransport.kt`)
- Komunikasi **DUA ARAH**: selain state HP→PC, ada juga paket rumble PC→HP
  (lihat bagian bawah).
- Semua angka multi-byte pakai **big-endian** (network byte order). Ini
  WAJIB sama di kedua sisi — Android pakai
  `ByteBuffer.order(ByteOrder.BIG_ENDIAN)`, Python pakai `'>'` di format
  `struct`. Kalau salah satu sisi diubah ke little-endian tanpa mengubah
  yang lain, semua angka float/int akan terbaca kacau tanpa error yang
  jelas (silent corruption) — jadi ini bagian paling penting untuk dijaga
  konsisten kalau ada yang mau modifikasi protokol ini.

## Kenapa ganti dari JSON ke biner?

Paket state dikirim puluhan-ratusan kali per detik. JSON teks (~150-180
byte per paket dengan semua tanda kutip, kurung kurawal, nama field)
signifikan lebih besar dan lebih lambat di-parse dibanding biner (44 byte
tetap, tinggal baca offset per field). Untuk input real-time seperti
gyro-aim, setiap milidetik yang dihemat dari serialisasi ikut mengurangi
latency yang terasa di tangan.

## Paket STATE (HP → PC) — 44 byte

| Offset | Ukuran | Field      | Tipe          | Rentang / Keterangan |
|--------|--------|------------|---------------|----------------------|
| 0      | 1      | `type`     | uint8         | Selalu `0x01` (TYPE_STATE) |
| 1      | 4      | `lx`       | float32       | -1.0 .. 1.0, left stick sumbu X |
| 5      | 4      | `ly`       | float32       | -1.0 .. 1.0, left stick sumbu Y |
| 9      | 4      | `rx`       | float32       | -1.0 .. 1.0, right stick sumbu X (dari gamepad fisik, belum ditambah gyro) |
| 13     | 4      | `ry`       | float32       | -1.0 .. 1.0, right stick sumbu Y (dari gamepad fisik, belum ditambah gyro) |
| 17     | 4      | `lt`       | float32       | 0.0 .. 1.0, left trigger analog |
| 21     | 4      | `rt`       | float32       | 0.0 .. 1.0, right trigger analog |
| 25     | 2      | `buttons`  | uint16        | Bitmask, lihat tabel di bawah |
| 27     | 4      | `gyaw`     | float32       | Derajat, delta rotasi yaw sejak paket sebelumnya |
| 31     | 4      | `gpitch`   | float32       | Derajat, delta rotasi pitch sejak paket sebelumnya |
| 35     | 1      | `gactive`  | uint8 (0/1)   | Apakah gyro sedang aktif (hold-to-aim) |
| 36     | 8      | `ts`       | int64         | Epoch milliseconds saat paket dibuat di HP |

Total: **44 byte**.

Catatan penting soal `gyaw`/`gpitch`: nilai ini adalah **delta akumulatif
sejak paket terakhir dikirim**, bukan posisi absolut. Server harus
menambahkannya ke posisi stick saat ini, bukan menimpanya. Setelah satu
paket terkirim, sisi Android mereset akumulator ini ke 0.

## Paket RUMBLE (PC → HP) — 9 byte

| Offset | Ukuran | Field   | Tipe    | Rentang / Keterangan |
|--------|--------|---------|---------|----------------------|
| 0      | 1      | `type`  | uint8   | Selalu `0x02` (TYPE_RUMBLE) |
| 1      | 4      | `large` | float32 | 0.0 .. 1.0, motor rumble berat/low-frequency |
| 5      | 4      | `small` | float32 | 0.0 .. 1.0, motor rumble ringan/high-frequency |

Total: **9 byte**.

Arah ini terjadi saat game mengirim rumble ke virtual controller di PC —
lihat `GamepadCore._handle_rumble_notification` di `server.py`. Di sisi
Android, dua nilai motor ini digabung jadi satu intensitas (karena
kebanyakan HP cuma punya satu motor getar) lewat
`RumbleState.combinedIntensity()`, lalu dipetakan ke amplitude
`VibrationEffect` (API 26+) atau `vibrate(ms)` biasa di Android lama.

Catatan: mode UDP mengirim balik rumble ke alamat pengirim paket state
TERAKHIR yang diterima server — jadi pastikan app tetap mengirim state
secara berkala (sudah otomatis, selama app aktif & terhubung) supaya server
tahu ke mana harus membalas.

## Bitmask tombol (`buttons`)

Harus identik antara `GamepadButton` (`ControllerState.kt`, Android) dan
`BUTTON_MAP` di `server.py`. Kalau menambah tombol baru, update KEDUA sisi
sekaligus.

| Bit (nilai)   | Tombol         |
|---------------|----------------|
| `1 << 0` (1)  | A              |
| `1 << 1` (2)  | B              |
| `1 << 2` (4)  | X              |
| `1 << 3` (8)  | Y              |
| `1 << 4` (16) | L1             |
| `1 << 5` (32) | R1             |
| `1 << 6` (64) | L3 (klik stick kiri) |
| `1 << 7` (128)| R3 (klik stick kanan) |
| `1 << 8` (256)| Start          |
| `1 << 9` (512)| Select/Back    |
| `1 << 10`     | D-Pad Up       |
| `1 << 11`     | D-Pad Down     |
| `1 << 12`     | D-Pad Left     |
| `1 << 13`     | D-Pad Right    |

Nilai maksimum bitmask saat ini `0x3FFF` (16383, 14 bit terisi semua),
jelas muat di uint16 (offset 25-26 di paket state).

Contoh: `buttons = 20` berarti bit 2 (X, nilai 4) + bit 4 (L1, nilai 16)
sedang ditekan bersamaan (4 + 16 = 20).

Catatan: L1 di baseline ini dipakai ganda sebagai tombol hold-to-aim gyro
(lihat `MainActivity.kt`), jadi saat L1 ditekan, `gactive` otomatis `true`
DAN bit L1 di `buttons` juga ikut `1`. Kalau kamu tidak ingin L1 terkirim
sebagai tombol biasa ke game saat dipakai untuk gyro, silakan sesuaikan di
`dispatchKeyEvent()`.

## Gyro → right stick

Di server, delta gyro ditambahkan ke `rx`/`ry` sebelum dikirim ke virtual
controller, hanya saat `gactive == true`:

```python
if gyro_active:
    rx += gyaw * gyro_to_stick_scale
    ry -= gpitch * gyro_to_stick_scale
```

`gyro_to_stick_scale` diatur lewat argumen `--gyro-scale` saat menjalankan
`server.py` (default `0.02`). Semakin besar nilainya, semakin sensitif
gyro-nya.

## Implementasi referensi

- Android: `android/app/src/main/java/com/gyropad/app/net/BinaryProtocol.kt`
  (`encodeState()` untuk paket state, `decodeRumble()` untuk paket rumble)
- Python: `pc/binary_protocol.py` (`decode_state()` dan `encode_rumble()`)

Kedua file ini **saling bergantung** — kalau ubah layout byte di salah
satu, wajib ubah juga di file satunya dengan format persis sama, atau
komunikasi akan gagal total (bukan error yang jelas, tapi data yang
terbaca ngaco).

## Rencana optimasi lanjutan (belum diimplementasikan)

- **Version byte**: menambah 1 byte versi protokol di awal paket, supaya
  kalau format berubah lagi di masa depan, kedua sisi bisa saling
  mendeteksi ketidakcocokan versi dan memberi pesan error yang jelas
  (dibanding sekarang: paket dengan layout salah cuma di-skip diam-diam
  lewat validasi `len(raw) != STATE_PACKET_SIZE`).
- **Delta/interpolasi stick**: untuk mode dengan bandwidth sangat terbatas,
  bisa dipertimbangkan hanya mengirim field yang berubah dari paket
  sebelumnya (perlu skema flag/dirty-bit tambahan).
