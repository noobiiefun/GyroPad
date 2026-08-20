package com.gyropad.app.net

import com.gyropad.app.model.ControllerState
import com.gyropad.app.model.RumbleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
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
 * Setelah `adb reverse` aktif, port di PC "muncul" sebagai localhost di sisi
 * HP - makanya host yang dipakai di sini SELALU "127.0.0.1", bukan IP WiFi.
 *
 * Format framing: newline-delimited JSON (satu baris = satu paket), supaya
 * gampang di-parse dari kedua sisi tanpa perlu length-prefix biner.
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
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))

                // Loop baca rumble berjalan di coroutine terpisah supaya tidak
                // memblokir loop kirim state.
                launch(Dispatchers.IO) {
                    try {
                        while (true) {
                            val line = reader.readLine() ?: break
                            parseRumble(line)?.let { onRumbleReceived?.invoke(it) }
                        }
                    } catch (e: Exception) {
                        // socket ditutup saat stop() -> wajar, tidak perlu dilaporkan
                    }
                }

                val intervalMs = (1000.0 / sendRateHz).toLong().coerceAtLeast(1)
                while (true) {
                    val payload = synchronized(state) {
                        state.timestampMs = System.currentTimeMillis()
                        val json = state.toJson()
                        state.gyroYaw = 0f
                        state.gyroPitch = 0f
                        json
                    }
                    output.write((payload + "\n").toByteArray(Charsets.UTF_8))
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

    private fun parseRumble(text: String): RumbleState? {
        return try {
            val o = JSONObject(text)
            if (o.optString("type") != "rumble") return null
            RumbleState(
                large = o.optDouble("large", 0.0).toFloat(),
                small = o.optDouble("small", 0.0).toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }
}
