package com.gyropad.app.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.gyropad.app.model.ControllerState
import com.gyropad.app.model.GamepadButton
import kotlin.math.abs

/**
 * Menangkap input dari gamepad Bluetooth fisik (diuji dengan iPega 9076).
 *
 * iPega 9076 terdeteksi Android sebagai standard HID gamepad, jadi dia lewat
 * jalur generic MotionEvent (stick + trigger analog) dan KeyEvent (tombol).
 * Ini BUKAN dibaca lewat Bluetooth API langsung - Android sudah menangani
 * pairing HID-nya, kita tinggal "dengarkan" event input standar seperti
 * halnya keyboard/mouse.
 *
 * Cara pakai: panggil [onGenericMotionEvent] dan [onKeyEvent] dari
 * Activity.dispatchGenericMotionEvent / dispatchKeyEvent, supaya event
 * gamepad ketangkep sebelum diproses view lain.
 *
 * Nilai axis disimpan langsung ke [state] yang di-share dengan UdpGamepadSender,
 * jadi tidak ada alokasi object baru tiap event (penting buat menjaga latency rendah).
 */
class GamepadInputManager(private val state: ControllerState) {

    // Sedikit deadzone supaya stick yang "netral tapi tidak pas 0.0" iPega
    // gak bikin virtual controller di PC drift sendiri.
    private val deadzone = 0.08f

    private fun applyDeadzone(v: Float): Float =
        if (abs(v) < deadzone) 0f else v

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) {
            return false
        }

        // Left stick
        state.leftX = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_X))
        state.leftY = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Y))

        // Right stick - kebanyakan gamepad generic (termasuk iPega) pakai Z/RZ.
        // Sebagian firmware lama pakai RX/RY, makanya kita ambil yang magnitude-nya lebih besar.
        val rxZ = event.getAxisValue(MotionEvent.AXIS_Z)
        val ryZ = event.getAxisValue(MotionEvent.AXIS_RZ)
        val rxAlt = event.getAxisValue(MotionEvent.AXIS_RX)
        val ryAlt = event.getAxisValue(MotionEvent.AXIS_RY)
        state.rightX = applyDeadzone(if (abs(rxZ) >= abs(rxAlt)) rxZ else rxAlt)
        state.rightY = applyDeadzone(if (abs(ryZ) >= abs(ryAlt)) ryZ else ryAlt)

        // Trigger analog - beberapa device pakai LTRIGGER/RTRIGGER, sebagian pakai BRAKE/GAS.
        val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
        val brake = event.getAxisValue(MotionEvent.AXIS_BRAKE)
        val gas = event.getAxisValue(MotionEvent.AXIS_GAS)
        state.leftTrigger = if (lt > 0f) lt else brake
        state.rightTrigger = if (rt > 0f) rt else gas

        // D-Pad pada banyak gamepad HID dikirim sebagai HAT_X/HAT_Y, bukan KeyEvent.
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        setButton(GamepadButton.DPAD_LEFT, hatX < -0.5f)
        setButton(GamepadButton.DPAD_RIGHT, hatX > 0.5f)
        setButton(GamepadButton.DPAD_UP, hatY < -0.5f)
        setButton(GamepadButton.DPAD_DOWN, hatY > 0.5f)

        return true
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val handled = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> { setButton(GamepadButton.A, down); true }
            KeyEvent.KEYCODE_BUTTON_B -> { setButton(GamepadButton.B, down); true }
            KeyEvent.KEYCODE_BUTTON_X -> { setButton(GamepadButton.X, down); true }
            KeyEvent.KEYCODE_BUTTON_Y -> { setButton(GamepadButton.Y, down); true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { setButton(GamepadButton.L1, down); true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { setButton(GamepadButton.R1, down); true }
            KeyEvent.KEYCODE_BUTTON_THUMBL -> { setButton(GamepadButton.L3, down); true }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> { setButton(GamepadButton.R3, down); true }
            KeyEvent.KEYCODE_BUTTON_START -> { setButton(GamepadButton.START, down); true }
            KeyEvent.KEYCODE_BUTTON_SELECT -> { setButton(GamepadButton.SELECT, down); true }
            KeyEvent.KEYCODE_DPAD_UP -> { setButton(GamepadButton.DPAD_UP, down); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { setButton(GamepadButton.DPAD_DOWN, down); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { setButton(GamepadButton.DPAD_LEFT, down); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { setButton(GamepadButton.DPAD_RIGHT, down); true }
            else -> false
        }
        return handled
    }

    private fun setButton(mask: Int, pressed: Boolean) {
        state.buttons = if (pressed) state.buttons or mask else state.buttons and mask.inv()
    }
}
