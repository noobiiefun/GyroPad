# Setup Mode USB (ADB)

Mode ini dipakai kalau WiFi tidak stabil, tidak tersedia, atau kamu memang
lebih suka koneksi kabel (latency umumnya lebih rendah & lebih stabil
dibanding WiFi).

## Kenapa perlu langkah tambahan (tidak "plug & play")?

Android Debug Bridge (`adb`) memang bisa nge-tunnel port lewat USB, tapi
**hanya untuk koneksi TCP** — ini batasan `adb` itu sendiri, bukan
keterbatasan GyroPad. Karena mode WiFi GyroPad memakai UDP, mode USB
memakai transport TCP terpisah (`TcpAdbTransport.kt` di sisi Android,
`TcpServer` di `server.py`) supaya tetap bisa lewat `adb reverse`.

## Prasyarat

1. **Android Platform Tools** (isinya `adb`) — download dari:
   https://developer.android.com/tools/releases/platform-tools
   Ekstrak, lalu tambahkan foldernya ke PATH Windows (atau jalankan `adb`
   langsung dari dalam folder itu).
2. **USB debugging** aktif di HP:
   `Settings > About phone` → tap `MIUI version` / `Build number` 7x untuk
   memunculkan Developer options → aktifkan **USB debugging**.
3. Kabel USB yang mendukung transfer data (bukan cuma kabel charging).

## Langkah-langkah

1. Colokkan HP ke PC lewat USB.
2. Di HP, akan muncul dialog "Allow USB debugging?" — centang
   "Always allow from this computer", lalu **Allow**.
3. Cek koneksi terdeteksi:
   ```bash
   adb devices
   ```
   Harus muncul device kamu dengan status `device` (bukan `unauthorized`).
4. Buka `adb reverse`, supaya port di PC "diproyeksikan" jadi
   `127.0.0.1:<port>` di sisi HP:
   ```bash
   adb reverse tcp:25565 tcp:25565
   ```
   Ganti `25565` kalau kamu pakai port lain — **dua angka ini harus sama**
   (port sisi HP : port sisi PC).
5. Jalankan server di PC dengan mode `tcp`:
   ```bash
   cd pc
   python server.py --mode tcp --port 25565
   ```
6. Di app GyroPad, pilih mode **"USB (ADB)"** (bukan WiFi), pastikan Port
   sama dengan yang dipakai di atas, lalu tekan **Hubungkan ke PC**.
   Field IP otomatis disembunyikan/diabaikan di mode ini karena selalu
   `127.0.0.1`.

## Catatan penting

- `adb reverse` **tidak permanen** — setiap kali HP dicabut-pasang ulang
  USB-nya, atau PC restart, kamu perlu menjalankan ulang command di langkah
  4 sebelum menyambungkan app.
- Kalau ganti port di app, ganti juga port di command `adb reverse` dan
  argumen `--port` di `server.py` — ketiganya harus sinkron.
- Untuk melihat daftar reverse tunnel yang aktif:
  ```bash
  adb reverse --list
  ```
- Untuk menghapus semua reverse tunnel (misal mau ganti port):
  ```bash
  adb reverse --remove-all
  ```

## Script pembantu (opsional)

Supaya tidak perlu ketik ulang tiap kali, simpan sebagai `start-usb.bat` di
folder `pc/` (Windows):

```bat
@echo off
adb reverse tcp:25565 tcp:25565
python server.py --mode tcp --port 25565
pause
```

## Troubleshooting

| Masalah | Solusi |
|---|---|
| `adb devices` tidak menampilkan device apapun | Cek driver USB terpasang, coba kabel/port USB lain, cek USB debugging benar-benar aktif |
| Status `unauthorized` di `adb devices` | Cabut-pasang ulang USB, terima dialog izin di HP |
| App GyroPad stuck di "Menyambung ke PC lewat USB..." | Pastikan `server.py --mode tcp` sudah jalan DULU sebelum tekan Hubungkan di app, dan `adb reverse` sudah dijalankan setelah HP tersambung |
| Rumble/state jalan sebentar lalu berhenti | Kemungkinan koneksi USB terputus (kabel longgar) — cek log `server.py`, akan muncul "Koneksi USB terputus" |
