package com.gyropad.app.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.gyropad.app.model.ControllerState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
 * (mis. lewat tombol L1 sebagai "gyro hold-to-aim", umum dipakai HBG/LBG
 * di Monster Hunter untuk aiming presisi).
 *
 * Sejak v0.4, ada tiga lapis koreksi supaya gyro tidak "ngambang"/drift
 * walau HP dipegang diam sempurna - lihat masing-masing bagian di bawah:
 * kalibrasi bias awal, auto-koreksi drift berkelanjutan, dan deadzone noise.
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

    // --- 1. Kalibrasi bias awal ---
    // Setiap chip gyroscope punya sedikit offset/bias bawaan (nilai yang
    // dibaca sensor walau HP benar-benar diam) - kalau tidak dikoreksi,
    // ini terasa sebagai "drift" pelan yang bikin crosshair/aim geser
    // sendiri walau tangan tidak bergerak. Kalibrasi mengukur bias ini
    // dengan merata-ratakan pembacaan sensor selama HP diam sesaat.
    private var isCalibrating = false
    private var calibrationStartNs = 0L
    private val calibrationDurationNs = (1.5 * 1_000_000_000L).toLong()
    private val calibrationDurationSeconds = 1.5f
    private var calibrationSampleCount = 0
    private var calibrationSumYaw = 0.0
    private var calibrationSumPitch = 0.0

    /** Bias hasil kalibrasi (rad/s), dikurangkan dari tiap pembacaan sensor. */
    private var biasYaw = 0f
    private var biasPitch = 0f

    /** Dipanggil saat status kalibrasi berubah, buat ditampilkan di UI. */
    var onCalibrationStatusChanged: ((String) -> Unit)? = null

    // --- 2. Auto-koreksi drift berkelanjutan ---
    // Bias gyro bisa sedikit "melayang" seiring waktu (mis. karena suhu
    // chip berubah selama dipakai lama). Daripada user harus sadar drift
    // muncul lalu manual re-kalibrasi, GyroManager terus memantau: kalau
    // HP diam (magnitude di bawah stillnessThreshold) selama beberapa saat
    // BERTURUT-TURUT dan gyro-aim SEDANG TIDAK dipakai (state.gyroActive
    // false - supaya tidak salah mengoreksi saat user sengaja menahan
    // bidikan diam-diam), sisa bacaan sensor dianggap sebagai bias yang
    // belum terkoreksi, dan pelan-pelan (alpha kecil) diserap ke bias.
    private var stillnessTimerSeconds = 0f
    private val stillnessThresholdRadPerSec = 0.03f // ~1.7 derajat/detik
    private val stillnessRequiredSeconds = 0.6f
    private val driftCorrectionAlpha = 0.02f // makin kecil = makin pelan/aman

    // --- 3. Deadzone noise ---
    // Getaran tangan alami/noise sensor yang sangat kecil (di bawah ambang
    // ini) diabaikan sepenuhnya, tidak dikonversi jadi delta gerakan sama
    // sekali - mencegah crosshair "gemetar" halus walau HP dipegang mantap.
    private val noiseDeadzoneRadPerSec = 0.015f

    // --- Visual offset untuk CrosshairView (TIDAK dikirim ke jaringan) ---
    // Berbeda dari state.gyroYaw/gyroPitch yang direset tiap paket terkirim,
    // ini akumulasi yang bertahan & diklem ke rentang -1f..1f, dipakai murni
    // buat ditampilkan sebagai posisi crosshair di dalam app (alat kalibrasi).
    private var visualYaw = 0f
    private var visualPitch = 0f
    private val visualRange = 25f // derajat -> dianggap "mentok" crosshair
    private val visualDecayPerSecond = 2.2f // kecepatan crosshair balik ke tengah saat gyro nonaktif

    /** Dipanggil tiap frame sensor (aktif maupun tidak) dengan posisi -1f..1f. */
    var onVisualOffsetChanged: ((x: Float, y: Float) -> Unit)? = null

    fun start() {
        if (!isAvailable) return
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        startCalibration()
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        lastTimestampNs = 0L
    }

    /**
     * Mulai (ulang) kalibrasi bias - panggil ini kalau user menekan tombol
     * "Kalibrasi Ulang", atau otomatis sekali saat [start] dipanggil.
     * User perlu meletakkan HP diam selama [calibrationDurationSeconds] detik.
     */
    fun startCalibration() {
        if (!isAvailable) return
        isCalibrating = true
        calibrationStartNs = 0L
        calibrationSampleCount = 0
        calibrationSumYaw = 0.0
        calibrationSumPitch = 0.0
        stillnessTimerSeconds = 0f
        onCalibrationStatusChanged?.invoke(
            "Mengkalibrasi... letakkan HP diam (${calibrationDurationSeconds}s)"
        )
    }

    /** Panggil dari tombol hold-to-aim (mis. saat L1 ditekan). */
    fun setActive(active: Boolean) {
        state.gyroActive = active && enabled
        if (!active) lastTimestampNs = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        if (lastTimestampNs == 0L) {
            lastTimestampNs = event.timestamp
            return
        }
        val dtSeconds = (event.timestamp - lastTimestampNs) / 1_000_000_000f
        lastTimestampNs = event.timestamp
        if (dtSeconds <= 0f || dtSeconds > 0.5f) return // lewati lompatan waktu aneh

        if (isCalibrating) {
            accumulateCalibrationSample(event)
            return
        }

        // event.values dalam rad/s. Pegang HP tegak (portrait) menghadap layar:
        // values[1] = rotasi sumbu Y (mendongak/menunduk -> pitch)
        // values[2] = rotasi sumbu Z (menoleh kiri/kanan -> yaw)
        // Bias hasil kalibrasi dikurangkan di sini, SEBELUM dipakai untuk
        // apapun (baik delta gerakan maupun deteksi diam).
        val yawRateRad = event.values[2] - biasYaw
        val pitchRateRad = event.values[1] - biasPitch

        updateStillnessTracking(yawRateRad, pitchRateRad, dtSeconds)

        if (!enabled || !state.gyroActive) {
            // Gyro nonaktif: hanya luruhkan crosshair kembali ke tengah, tidak
            // mengubah state jaringan sama sekali.
            decayVisualOffset(dtSeconds)
            return
        }

        val magnitude = sqrt(yawRateRad * yawRateRad + pitchRateRad * pitchRateRad)
        if (magnitude < noiseDeadzoneRadPerSec) return // di bawah noise floor, abaikan total

        val yawRateDeg = Math.toDegrees(yawRateRad.toDouble()).toFloat()
        val pitchRateDeg = Math.toDegrees(pitchRateRad.toDouble()).toFloat()

        var deltaYaw = yawRateDeg * dtSeconds * sensitivity
        var deltaPitch = pitchRateDeg * dtSeconds * sensitivity

        deltaYaw = deltaYaw.coerceIn(-maxDeltaPerEvent, maxDeltaPerEvent)
        deltaPitch = deltaPitch.coerceIn(-maxDeltaPerEvent, maxDeltaPerEvent)

        // Dikirim sebagai delta akumulatif per-frame kirim; direset oleh
        // transport (UdpTransport/TcpAdbTransport) setelah tiap paket terkirim.
        state.gyroYaw += deltaYaw
        state.gyroPitch += deltaPitch

        // Akumulasi terpisah buat crosshair - tidak pernah direset dari luar,
        // cuma diklem ke rentang visualRange lalu dinormalisasi ke -1f..1f.
        visualYaw = (visualYaw + deltaYaw).coerceIn(-visualRange, visualRange)
        visualPitch = (visualPitch + deltaPitch).coerceIn(-visualRange, visualRange)
        onVisualOffsetChanged?.invoke(visualYaw / visualRange, -visualPitch / visualRange)
    }

    private fun accumulateCalibrationSample(event: SensorEvent) {
        if (calibrationStartNs == 0L) calibrationStartNs = event.timestamp
        calibrationSumYaw += event.values[2]
        calibrationSumPitch += event.values[1]
        calibrationSampleCount++

        if (event.timestamp - calibrationStartNs >= calibrationDurationNs) {
            finishCalibration()
        }
    }

    private fun finishCalibration() {
        if (calibrationSampleCount > 0) {
            biasYaw = (calibrationSumYaw / calibrationSampleCount).toFloat()
            biasPitch = (calibrationSumPitch / calibrationSampleCount).toFloat()
        }
        isCalibrating = false
        stillnessTimerSeconds = 0f
        lastTimestampNs = 0L // mulai ulang tracking dt bersih setelah kalibrasi
        onCalibrationStatusChanged?.invoke("Kalibrasi selesai, siap dipakai")
    }

    /**
     * Pantau apakah HP sedang diam. Kalau ya, cukup lama, DAN gyro-aim
     * sedang tidak dipakai (supaya tidak salah koreksi saat user sengaja
     * menahan bidikan diam), pelan-pelan serap sisa bacaan ke [biasYaw]/
     * [biasPitch] - ini yang membuat drift lambat tidak menumpuk selama
     * main lama tanpa perlu user sadar & kalibrasi ulang manual.
     */
    private fun updateStillnessTracking(yawRateRad: Float, pitchRateRad: Float, dtSeconds: Float) {
        val magnitude = sqrt(yawRateRad * yawRateRad + pitchRateRad * pitchRateRad)
        if (magnitude < stillnessThresholdRadPerSec) {
            stillnessTimerSeconds += dtSeconds
            if (stillnessTimerSeconds >= stillnessRequiredSeconds && !state.gyroActive) {
                biasYaw += driftCorrectionAlpha * yawRateRad
                biasPitch += driftCorrectionAlpha * pitchRateRad
            }
        } else {
            stillnessTimerSeconds = 0f
        }
    }

    private fun decayVisualOffset(dtSeconds: Float) {
        if (visualYaw == 0f && visualPitch == 0f) return
        val decay = (visualDecayPerSecond * dtSeconds * visualRange)
        visualYaw = moveTowardZero(visualYaw, decay)
        visualPitch = moveTowardZero(visualPitch, decay)
        onVisualOffsetChanged?.invoke(visualYaw / visualRange, -visualPitch / visualRange)
    }

    private fun moveTowardZero(value: Float, step: Float): Float {
        if (value > 0f) return max(0f, value - step)
        if (value < 0f) return min(0f, value + step)
        return 0f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // tidak dipakai
    }
}
