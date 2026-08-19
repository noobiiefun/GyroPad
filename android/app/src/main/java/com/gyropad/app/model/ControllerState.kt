package com.gyropad.app.model

import org.json.JSONObject

/**
 * Representasi satu "snapshot" state controller yang dikirim ke PC.
 *
 * Semua nilai stick/trigger dinormalisasi ke rentang -1f..1f (stick) atau
 * 0f..1f (trigger) supaya PC tidak perlu tahu detail range asli device.
 *
 * [buttons] adalah bitmask, urutan bit didefinisikan di GamepadButton.
 * [gyroYaw] dan [gyroPitch] adalah HASIL INTEGRASI (derajat, sudah dikali
 * sensitivitas), bukan raw rad/s dari sensor. Lihat GyroManager.
 */
data class ControllerState(
    var leftX: Float = 0f,
    var leftY: Float = 0f,
    var rightX: Float = 0f,
    var rightY: Float = 0f,
    var leftTrigger: Float = 0f,
    var rightTrigger: Float = 0f,
    var buttons: Int = 0,
    var gyroYaw: Float = 0f,
    var gyroPitch: Float = 0f,
    var gyroActive: Boolean = false,
    var timestampMs: Long = 0L
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("lx", leftX)
        o.put("ly", leftY)
        o.put("rx", rightX)
        o.put("ry", rightY)
        o.put("lt", leftTrigger)
        o.put("rt", rightTrigger)
        o.put("btn", buttons)
        o.put("gyaw", gyroYaw)
        o.put("gpitch", gyroPitch)
        o.put("gactive", gyroActive)
        o.put("ts", timestampMs)
        return o.toString()
    }
}

/**
 * Definisi bit untuk field [ControllerState.buttons].
 * Urutan ini HARUS sama persis dengan yang dibaca server.py (BUTTON_MAP).
 */
object GamepadButton {
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val X = 1 shl 2
    const val Y = 1 shl 3
    const val L1 = 1 shl 4
    const val R1 = 1 shl 5
    const val L3 = 1 shl 6
    const val R3 = 1 shl 7
    const val START = 1 shl 8
    const val SELECT = 1 shl 9
    const val DPAD_UP = 1 shl 10
    const val DPAD_DOWN = 1 shl 11
    const val DPAD_LEFT = 1 shl 12
    const val DPAD_RIGHT = 1 shl 13
}
