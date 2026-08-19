package com.gyropad.app.net

import com.gyropad.app.model.ControllerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Kirim [ControllerState] ke server PC lewat UDP, di WiFi/hotspot yang sama.
 *
 * Kenapa UDP, bukan lewat ADB/TCP?
 * - Untuk input real-time (gamepad+gyro), telat sedikit lebih baik daripada
 *   nunggu retransmit paket yang basi (khas masalah TCP untuk kasus ini).
 *   ADB reverse-tethering juga bisa dipakai sebagai alternatif (lihat
 *   docs/SETUP_PC.md), tapi versi awal ini pakai WiFi langsung supaya simpel.
 *
 * Kirim di interval tetap (default ~120Hz) berdasarkan snapshot [state]
 * terakhir, BUKAN setiap kali ada event input - ini mencegah banjir paket
 * saat stick digoyang cepat sekaligus menjaga gyro tetap responsif.
 */
class UdpGamepadSender(
    private val state: ControllerState,
    private var host: String,
    private var port: Int,
    private val sendRateHz: Int = 120
) {
    private var job: Job? = null
    private var socket: DatagramSocket? = null

    var onError: ((Exception) -> Unit)? = null
    var onPacketSent: (() -> Unit)? = null

    fun updateTarget(newHost: String, newPort: Int) {
        host = newHost
        port = newPort
    }

    fun start(scope: CoroutineScope) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            try {
                socket = DatagramSocket()
                val address = InetAddress.getByName(host)
                val intervalMs = (1000.0 / sendRateHz).toLong().coerceAtLeast(1)

                while (true) {
                    val payload = synchronized(state) {
                        state.timestampMs = System.currentTimeMillis()
                        val json = state.toJson()
                        // Reset delta gyro akumulatif setelah dibaca, supaya
                        // tidak terhitung dobel di paket berikutnya.
                        state.gyroYaw = 0f
                        state.gyroPitch = 0f
                        json
                    }.toByteArray(Charsets.UTF_8)

                    val packet = DatagramPacket(payload, payload.size, address, port)
                    socket?.send(packet)
                    onPacketSent?.invoke()
                    delay(intervalMs)
                }
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        socket?.close()
        socket = null
    }
}
