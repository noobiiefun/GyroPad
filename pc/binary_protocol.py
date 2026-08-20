"""
Protokol biner GyroPad (sisi PC) - HARUS SINKRON PERSIS dengan
android/app/src/main/java/com/gyropad/app/net/BinaryProtocol.kt.

Kalau ubah layout di salah satu sisi, WAJIB ubah juga di sisi satunya -
tidak ada validasi versi/skema otomatis di v0.3 ini (lihat docs/PROTOCOL.md
bagian "Rencana optimasi" untuk ide penambahan version byte di masa depan).
"""

from __future__ import annotations

import struct


TYPE_STATE = 1
TYPE_RUMBLE = 2

# '>' = big-endian, harus sama dengan ByteOrder.BIG_ENDIAN di sisi Kotlin.
# B=uint8, f=float32, H=uint16, q=int64 (semua ukuran standar, tanpa padding
# karena pakai '>' bukan native alignment).
STATE_FORMAT = ">BffffffHffBq"
STATE_PACKET_SIZE = struct.calcsize(STATE_FORMAT)  # = 44

RUMBLE_FORMAT = ">Bff"
RUMBLE_PACKET_SIZE = struct.calcsize(RUMBLE_FORMAT)  # = 9

assert STATE_PACKET_SIZE == 44, "STATE_FORMAT berubah - cek juga BinaryProtocol.kt"
assert RUMBLE_PACKET_SIZE == 9, "RUMBLE_FORMAT berubah - cek juga BinaryProtocol.kt"


def decode_state(raw: bytes) -> dict | None:
    """
    Parse paket state (HP -> PC). Return None kalau paket tidak valid
    (ukuran salah atau type byte bukan TYPE_STATE) - dipanggil di titik
    terima paket, pemanggil cukup skip paket kalau hasilnya None.
    """
    if len(raw) != STATE_PACKET_SIZE:
        return None

    (
        packet_type,
        lx, ly, rx, ry,
        lt, rt,
        buttons,
        gyaw, gpitch,
        gactive,
        ts,
    ) = struct.unpack(STATE_FORMAT, raw)

    if packet_type != TYPE_STATE:
        return None

    return {
        "lx": lx, "ly": ly, "rx": rx, "ry": ry,
        "lt": lt, "rt": rt,
        "btn": buttons,
        "gyaw": gyaw, "gpitch": gpitch,
        "gactive": bool(gactive),
        "ts": ts,
    }


def encode_rumble(large: float, small: float) -> bytes:
    """Bungkus nilai rumble (0.0-1.0) jadi paket biner (PC -> HP)."""
    return struct.pack(RUMBLE_FORMAT, TYPE_RUMBLE, large, small)
