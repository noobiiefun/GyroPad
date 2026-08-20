package com.gyropad.app.profile

/**
 * Satu profil sensitivitas gyro, biasanya dinamai sesuai game (mis.
 * "Monster Hunter Rise", "Elden Ring"). [sensitivity] pakai satuan yang
 * sama persis dengan [com.gyropad.app.input.GyroManager.sensitivity]
 * (multiplier 0.2f..3.0f), jadi bisa langsung dipasang tanpa konversi.
 *
 * Sengaja disimpan simpel (cuma nama + satu angka) supaya gampang di-extend
 * nanti kalau mau nambah field lain per profil (mis. haptic intensity,
 * lihat roadmap #5 di docs/ROADMAP.md).
 */
data class SensitivityProfile(
    val name: String,
    val sensitivity: Float
)
