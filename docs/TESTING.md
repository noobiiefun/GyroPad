# Testing Latency & Tombol

Dokumen ini berisi cara-cara yang lazim dipakai untuk menguji GyroPad -
baik latency (seberapa cepat input HP sampai jadi aksi di game) maupun
kebenaran mapping tombol.

## Testing latency

Total latency yang kamu rasakan sebenarnya gabungan dari beberapa tahap:

```
[gerak tangan] -> [sensor/gamepad baca] -> [HP proses] -> [jaringan ke PC]
   -> [server.py proses] -> [ViGEmBus] -> [game baca input] -> [render ke layar]
```

GyroPad cuma mengontrol bagian tengah (baca sensor sampai ViGEmBus). Tiga
cara berikut fokus mengukur bagian yang relevan buat debugging GyroPad:

### 1. Round-trip timestamp (paling praktis, tanpa alat tambahan)

Setiap paket state yang dikirim HP sudah menyertakan field `ts` (epoch
milliseconds saat paket dibuat di HP, lihat [PROTOCOL.md](PROTOCOL.md)).
Cara paling sederhana untuk memperkirakan latency jaringan:

1. Pastikan jam HP & PC kurang lebih sinkron (biasanya otomatis lewat NTP,
   selisihnya di jaringan lokal biasanya sangat kecil).
2. Tambahkan log sementara di `server.py` untuk membandingkan `ts` dari
   paket dengan `time.time() * 1000` saat paket diterima:
   ```python
   # tempel sementara di GamepadCore.apply_state() atau di titik terima paket
   latency_ms = (time.time() * 1000) - data.get("ts", 0)
   print(f"latency: {latency_ms:.1f} ms")
   ```
3. Amati angkanya selama beberapa menit main. Ini mengukur **network +
   parsing**, BUKAN waktu render game.

Catatan: ini belum jadi fitur bawaan di UI (belum ada tombol "Test
Latency" di app) — masuk daftar di [ROADMAP.md](ROADMAP.md).

### 2. Kamera slow-motion (paling akurat, dipakai komunitas speedrun/FGC)

1. Rekam layar PC + tangan kamu memegang HP dalam satu frame kamera,
   idealnya 120-240fps (kebanyakan HP flagship & beberapa mid-range sudah
   mendukung ini di mode Slow-Mo kamera).
2. Gerakkan HP secara tiba-tiba (mis. sentakan cepat), lalu putar ulang
   rekaman frame-by-frame.
3. Hitung selisih frame antara saat HP mulai bergerak vs saat karakter di
   game mulai bereaksi di layar. Bagi jumlah frame dengan frame rate
   rekaman untuk dapat waktu dalam milidetik.

Ini mengukur **total end-to-end latency**, termasuk bagian yang di luar
kendali GyroPad (render game, refresh rate monitor, dll) - jadi berguna
untuk melihat gambaran besar, bukan buat mengisolasi masalah di GyroPad
sendiri.

### 3. Windows Game Controller test panel

Buka Start Menu, cari **"Set up USB game controllers"** (panel `joy.cpp`
bawaan Windows, sudah ada sejak lama). Ini bukan pengukur waktu presisi,
tapi enak dipakai untuk melihat responsivitas kasar secara visual - kalau
ada delay atau stutter yang kentara, biasanya kelihatan di sini duluan
sebelum masuk ke game.

## Testing tombol / mapping

Virtual controller yang dibuat GyroPad ada di level Windows (XInput), jadi
bisa dites pakai tool controller generik, tidak perlu masuk game dulu:

| Tool | Kelebihan |
|---|---|
| **Set up USB game controllers** (Windows, cari lewat Start Menu) | Bawaan Windows, tab "Test" menyorot tombol yang ditekan real-time, stick digambar sebagai crosshair |
| [hardwaretester.com/gamepad](https://hardwaretester.com/gamepad) | Browser-based (pakai Gamepad API), tanpa install, tampilan lebih rapi |
| Steam (`Big Picture Mode > Settings > Controller Settings > Preview/Test`) | Paling relevan kalau kamu main lewat Steam, karena jalur ujinya sama seperti saat main beneran |

Alur testing yang disarankan tiap kali ubah kode mapping tombol
(`GamepadInputManager.kt` atau `BUTTON_MAP` di `server.py`):

1. Jalankan `server.py`, hubungkan app GyroPad.
2. Buka salah satu tool di atas.
3. Tekan tiap tombol di gamepad fisik satu-satu, cocokkan yang menyala di
   tool dengan tombol yang seharusnya (mis. tombol **A** di iPega harus
   membuat **A** menyala, bukan **B**).
4. Gerakkan tiap stick ke 4 arah ekstrem + coba kembalikan ke tengah,
   pastikan indikator kembali ke posisi netral (bukan "nyangkut" karena
   deadzone kurang pas).
