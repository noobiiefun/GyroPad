package com.gyropad.app.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.gyropad.app.model.ControllerState
import kotlin.math.max
import kotlin.math.min

/**
 * Baca sensor TYPE_GYROSCOPE (angular velocity, rad/s) dan integrasikan
 * jadi delta sudut (derajat) yang ditambahkan ke state setiap frame sensor.
 *
 * Kenapa integrasi manual, bukan TYPE_ROTATION_VECTOR?
 * - Untuk "gyro aim" ala controller (Switch/DS4), yang dipakai memang laju
 *   putaran (angular velocity), lalu diskalakan jadi kecepatan gerak kamera -
 *   mirip cara kerja mouse relative, bukan orientasi absolut. Ini menghindari
 *   masalah drift orientasi & gimbal lock dari sensor fusion absolut.
 *
 * [sensitivity] dan toggle aktif/nonaktif dikontrol dari MainActivity
 * (mis. lewat tombol L2 sebagai "gyro hold-to-aim", umum dipakai HBG/LBG
 * di Monster Hunter untuk aiming presisi).
 */
class GyroManager(
    context: Context,
    private val state: ControllerState
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    var sensitivity: Float = 1.0f
    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) state.gyroActive = false
            lastTimestampNs = 0L
        }

    private var lastTimestampNs = 0L

    /** Batas maksimum output per frame, biar tetap smooth walau delay/lag. */
    private val maxDeltaPerEvent = 8.0f

    val isAvailable: Boolean get() = gyroSensor != null

    fun start() {
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        lastTimestampNs = 0L
    }

    /** Panggil dari tombol hold-to-aim (mis. saat L2/R2 ditekan). */
    fun setActive(active: Boolean) {
        state.gyroActive = active && enabled
        if (!active) lastTimestampNs = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!enabled || !state.gyroActive) return
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        if (lastTimestampNs == 0L) {
            lastTimestampNs = event.timestamp
            return
        }
        val dtSeconds = (event.timestamp - lastTimestampNs) / 1_000_000_000f
        lastTimestampNs = event.timestamp
        if (dtSeconds <= 0f || dtSeconds > 0.5f) return // lewati lompatan waktu aneh

        // event.values dalam rad/s. Pegang HP tegak (portrait) menghadap layar:
        // values[1] = rotasi sumbu Y (mendongak/menunduk -> pitch)
        // values[2] = rotasi sumbu Z (menoleh kiri/kanan -> yaw)
        val pitchRateDeg = Math.toDegrees(event.values[1].toDouble()).toFloat()
        val yawRateDeg = Math.toDegrees(event.values[2].toDouble()).toFloat()

        var deltaYaw = yawRateDeg * dtSeconds * sensitivity
        var deltaPitch = pitchRateDeg * dtSeconds * sensitivity

        deltaYaw = deltaYaw.coerceIn(-maxDeltaPerEvent, maxDeltaPerEvent)
        deltaPitch = deltaPitch.coerceIn(-maxDeltaPerEvent, maxDeltaPerEvent)

        // Dikirim sebagai delta akumulatif per-frame kirim; direset oleh
        // UdpGamepadSender setelah tiap paket terkirim (lihat consumeGyroDelta()).
        state.gyroYaw += deltaYaw
        state.gyroPitch += deltaPitch
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // tidak dipakai
    }
}
