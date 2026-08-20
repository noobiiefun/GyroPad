package com.gyropad.app.net

import com.gyropad.app.model.ControllerState
import com.gyropad.app.model.RumbleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * Transport lewat kabel USB, ditunnel pakai `adb reverse tcp:<port> tcp:<port>`.
 *
 * Kenapa TCP, bukan UDP seperti mode WiFi? Karena `adb reverse`/`adb forward`
 * cuma bisa nge-tunnel koneksi TCP, bukan UDP - ini batasan ADB itu sendiri,
 * bukan pilihan desain GyroPad. Lihat docs/SETUP_ADB.md untuk command
 * lengkapnya.
 *
 * Sejak v0.3 memakai format BINER (lihat [BinaryProtocol]). Karena TCP itu
 * stream byte tanpa batas pesan otomatis (beda dari UDP yang per-datagram),
 * framing di sini memanfaatkan bahwa UKURAN tiap jenis paket TETAP dan
 * ARAHNYA TETAP: HP -> PC selalu 44 byte (state), PC -> HP selalu 9 byte
 * (rumble). Jadi tidak perlu length-prefix tambahan, cukup baca persis
 * [BinaryProtocol.RUMBLE_PACKET_SIZE] byte tiap kali menunggu rumble.
 */
class TcpAdbTransport(
    private val state: ControllerState,
    private val port: Int,
    private val sendRateHz: Int = 60
) : GyroPadTransport {

    override var onStatusChanged: ((String) -> Unit)? = null
    override var onPacketSent: (() -> Unit)? = null
    override var onRumbleReceived: ((RumbleState) -> Unit)? = null
    override var onError: ((Exception) -> Unit)? = null

    private var job: Job? = null
    private var socket: Socket? = null

    override fun start(scope: CoroutineScope) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            try {
                onStatusChanged?.invoke("Menyambung ke PC lewat USB (127.0.0.1:$port)...")
                val s = Socket("127.0.0.1", port)
                socket = s
                onStatusChanged?.invoke("Terhubung lewat USB (127.0.0.1:$port)")

                val output: OutputStream = s.getOutputStream()
                val input: InputStream = s.getInputStream()

                // Loop baca rumble berjalan di coroutine terpisah supaya tidak
                // memblokir loop kirim state.
                launch(Dispatchers.IO) {
                    try {
                        val rumbleBuffer = ByteArray(BinaryProtocol.RUMBLE_PACKET_SIZE)
                        while (true) {
                            readExactly(input, rumbleBuffer) ?: break
                            BinaryProtocol.decodeRumble(rumbleBuffer, rumbleBuffer.size)?.let {
                                onRumbleReceived?.invoke(it)
                            }
                        }
                    } catch (e: Exception) {
                        // socket ditutup saat stop() -> wajar, tidak perlu dilaporkan
                    }
                }

                val intervalMs = (1000.0 / sendRateHz).toLong().coerceAtLeast(1)
                while (true) {
                    val payload = synchronized(state) {
                        state.timestampMs = System.currentTimeMillis()
                        val bytes = BinaryProtocol.encodeState(state)
                        state.gyroYaw = 0f
                        state.gyroPitch = 0f
                        bytes
                    }
                    output.write(payload)
                    output.flush()
                    onPacketSent?.invoke()
                    delay(intervalMs)
                }
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        try {
            socket?.close()
        } catch (e: Exception) {
            // diabaikan, socket mungkin sudah tertutup
        }
        socket = null
    }

    /**
     * Baca stream sampai [buffer] terisi penuh - satu `InputStream.read()`
     * di TCP tidak dijamin mengembalikan semua byte yang diminta sekaligus,
     * jadi perlu loop sampai benar-benar lengkap. Return null kalau stream
     * berakhir (koneksi ditutup) sebelum data lengkap terbaca.
     */
    private fun readExactly(input: InputStream, buffer: ByteArray): ByteArray? {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) return null
            offset += read
        }
        return buffer
    }
}
