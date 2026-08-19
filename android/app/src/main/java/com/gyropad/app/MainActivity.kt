package com.gyropad.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gyropad.app.input.GamepadInputManager
import com.gyropad.app.input.GyroManager
import com.gyropad.app.model.ControllerState
import com.gyropad.app.net.UdpGamepadSender

/**
 * Layar utama GyroPad.
 *
 * Alur data: GamepadInputManager & GyroManager sama-sama menulis ke satu
 * [ControllerState] yang sama -> UdpGamepadSender membaca snapshot state itu
 * secara berkala dan mengirimkannya ke server PC.
 *
 * Kontrol gyro: tekan & tahan tombol L1 di gamepad (atau tombol "GYRO"
 * di layar buat testing tanpa gamepad) untuk mengaktifkan gyro-aim -
 * mirip pola "hold to aim" yang lazim dipakai game Switch/DS4.
 */
class MainActivity : AppCompatActivity() {

    private val state = ControllerState()
    private lateinit var gamepadInput: GamepadInputManager
    private lateinit var gyroManager: GyroManager
    private lateinit var sender: UdpGamepadSender

    private lateinit var statusText: TextView
    private lateinit var packetCountText: TextView
    private var packetsSent = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gamepadInput = GamepadInputManager(state)
        gyroManager = GyroManager(this, state)

        val ipInput = findViewById<EditText>(R.id.editTextIp)
        val portInput = findViewById<EditText>(R.id.editTextPort)
        val connectButton = findViewById<Button>(R.id.buttonConnect)
        val gyroSwitch = findViewById<Switch>(R.id.switchGyro)
        val sensitivitySeek = findViewById<SeekBar>(R.id.seekBarSensitivity)
        val gyroHoldButton = findViewById<Button>(R.id.buttonGyroHold)
        statusText = findViewById(R.id.textStatus)
        packetCountText = findViewById(R.id.textPacketCount)

        sender = UdpGamepadSender(state, host = "192.168.1.100", port = 25565)
        sender.onError = { e ->
            runOnUiThread { statusText.text = "Error: ${e.message}" }
        }
        sender.onPacketSent = {
            packetsSent++
            if (packetsSent % 60 == 0) {
                runOnUiThread { packetCountText.text = "Paket terkirim: $packetsSent" }
            }
        }

        connectButton.setOnClickListener {
            val ip = ipInput.text.toString().ifBlank { "192.168.1.100" }
            val port = portInput.text.toString().toIntOrNull() ?: 25565
            sender.updateTarget(ip, port)
            sender.start(lifecycleScope)
            statusText.text = "Mengirim ke $ip:$port"
        }

        gyroSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            gyroManager.enabled = isChecked
            if (!gyroManager.isAvailable) {
                statusText.text = "Sensor gyroscope tidak tersedia di device ini"
            }
        }

        sensitivitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // progress 0..100 dipetakan ke sensitivitas 0.2x..3.0x
                gyroManager.sensitivity = 0.2f + (progress / 100f) * 2.8f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Tombol di layar buat simulasi "hold to aim" tanpa perlu pencet L1 fisik dulu.
        gyroHoldButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> gyroManager.setActive(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> gyroManager.setActive(false)
            }
            false
        }

        gyroManager.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        gyroManager.stop()
        sender.stop()
    }

    // Ditangkap di level Activity supaya event dari gamepad fisik (iPega 9076)
    // ketangkep sebelum diproses view manapun.
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepadInput.onGenericMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Pakai L1 fisik sebagai hold-to-aim gyro (ubah sesuai selera di sini).
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            gyroManager.setActive(event.action == KeyEvent.ACTION_DOWN)
        }
        if (gamepadInput.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
