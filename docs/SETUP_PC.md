# Setup PC Server

GyroPad server saat ini hanya didukung di **Windows**, karena memakai
**ViGEmBus** untuk emulasi virtual Xbox 360 controller. (Dukungan Linux via
`uinput` ada di roadmap — lihat README.)

## Prasyarat

1. **Python 3.9+** — https://www.python.org/downloads/
   Saat instalasi, centang **"Add python.exe to PATH"**.
2. **ViGEmBus driver** — driver Windows yang menyediakan virtual
   Xbox/DS4 controller di level sistem.
   - Download installer terbaru dari halaman rilis resmi:
     https://github.com/ViGEm/ViGEmBus/releases
   - Jalankan installer-nya, restart PC kalau diminta.
   - Ini WAJIB terpasang sebelum `server.py` bisa jalan — tanpa ini,
     `vgamepad` akan gagal membuat virtual controller.

## Instalasi dependency Python

Dari folder `pc/`:

```bash
cd pc
pip install -r requirements.txt
```

## Menjalankan server

```bash
python server.py
```

Ini menjalankan mode WiFi (UDP) secara default. Kalau mau pakai USB/ADB,
lihat [SETUP_ADB.md](SETUP_ADB.md) — command-nya beda (`--mode tcp`).

Secara default, server:
- Mendengarkan di port UDP `25565`
- Skala pengaruh gyro ke stick: `0.02`
- Reset controller ke netral kalau tidak ada paket masuk selama 2 detik
  (mis. app HP ditutup / koneksi putus) — supaya stick tidak "nyangkut"
- Mendengarkan rumble dari game lewat `register_notification` milik
  `vgamepad`, dan mengirimkannya balik ke HP yang sedang terhubung (lihat
  [PROTOCOL.md](PROTOCOL.md) bagian "Paket rumble")

Opsi yang bisa diubah:

```bash
python server.py --mode udp --port 25565 --gyro-scale 0.02 --timeout 2.0
```

| Argumen | Default | Keterangan |
|---|---|---|
| `--mode` | `udp` | `udp` (WiFi) atau `tcp` (USB/ADB, lihat SETUP_ADB.md) |
| `--port` | `25565` | Port, harus sama dengan yang diisi di app Android |
| `--gyro-scale` | `0.02` | Semakin besar, semakin sensitif gyro-nya |
| `--timeout` | `2.0` | [mode udp saja] Detik idle sebelum controller direset ke netral |

Saat pertama kali menjalankan `server.py`, Windows biasanya akan
memunculkan dialog **Windows Defender Firewall** minta izin akses jaringan
— pilih **Allow access** (minimal untuk jaringan Private/rumah), supaya
paket UDP dari HP bisa sampai.

## Verifikasi virtual controller terbaca Windows

1. Jalankan `server.py`.
2. Buka `Settings > Bluetooth & devices > Devices` atau ketik
   `joy.cpp` / gunakan **"Set up USB game controllers"** (`joy.cpp` legacy
   Control Panel applet, cari lewat Start Menu: "Set up USB game
   controllers") — kamu akan melihat entry controller Xbox 360 muncul
   begitu server jalan.
3. Sambungkan app Android (lihat [SETUP_ANDROID.md](SETUP_ANDROID.md)) dan
   gerakkan stick — nilainya harus ikut bergerak di test panel tadi.

## Menjalankan di game (mis. Monster Hunter)

Karena controller yang diemulasikan terlihat sebagai controller Xbox 360
asli, kebanyakan game (termasuk yang lewat Steam) akan otomatis
mendeteksinya sebagai gamepad biasa — tidak perlu konfigurasi tambahan di
sisi game. Kalau game punya opsi "Controller/Gamepad support", pastikan itu
aktif.

## Troubleshooting

| Masalah | Solusi |
|---|---|
| `ModuleNotFoundError: No module named 'vgamepad'` | Jalankan `pip install -r requirements.txt` di folder `pc/` |
| Error terkait ViGEmBus / virtual device gagal dibuat | Pastikan ViGEmBus driver benar-benar terpasang (cek Device Manager, cari "Nefarius Virtual Gamepad Emulation Bus"), restart PC setelah instalasi |
| Server jalan tapi tidak ada log "Terhubung dengan ..." | HP belum kirim paket — cek [SETUP_ANDROID.md](SETUP_ANDROID.md) bagian "Menghubungkan ke PC", cek firewall |
| Stick di game "nyangkut" ke satu arah setelah app HP ditutup paksa | Tunggu beberapa detik (default timeout 2 detik), server otomatis reset ke netral. Kalau tidak, restart `server.py` |
| HP tidak pernah bergetar walau game seharusnya kasih rumble | Tidak semua game mengirim rumble ke controller Xbox (tergantung implementasi game-nya); pastikan juga toggle rumble/vibration aktif di pengaturan game tersebut |
