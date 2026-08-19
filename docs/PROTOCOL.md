# Protokol Data GyroPad

Versi: `0.1` (baseline, JSON over UDP)

## Transport

- Protokol: **UDP**
- Arah: HP (client) → PC (server), satu arah (server tidak membalas paket)
- Default port: `25565` (bisa diganti, lihat `--port` di `server.py` dan
  field Port di app Android)
- Rate kirim default: **120 paket/detik** (`sendRateHz` di `UdpGamepadSender.kt`)

## Format paket

Setiap paket adalah satu objek JSON UTF-8, dikirim sebagai isi datagram UDP
(tanpa header tambahan). Contoh:

```json
{
  "lx": 0.42,
  "ly": -0.10,
  "rx": 0.0,
  "ry": 0.0,
  "lt": 0.0,
  "rt": 1.0,
  "btn": 20,
  "gyaw": 1.35,
  "gpitch": -0.22,
  "gactive": true,
  "ts": 1732000000123
}
```

| Field     | Tipe    | Rentang        | Keterangan                                                                 |
|-----------|---------|----------------|------------------------------------------------------------------------|
| `lx`      | float   | -1.0 .. 1.0    | Left stick sumbu X                                                     |
| `ly`      | float   | -1.0 .. 1.0    | Left stick sumbu Y                                                     |
| `rx`      | float   | -1.0 .. 1.0    | Right stick sumbu X (dari gamepad fisik, belum ditambah gyro)          |
| `ry`      | float   | -1.0 .. 1.0    | Right stick sumbu Y (dari gamepad fisik, belum ditambah gyro)          |
| `lt`      | float   | 0.0 .. 1.0     | Left trigger analog                                                    |
| `rt`      | float   | 0.0 .. 1.0     | Right trigger analog                                                   |
| `btn`     | int     | bitmask        | Lihat tabel bitmask tombol di bawah                                    |
| `gyaw`    | float   | derajat        | Delta rotasi yaw (kiri/kanan) sejak paket sebelumnya                   |
| `gpitch`  | float   | derajat        | Delta rotasi pitch (atas/bawah) sejak paket sebelumnya                 |
| `gactive` | boolean | true/false     | Apakah gyro sedang aktif (mis. tombol hold-to-aim sedang ditekan)      |
| `ts`      | long    | epoch ms       | Timestamp saat paket dibuat di HP (buat debugging latency)             |

Catatan penting soal `gyaw`/`gpitch`: nilai ini adalah **delta akumulatif
sejak paket terakhir dikirim**, bukan posisi absolut. Server harus
menambahkannya ke posisi stick saat ini, bukan menimpanya. Setelah satu
paket terkirim, sisi Android mereset akumulator ini ke 0.

## Bitmask tombol (`btn`)

Harus identik antara `ControllerState.kt` (Android) dan `BUTTON_MAP` di
`server.py`. Kalau menambah tombol baru, update KEDUA sisi.

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

Contoh: `btn = 20` berarti bit 2 (X, nilai 4) + bit 4 (L1, nilai 16) sedang
ditekan bersamaan (4 + 16 = 20).

Catatan: L1 di baseline ini dipakai ganda sebagai tombol hold-to-aim gyro
(lihat `MainActivity.kt`), jadi saat L1 ditekan, `gactive` otomatis `true`
DAN bit L1 di `btn` juga ikut `1`. Kalau kamu tidak ingin L1 terkirim
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

## Rencana optimasi (belum diimplementasikan)

Untuk versi berikutnya, format biner (`struct`) lebih disarankan untuk
menekan ukuran paket dan waktu parsing, misalnya:

```
[4 bytes lx][4 bytes ly][4 bytes rx][4 bytes ry]
[4 bytes lt][4 bytes rt][2 bytes btn]
[4 bytes gyaw][4 bytes gpitch][1 byte gactive]
[8 bytes ts]
```

= 39 byte per paket (vs ~180 byte untuk JSON setara), dikodekan pakai
`java.nio.ByteBuffer` di Android dan modul `struct` di Python. Ini masuk
roadmap, kontribusi welcome.
