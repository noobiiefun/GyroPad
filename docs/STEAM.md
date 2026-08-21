# Gyro-Aim di Steam (dan game non-Steam)

Banyak tutorial "pakai gyro di PC" menyuruh emulasi controller sebagai
**DualShock 4 (DS4)**, karena Steam Input punya tab "Gyro" khusus yang bisa
me-remap gyro DS4/DualSense jadi gerakan kamera - bahkan untuk game yang
aslinya tidak punya dukungan gyro sama sekali. GyroPad **TIDAK** memakai
jalur itu. Dokumen ini menjelaskan kenapa, dan kenapa itu tetap oke.

## Kenapa GyroPad tetap emulasi Xbox, bukan DS4

Ada dua cara mencampur gyro ke dalam sebuah virtual controller:

1. **Cara Steam/DS4**: controller melaporkan gyro sebagai data MENTAH
   terpisah (axis gyro asli), lalu **Steam Input** yang mencampurnya jadi
   gerakan stick kanan/mouse, dikonfigurasi lewat tab Gyro di pengaturan
   controller Steam.
2. **Cara GyroPad**: gyro dicampur ke stick kanan **di server PC, SEBELUM**
   data itu sampai ke Windows/Steam/game sama sekali. Jadi yang "dilihat"
   Windows cuma controller Xbox biasa yang stick kanannya kebetulan
   bergerak sendiri saat kamu menggerakkan HP.

Karena percampurannya sudah selesai sebelum sampai ke OS, GyroPad **tidak
butuh Steam Input dikonfigurasi apapun** — tidak perlu buka tab Gyro, tidak
perlu ganti "Controller Layout", tidak perlu Steam Input diaktifkan sama
sekali. Ini juga sekaligus alasan kenapa GyroPad **bekerja sama baiknya di
game non-Steam** (yang tidak lewat Steam Input) — beda dengan pendekatan
DS4 yang manfaatnya baru terasa kalau game/launcher-nya lewat Steam Input.

Alasan teknis kenapa GyroPad tidak (untuk sekarang) memakai jalur DS4:
library `vgamepad` yang dipakai server memang bisa emulasi DS4, tapi API
publiknya **tidak mengekspos field gyro/accelerometer** ke Python (field
itu ada di level driver ViGEmBus, tapi tidak "diteruskan" oleh wrapper
Python-nya). Untuk benar-benar mengirim gyro asli ke Steam, perlu bypass
`vgamepad` dan bicara langsung ke DLL `ViGEmClient` pakai `ctypes` — jauh
lebih rumit dan rawan rapuh (gampang rusak kalau versi driver berubah).
Karena tujuan akhirnya (gyro menggerakkan kamera) sudah tercapai lewat
cara yang lebih sederhana, GyroPad tetap pada pendekatan Xbox + stick.

## Yang perlu kamu lakukan di Steam: TIDAK ADA

Karena GyroPad terlihat sebagai controller Xbox 360 biasa, Steam menangani
seperti kamu colok controller Xbox asli — kebanyakan game punya dukungan
native Xbox controller, jadi biasanya langsung terdeteksi tanpa konfigurasi
tambahan. Tidak perlu:
- Mengaktifkan Steam Input secara khusus
- Membuka tab Gyro di pengaturan controller
- Mengubah "Controller Layout" jadi DS4/PlayStation

Kalau game kamu punya opsi "Controller Support" atau semacamnya, pastikan
itu aktif — selebihnya berjalan otomatis.

## Perbandingan dengan cara DS4 + Steam Gyro

| | GyroPad (Xbox + stick, cara sekarang) | DS4 asli + Steam Input Gyro |
|---|---|---|
| Butuh Steam Input aktif? | Tidak | Ya |
| Jalan di game non-Steam? | Ya | Tidak (perlu Steam Input) |
| Sensitivitas diatur di mana | Slider/profil di app GyroPad | Tab Gyro di Steam |
| Mode "gyro sebagai mouse" (relative look, bukan simulasi stick) | Tidak tersedia | Tersedia |
| Kompleksitas implementasi | Sederhana (sudah jalan) | Perlu bypass driver level rendah |

Baris yang paling penting buat disadari: **"gyro sebagai mouse"**. Steam
Input punya mode di mana gyro dipetakan langsung jadi gerakan mouse
relatif (biasanya terasa lebih presisi buat aiming FPS dibanding simulasi
stick, karena tidak melalui kurva respons analog stick). GyroPad saat ini
HANYA bisa menggerakkan stick kanan (karena itu satu-satunya axis yang
tersedia di virtual controller Xbox) — jadi kalau kamu pernah coba gyro
lewat DS4+Steam dalam mode mouse dan terasa lebih "tajam", itu memang
bukan sesuatu yang bisa ditandingi lewat simulasi stick. Kalau ini penting
buat kamu, opsi B (DS4 + bypass ke ViGEmClient langsung) tetap ada sebagai
kemungkinan pengembangan lanjutan - lihat catatan di
[ROADMAP.md](ROADMAP.md).

## Rekomendasi pemakaian

1. Atur **Sensitivitas Gyro** di app GyroPad sampai terasa pas untuk game
   yang sedang dimainkan (bikin profil terpisah per game, lihat bagian
   "Profil sensitivitas per-game" di [SETUP_ANDROID.md](SETUP_ANDROID.md)).
2. Tahan **L1** (atau tombol yang kamu pilih di `dispatchKeyEvent()`) saat
   ingin membidik presisi — ini yang menjalankan percampuran gyro ke stick
   kanan (lihat `gyro_to_stick_scale` di [PROTOCOL.md](PROTOCOL.md)).
3. Kalau gerakan kamera terasa terlalu "kental"/lambat merespons dibanding
   gyro asli DS4 yang pernah kamu coba, itu wajar — kamu sedang
   membandingkan simulasi stick vs mode mouse asli Steam. Naikkan
   sensitivitas atau `--gyro-scale` di server buat mendekatkan rasanya.
