"""
GyroPad Server
==============
Menerima paket JSON dari aplikasi Android GyroPad lewat UDP, lalu
mengemulasikan virtual Xbox 360 controller di Windows lewat ViGEmBus
(via library `vgamepad`).

Cara input digabung:
- Left stick, right stick, trigger, dan tombol datang langsung dari
  gamepad fisik (iPega 9076) yang dibaca Android lewat MotionEvent/KeyEvent.
- Delta gyro (gyaw, gpitch) DITAMBAHKAN ke posisi right stick, hanya saat
  `gactive` true (mis. saat tombol L1 ditahan di HP). Ini membuat gyro
  berfungsi sebagai "fine aim" di atas stick kanan biasa - persis seperti
  gyro-aiming di controller Switch/DS4.

Requirement:
    pip install -r requirements.txt
    (Windows) install ViGEmBus driver: https://github.com/ViGEm/ViGEmBus/releases

Jalankan:
    python server.py --port 25565
"""

import argparse
import json
import socket
import sys
import time

try:
    import vgamepad as vg
except ImportError:
    print("Library 'vgamepad' belum terpasang. Jalankan: pip install vgamepad")
    sys.exit(1)


# Bitmask tombol - HARUS SAMA PERSIS dengan GamepadButton.kt di sisi Android.
BUTTON_MAP = {
    1 << 0: vg.XUSB_BUTTON.XUSB_GAMEPAD_A,
    1 << 1: vg.XUSB_BUTTON.XUSB_GAMEPAD_B,
    1 << 2: vg.XUSB_BUTTON.XUSB_GAMEPAD_X,
    1 << 3: vg.XUSB_BUTTON.XUSB_GAMEPAD_Y,
    1 << 4: vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER,
    1 << 5: vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER,
    1 << 6: vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB,
    1 << 7: vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB,
    1 << 8: vg.XUSB_BUTTON.XUSB_GAMEPAD_START,
    1 << 9: vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK,
    1 << 10: vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP,
    1 << 11: vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN,
    1 << 12: vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT,
    1 << 13: vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT,
}


def clamp(value: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, value))


class GyroPadServer:
    def __init__(self, port: int, gyro_to_stick_scale: float, timeout_s: float):
        self.port = port
        # Seberapa besar 1 derajat delta gyro mempengaruhi posisi stick (-1..1).
        # Nilai kecil = gyro halus/presisi, nilai besar = gyro sensitif/cepat.
        self.gyro_to_stick_scale = gyro_to_stick_scale
        self.timeout_s = timeout_s

        self.gamepad = vg.VX360Gamepad()
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind(("0.0.0.0", self.port))
        self.sock.settimeout(1.0)

        self.last_packet_time = 0.0

    def apply_state(self, data: dict) -> None:
        lx = clamp(float(data.get("lx", 0.0)), -1.0, 1.0)
        ly = clamp(float(data.get("ly", 0.0)), -1.0, 1.0)
        rx = clamp(float(data.get("rx", 0.0)), -1.0, 1.0)
        ry = clamp(float(data.get("ry", 0.0)), -1.0, 1.0)
        lt = clamp(float(data.get("lt", 0.0)), 0.0, 1.0)
        rt = clamp(float(data.get("rt", 0.0)), 0.0, 1.0)
        buttons = int(data.get("btn", 0))
        gyro_active = bool(data.get("gactive", False))
        gyaw = float(data.get("gyaw", 0.0))
        gpitch = float(data.get("gpitch", 0.0))

        if gyro_active:
            rx += gyaw * self.gyro_to_stick_scale
            ry -= gpitch * self.gyro_to_stick_scale  # dongak = kamera naik
            rx = clamp(rx, -1.0, 1.0)
            ry = clamp(ry, -1.0, 1.0)

        self.gamepad.left_joystick_float(x_value_float=lx, y_value_float=ly)
        self.gamepad.right_joystick_float(x_value_float=rx, y_value_float=ry)
        self.gamepad.left_trigger_float(value_float=lt)
        self.gamepad.right_trigger_float(value_float=rt)

        for mask, xusb_button in BUTTON_MAP.items():
            if buttons & mask:
                self.gamepad.press_button(button=xusb_button)
            else:
                self.gamepad.release_button(button=xusb_button)

        self.gamepad.update()

    def run(self) -> None:
        print(f"[GyroPad] Server jalan di UDP port {self.port}")
        print("[GyroPad] Menunggu koneksi dari HP... (buka app GyroPad & tekan Hubungkan)")

        while True:
            try:
                raw, addr = self.sock.recvfrom(2048)
            except socket.timeout:
                self._check_idle()
                continue

            try:
                data = json.loads(raw.decode("utf-8"))
            except (json.JSONDecodeError, UnicodeDecodeError):
                continue

            if self.last_packet_time == 0.0:
                print(f"[GyroPad] Terhubung dengan {addr[0]}:{addr[1]}")

            self.last_packet_time = time.time()
            self.apply_state(data)

    def _check_idle(self) -> None:
        if self.last_packet_time and (time.time() - self.last_packet_time) > self.timeout_s:
            print("[GyroPad] Tidak ada paket masuk, mereset controller ke posisi netral.")
            self.gamepad.reset()
            self.gamepad.update()
            self.last_packet_time = 0.0


def main() -> None:
    parser = argparse.ArgumentParser(description="GyroPad PC server")
    parser.add_argument("--port", type=int, default=25565, help="UDP port (default 25565)")
    parser.add_argument(
        "--gyro-scale",
        type=float,
        default=0.02,
        help="Skala pengaruh gyro ke stick kanan (default 0.02)",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=2.0,
        help="Detik idle sebelum controller direset ke netral (default 2.0)",
    )
    args = parser.parse_args()

    server = GyroPadServer(
        port=args.port,
        gyro_to_stick_scale=args.gyro_scale,
        timeout_s=args.timeout,
    )
    try:
        server.run()
    except KeyboardInterrupt:
        print("\n[GyroPad] Dihentikan oleh user.")


if __name__ == "__main__":
    main()
