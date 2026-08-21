"""
Notifikasi popup (Windows toast) + bunyi saat HP baru tersambung ke server.

Desain penting: SEMUA konfigurasi & file suara disimpan di dalam folder
`pc/` ini sendiri (notify_config.json, sounds/connect.wav) - BUKAN di
%APPDATA%, registry Windows, atau lokasi sistem lain. Alasannya: kalau
folder GyroPad ini dihapus/diinstall ulang (setara "uninstall" untuk
project sederhana seperti ini), semua kustomisasi (suara custom, on/off
toast) otomatis ikut hilang tanpa perlu uninstaller terpisah yang
membersihkan lokasi lain.

Semua kegagalan di modul ini SENGAJA didiamkan (try/except luas) - toast
library tidak terpasang, file suara rusak/hilang, atau OS bukan Windows
tidak boleh sampai menghentikan server inti. Notifikasi ini pelengkap,
bukan fitur yang menentukan jalan-tidaknya GyroPad.
"""

import json
import os

try:
    import winsound
    _HAS_WINSOUND = True
except ImportError:
    _HAS_WINSOUND = False

try:
    from win10toast import ToastNotifier
    _toaster = ToastNotifier()
except Exception:
    _toaster = None

_PC_DIR = os.path.dirname(os.path.abspath(__file__))
_CONFIG_PATH = os.path.join(_PC_DIR, "notify_config.json")

_DEFAULT_CONFIG = {
    "enabled": True,
    "toast_enabled": True,
    "sound_enabled": True,
    # Path relatif terhadap folder pc/ - ganti file ini (atau path-nya di
    # sini) untuk memakai bunyi custom sendiri.
    "sound_path": "sounds/connect.wav",
}


def _load_config() -> dict:
    if not os.path.exists(_CONFIG_PATH):
        _save_config(_DEFAULT_CONFIG)
        return dict(_DEFAULT_CONFIG)
    try:
        with open(_CONFIG_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        merged = dict(_DEFAULT_CONFIG)
        merged.update(data)
        return merged
    except (OSError, json.JSONDecodeError):
        return dict(_DEFAULT_CONFIG)


def _save_config(config: dict) -> None:
    try:
        with open(_CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(config, f, indent=2)
    except OSError:
        pass


def notify_connected(device_label: str) -> None:
    """
    Panggil ini SEKALI setiap kali ada koneksi baru (bukan tiap paket) -
    lihat titik panggilnya di server.py, sudah dijaga lewat pengecekan
    "apakah alamat/koneksi ini baru" di UdpServer/TcpServer.
    """
    config = _load_config()
    if not config.get("enabled", True):
        return

    message = f"Perangkat tersambung: {device_label}"

    if config.get("toast_enabled", True) and _toaster is not None:
        try:
            _toaster.show_toast("GyroPad", message, duration=4, threaded=True)
        except Exception:
            pass

    if config.get("sound_enabled", True) and _HAS_WINSOUND:
        sound_path = os.path.join(_PC_DIR, config.get("sound_path", "sounds/connect.wav"))
        try:
            if os.path.exists(sound_path):
                winsound.PlaySound(sound_path, winsound.SND_FILENAME | winsound.SND_ASYNC)
            else:
                # File custom belum ada / path salah - tetap kasih bunyi
                # fallback bawaan Windows daripada diam total.
                winsound.MessageBeep()
        except Exception:
            pass
