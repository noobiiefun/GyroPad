package com.gyropad.app.net

import com.gyropad.app.model.ControllerState
import com.gyropad.app.model.RumbleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Transport lewat WiFi (UDP), untuk kasus HP & PC di jaringan yang sama.
 *
 * Sejak v0.3 memakai format BINER (lihat [BinaryProtocol]), bukan JSON lagi -
 * paket state jadi 44 byte tetap (vs ~150-180 byte JSON), dan parsing di
 * kedua sisi tinggal baca offset tanpa perlu parser teks.
 *
 * Satu [DatagramSocket] dipakai untuk DUA arah:
 * - loop kirim: mengirim snapshot [ControllerState] ke PC secara berkala
 * - loop terima: mendengarkan balasan rumble dari PC secara terus-menerus
 *
 * Ini bisa dalam satu socket yang sama karena PC membalas ke alamat asal
 * paket yang diterimanya (lihat server.py) - jadi tidak perlu port terpisah.
 */
class UdpTransport(
    private val state: ControllerState,
    private var host: String,
    private var port: Int,
    private val sendRateHz: Int = 120
) : GyroPadTransport {

    override var onStatusChanged: ((String) -> Unit)? = null
    override var onPacketSent: (() -> Unit)? = null
    override var onRumbleReceived: ((RumbleState) -> Unit)? = null
    override var onError: ((Exception) -> Unit)? = null

    private var sendJob: Job? = null
    private var receiveJob: Job? = null
    private var socket: DatagramSocket? = null

    fun updateTarget(newHost: String, newPort: Int) {
        host = newHost
        port = newPort
    }

    override fun start(scope: CoroutineScope) {
        stop()
        try {
            socket = DatagramSocket()
        } catch (e: Exception) {
            onError?.invoke(e)
            return
        }

        sendJob = scope.launch(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName(host)
                val intervalMs = (1000.0 / sendRateHz).toLong().coerceAtLeast(1)
                onStatusChanged?.invoke("Mengirim ke $host:$port (WiFi)")

                while (true) {
                    val payload = synchronized(state) {
                        state.timestampMs = System.currentTimeMillis()
                        val bytes = BinaryProtocol.encodeState(state)
                        state.gyroYaw = 0f
                        state.gyroPitch = 0f
                        bytes
                    }

                    val packet = DatagramPacket(payload, payload.size, address, port)
                    socket?.send(packet)
                    onPacketSent?.invoke()
                    delay(intervalMs)
                }
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }

        receiveJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(64)
            try {
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet) ?: break
                    BinaryProtocol.decodeRumble(packet.data, packet.length)?.let {
                        onRumbleReceived?.invoke(it)
                    }
                }
            } catch (e: Exception) {
                // Socket ditutup saat stop() -> exception ini normal, jangan dilaporkan sebagai error.
            }
        }
    }

    override fun stop() {
        sendJob?.cancel()
        receiveJob?.cancel()
        sendJob = null
        receiveJob = null
        socket?.close()
        socket = null
    }
}
