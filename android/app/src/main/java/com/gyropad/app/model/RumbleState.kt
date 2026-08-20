package com.gyropad.app.model

/**
 * Nilai rumble/getar terakhir yang diterima dari game lewat PC.
 * large = motor rumble berat/low-frequency, small = motor rumble ringan/high-frequency
 * (sama seperti dua motor di controller Xbox/DualShock asli).
 * Rentang 0f..1f, sudah dinormalisasi dari byte 0..255 di sisi server.
 */
data class RumbleState(
    val large: Float = 0f,
    val small: Float = 0f
) {
    val isActive: Boolean get() = large > 0.01f || small > 0.01f

    /** Gabungan sederhana dua motor jadi satu intensitas, buat HP yang cuma punya 1 motor getar. */
    fun combinedIntensity(): Float = large.coerceIn(0f, 1f).coerceAtLeast(small.coerceIn(0f, 1f))
}
