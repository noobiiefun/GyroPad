package com.gyropad.app.net

import com.gyropad.app.model.RumbleState
import kotlinx.coroutines.CoroutineScope

/**
 * Abstraksi jalur komunikasi HP <-> PC. GyroPad punya dua implementasi:
 * - [UdpTransport]: lewat WiFi, satu jaringan yang sama
 * - [TcpAdbTransport]: lewat kabel USB, ditunnel `adb reverse`
 *
 * Keduanya BIDIRECTIONAL: mengirim [com.gyropad.app.model.ControllerState]
 * ke PC, sekaligus menerima [RumbleState] balik dari PC (rumble dari game).
 */
interface GyroPadTransport {

    /** Dipanggil saat status koneksi berubah, buat ditampilkan di UI. */
    var onStatusChanged: ((String) -> Unit)?

    /** Dipanggil setiap paket state berhasil terkirim (buat hitung counter). */
    var onPacketSent: (() -> Unit)?

    /** Dipanggil setiap ada rumble baru diterima dari PC. */
    var onRumbleReceived: ((RumbleState) -> Unit)?

    /** Dipanggil kalau ada error jaringan. */
    var onError: ((Exception) -> Unit)?

    fun start(scope: CoroutineScope)
    fun stop()
}
