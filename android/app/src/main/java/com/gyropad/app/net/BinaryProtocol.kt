package com.gyropad.app.net

import com.gyropad.app.model.ControllerState
import com.gyropad.app.model.RumbleState
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Format paket biner GyroPad (menggantikan JSON sejak v0.3).
 *
 * Kenapa diganti dari JSON? Paket state dikirim ~60-120x/detik - JSON teks
 * (~150-180 byte/paket dengan semua tanda kutip, kurung kurawal, nama field)
 * jauh lebih besar dan lebih lambat di-parse dibanding biner (44 byte tetap,
 * tinggal baca offset). Untuk kasus real-time seperti gyro-aim, setiap
 * milidetik parsing & ukuran paket ikut menyumbang ke latency yang terasa.
 *
 * Byte order: BIG_ENDIAN di kedua sisi (Android & Python pakai '>' saat
 * struct.pack/unpack) - HARUS SAMA, kalau salah satu beda maka semua angka
 * float/int akan terbaca kacau tanpa error yang jelas.
 *
 * Layout paket STATE (HP -> PC), total 44 byte:
 * | offset | ukuran | field    | tipe   |
 * |--------|--------|----------|--------|
 * | 0      | 1      | type     | byte (selalu TYPE_STATE) |
 * | 1      | 4      | lx       | float32 |
 * | 5      | 4      | ly       | float32 |
 * | 9      | 4      | rx       | float32 |
 * | 13     | 4      | ry       | float32 |
 * | 17     | 4      | lt       | float32 |
 * | 21     | 4      | rt       | float32 |
 * | 25     | 2      | buttons  | uint16 (bitmask, lihat GamepadButton) |
 * | 27     | 4      | gyaw     | float32 |
 * | 31     | 4      | gpitch   | float32 |
 * | 35     | 1      | gactive  | byte (0/1) |
 * | 36     | 8      | ts       | int64 (epoch ms) |
 *
 * Layout paket RUMBLE (PC -> HP), total 9 byte:
 * | offset | ukuran | field  | tipe    |
 * |--------|--------|--------|---------|
 * | 0      | 1      | type   | byte (selalu TYPE_RUMBLE) |
 * | 1      | 4      | large  | float32 |
 * | 5      | 4      | small  | float32 |
 *
 * Lihat docs/PROTOCOL.md untuk versi dokumentasi lengkapnya, dan
 * pc/server.py (fungsi decode_state/encode_rumble) untuk sisi PC - kalau
 * mengubah layout di sini, WAJIB diubah juga di sana.
 */
object BinaryProtocol {

    const val TYPE_STATE: Byte = 1
    const val TYPE_RUMBLE: Byte = 2

    const val STATE_PACKET_SIZE = 44
    const val RUMBLE_PACKET_SIZE = 9

    fun encodeState(state: ControllerState): ByteArray {
        val buf = ByteBuffer.allocate(STATE_PACKET_SIZE).order(ByteOrder.BIG_ENDIAN)
        buf.put(TYPE_STATE)
        buf.putFloat(state.leftX)
        buf.putFloat(state.leftY)
        buf.putFloat(state.rightX)
        buf.putFloat(state.rightY)
        buf.putFloat(state.leftTrigger)
        buf.putFloat(state.rightTrigger)
        // Bitmask tombol maksimum saat ini 14 bit (< 32768), jadi aman
        // disimpan sebagai Short - bit pattern-nya identik dibaca sebagai
        // unsigned 16-bit di sisi Python ('>H').
        buf.putShort(state.buttons.toShort())
        buf.putFloat(state.gyroYaw)
        buf.putFloat(state.gyroPitch)
        buf.put(if (state.gyroActive) 1 else 0)
        buf.putLong(state.timestampMs)
        return buf.array()
    }

    /**
     * Parse paket rumble dari byte array. [length] dipakai supaya bisa
     * langsung memakai buffer hasil DatagramPacket tanpa perlu copy dulu
     * (DatagramPacket.getLength() bisa lebih kecil dari buffer.size).
     */
    fun decodeRumble(bytes: ByteArray, length: Int): RumbleState? {
        if (length < RUMBLE_PACKET_SIZE) return null
        val buf = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.BIG_ENDIAN)
        val type = buf.get()
        if (type != TYPE_RUMBLE) return null
        val large = buf.float
        val small = buf.float
        return RumbleState(large = large, small = small)
    }
}
