"""
GyroPad Server
==============
Menerima paket BINER (lihat binary_protocol.py) dari aplikasi Android
GyroPad, mengemulasikan virtual Xbox 360 controller di Windows lewat
ViGEmBus (via library `vgamepad`), dan mengirim BALIK rumble/getaran dari
game ke HP - berguna kalau motor getar gamepad fisik kamu (mis. iPega
9076) tidak berfungsi.

Sejak v0.3, format paket adalah BINER, bukan JSON lagi - lebih kecil
(44 byte vs ~150-180 byte JSON untuk paket state) dan lebih cepat di-parse,
supaya latency tambahan dari serialisasi seminimal mungkin. Layout byte-nya
didokumentasikan di binary_protocol.py dan docs/PROTOCOL.md.

Dua mode transport:
- udp  : HP & PC di WiFi yang sama (default)
- tcp  : HP disambungkan lewat kabel USB, ditunnel `adb reverse` (lihat
         docs/SETUP_ADB.md). Dipakai kalau WiFi tidak stabil/tidak tersedia.

Requirement:
    pip install -r requirements.txt
    (Windows) install ViGEmBus driver: https://github.com/ViGEm/ViGEmBus/releases

Jalankan:
    python server.py --mode udp --port 25565
    python server.py --mode tcp --port 25565   # + jalankan adb reverse dulu
"""

from __future__ import annotations

import argparse
import socket
import sys
import threading
import time

from binary_protocol import (
    STATE_PACKET_SIZE,
    RUMBLE_PACKET_SIZE,
    decode_state,
    encode_rumble,
)
from notifier import notify_connected

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


class GamepadCore:
    """
    Logika inti yang dipakai bersama oleh mode UDP maupun TCP: terima dict
    state (hasil decode_state) dari HP, terapkan ke virtual controller, dan
    expose callback rumble. Supaya kode transport (UDP/TCP) tidak perlu tahu
    detail vgamepad.
    """

    def __init__(self, gyro_to_stick_scale: float, on_rumble):
        self.gyro_to_stick_scale = gyro_to_stick_scale
        self.on_rumble = on_rumble  # callback(large_float, small_float)

        self.gamepad = vg.VX360Gamepad()
        # register_notification dipanggil ViGEmBus setiap kali game
        # mengirim rumble ke controller ini (large/small motor bernilai 0-255).
        self.gamepad.register_notification(self._handle_rumble_notification)

    def _handle_rumble_notification(self, client, target, large_motor, small_motor, led_number, user_data):
        large = large_motor / 255.0
        small = small_motor / 255.0
        self.on_rumble(large, small)

    def apply_state(self, data: dict) -> None:
        lx = clamp(data["lx"], -1.0, 1.0)
        ly = clamp(data["ly"], -1.0, 1.0)
        rx = clamp(data["rx"], -1.0, 1.0)
        ry = clamp(data["ry"], -1.0, 1.0)
        lt = clamp(data["lt"], 0.0, 1.0)
        rt = clamp(data["rt"], 0.0, 1.0)
        buttons = data["btn"]
        gyro_active = data["gactive"]
        gyaw = data["gyaw"]
        gpitch = data["gpitch"]

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

    def reset(self) -> None:
        self.gamepad.reset()
        self.gamepad.update()


class UdpServer:
    """Mode WiFi: satu socket UDP dipakai buat terima state & kirim balik rumble."""

    def __init__(self, port: int, gyro_scale: float, timeout_s: float):
        self.port = port
        self.timeout_s = timeout_s
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind(("0.0.0.0", self.port))
        self.sock.settimeout(1.0)

        self.last_addr = None
        self.last_packet_time = 0.0
        self.addr_lock = threading.Lock()

        self.core = GamepadCore(gyro_scale, self._on_rumble)

    def _on_rumble(self, large: float, small: float) -> None:
        with self.addr_lock:
            addr = self.last_addr
        if addr is None:
            return
        try:
            self.sock.sendto(encode_rumble(large, small), addr)
        except OSError:
            pass

    def run(self) -> None:
        print(f"[GyroPad] Server (UDP/WiFi, protokol biner) jalan di port {self.port}")
        print("[GyroPad] Menunggu koneksi dari HP... (buka app GyroPad & tekan Hubungkan)")

        while True:
            try:
                raw, addr = self.sock.recvfrom(256)
            except socket.timeout:
                self._check_idle()
                continue

            data = decode_state(raw)
            if data is None:
                continue  # paket rusak/bukan state packet, abaikan

            with self.addr_lock:
                if self.last_addr != addr:
                    print(f"[GyroPad] Terhubung dengan {addr[0]}:{addr[1]}")
                    notify_connected(f"{addr[0]}:{addr[1]} (WiFi)")
                self.last_addr = addr
            self.last_packet_time = time.time()
            self.core.apply_state(data)

    def _check_idle(self) -> None:
        if self.last_packet_time and (time.time() - self.last_packet_time) > self.timeout_s:
            print("[GyroPad] Tidak ada paket masuk, mereset controller ke posisi netral.")
            self.core.reset()
            self.last_packet_time = 0.0
            with self.addr_lock:
                self.last_addr = None


class TcpServer:
    """
    Mode USB/ADB: HP menyambung ke 127.0.0.1:<port> di sisi HP, yang lewat
    `adb reverse tcp:<port> tcp:<port>` diteruskan ke port ini di PC.
    Satu koneksi TCP dipakai dua arah: baca paket biner state (44 byte
    tetap) dari HP, tulis paket biner rumble (9 byte tetap) balik ke HP
    saat game mengirim rumble.
    """

    def __init__(self, port: int, gyro_scale: float):
        self.port = port
        self.conn_lock = threading.Lock()
        self.conn = None
        self.core = GamepadCore(gyro_scale, self._on_rumble)

    def _on_rumble(self, large: float, small: float) -> None:
        with self.conn_lock:
            conn = self.conn
        if conn is None:
            return
        try:
            conn.sendall(encode_rumble(large, small))
        except OSError:
            pass

    def run(self) -> None:
        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_sock.bind(("127.0.0.1", self.port))
        server_sock.listen(1)
        print(f"[GyroPad] Server (TCP/USB, protokol biner) jalan di port {self.port}")
        print("[GyroPad] Pastikan sudah jalankan: adb reverse tcp:%d tcp:%d" % (self.port, self.port))
        print("[GyroPad] Menunggu koneksi dari HP...")

        while True:
            conn, _ = server_sock.accept()
            print("[GyroPad] HP terhubung lewat USB.")
            notify_connected("USB (ADB)")
            with self.conn_lock:
                self.conn = conn
            self._handle_connection(conn)
            with self.conn_lock:
                self.conn = None
            self.core.reset()
            print("[GyroPad] Koneksi USB terputus, menunggu koneksi baru...")

    def _handle_connection(self, conn: socket.socket) -> None:
        conn.settimeout(5.0)
        while True:
            raw = self._recv_exactly(conn, STATE_PACKET_SIZE)
            if raw is None:
                break
            data = decode_state(raw)
            if data is None:
                continue
            self.core.apply_state(data)

    @staticmethod
    def _recv_exactly(conn: socket.socket, size: int) -> bytes | None:
        """
        Baca stream sampai persis [size] byte terkumpul - satu `recv()` di
        TCP tidak dijamin mengembalikan semua byte yang diminta sekaligus.
        Return None kalau koneksi terputus sebelum data lengkap terbaca.
        """
        buffer = b""
        while len(buffer) < size:
            try:
                chunk = conn.recv(size - len(buffer))
            except socket.timeout:
                continue
            except OSError:
                return None
            if not chunk:
                return None
            buffer += chunk
        return buffer


def main() -> None:
    parser = argparse.ArgumentParser(description="GyroPad PC server")
    parser.add_argument("--mode", choices=["udp", "tcp"], default="udp",
                         help="udp = WiFi (default), tcp = USB lewat adb reverse")
    parser.add_argument("--port", type=int, default=25565, help="Port (default 25565)")
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
        help="[mode udp] Detik idle sebelum controller direset ke netral (default 2.0)",
    )
    args = parser.parse_args()

    try:
        if args.mode == "udp":
            server = UdpServer(port=args.port, gyro_scale=args.gyro_scale, timeout_s=args.timeout)
        else:
            server = TcpServer(port=args.port, gyro_scale=args.gyro_scale)
        server.run()
    except KeyboardInterrupt:
        print("\n[GyroPad] Dihentikan oleh user.")


if __name__ == "__main__":
    main()
