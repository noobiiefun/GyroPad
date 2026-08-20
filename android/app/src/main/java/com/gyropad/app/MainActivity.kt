package com.gyropad.app

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gyropad.app.input.GamepadInputManager
import com.gyropad.app.input.GyroManager
import com.gyropad.app.model.ControllerState
import com.gyropad.app.model.RumbleState
import com.gyropad.app.net.GyroPadTransport
import com.gyropad.app.net.TcpAdbTransport
import com.gyropad.app.net.UdpTransport
import com.gyropad.app.ui.CrosshairView

/**
 * Layar utama GyroPad.
 *
 * Alur data:
 *  - GamepadInputManager & GyroManager sama-sama menulis ke satu
 *    [ControllerState] yang sama -> [transport] membaca snapshot state itu
 *    secara berkala dan mengirimkannya ke server PC (lewat WiFi atau USB).
 *  - Arah sebaliknya: server bisa mengirim balik [RumbleState] (rumble dari
 *    game) -> di sini dipetakan ke getaran HP lewat Vibrator API, sebagai
 *    pengganti motor getar iPega yang tidak berfungsi.
 *  - GyroManager juga melaporkan offset visual (terpisah dari data yang
 *    dikirim ke jaringan) ke [CrosshairView], sebagai alat kalibrasi gyro
 *    di dalam app (bukan overlay di atas game).
 */
class MainActivity : AppCompatActivity() {

    private val state = ControllerState()
    private lateinit var gamepadInput: GamepadInputManager
    private lateinit var gyroManager: GyroManager
    private var transport: GyroPadTransport? = null
    private var vibrator: Vibrator? = null

    private lateinit var statusText: TextView
    private lateinit var packetCountText: TextView
    private lateinit var rumbleStatusText: TextView
    private lateinit var crosshairView: CrosshairView
    private lateinit var ipInput: EditText
    private lateinit var portInput: EditText
    private lateinit var labelIp: TextView
    private lateinit var usbHint: TextView
    private lateinit var radioGroupMode: RadioGroup

    private var packetsSent = 0
    private var usbModeSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gamepadInput = GamepadInputManager(state)
        gyroManager = GyroManager(this, state)
        vibrator = getVibrator()

        ipInput = findViewById(R.id.editTextIp)
        portInput = findViewById(R.id.editTextPort)
        labelIp = findViewById(R.id.labelIp)
        usbHint = findViewById(R.id.textUsbHint)
        radioGroupMode = findViewById(R.id.radioGroupMode)
        val connectButton = findViewById<Button>(R.id.buttonConnect)
        val gyroSwitch = findViewById<Switch>(R.id.switchGyro)
        val sensitivitySeek = findViewById<SeekBar>(R.id.seekBarSensitivity)
        val gyroHoldButton = findViewById<Button>(R.id.buttonGyroHold)
        val recalibrateButton = findViewById<Button>(R.id.buttonRecalibrate)
        val calibrationStatusText = findViewById<TextView>(R.id.textCalibrationStatus)
        statusText = findViewById(R.id.textStatus)
        packetCountText = findViewById(R.id.textPacketCount)
        rumbleStatusText = findViewById(R.id.textRumbleStatus)
        crosshairView = findViewById(R.id.crosshairView)

        radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            usbModeSelected = checkedId == R.id.radioUsb
            labelIp.visibility = if (usbModeSelected) android.view.View.GONE else android.view.View.VISIBLE
            ipInput.visibility = if (usbModeSelected) android.view.View.GONE else android.view.View.VISIBLE
            usbHint.visibility = if (usbModeSelected) android.view.View.VISIBLE else android.view.View.GONE
        }

        connectButton.setOnClickListener {
            transport?.stop()
            val port = portInput.text.toString().toIntOrNull() ?: 25565

            transport = if (usbModeSelected) {
                TcpAdbTransport(state, port = port)
            } else {
                val ip = ipInput.text.toString().ifBlank { "192.168.1.100" }
                UdpTransport(state, host = ip, port = port)
            }

            transport?.onStatusChanged = { msg ->
                runOnUiThread { statusText.text = msg }
            }
            transport?.onError = { e ->
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            }
            transport?.onPacketSent = {
                packetsSent++
                if (packetsSent % 60 == 0) {
                    runOnUiThread { packetCountText.text = "Paket terkirim: $packetsSent" }
                }
            }
            transport?.onRumbleReceived = { rumble ->
                runOnUiThread { applyRumble(rumble) }
            }
            transport?.start(lifecycleScope)
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

        recalibrateButton.setOnClickListener {
            gyroManager.startCalibration()
        }

        // Callback status HARUS dipasang sebelum gyroManager.start() dipanggil
        // di bawah, karena start() langsung memicu kalibrasi awal - kalau
        // dipasang setelahnya, pesan status pertama akan terlewat.
        gyroManager.onCalibrationStatusChanged = { msg ->
            runOnUiThread { calibrationStatusText.text = msg }
        }

        gyroManager.onVisualOffsetChanged = { x, y ->
            runOnUiThread { crosshairView.setOffset(x, y) }
        }

        gyroManager.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        gyroManager.stop()
        transport?.stop()
        vibrator?.cancel()
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

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Terjemahkan rumble dari game (large/small motor, 0f..1f) jadi getaran HP.
     *
     * HP cuma punya satu motor getar (beda dari controller yang punya dua
     * motor terpisah), jadi kedua nilai digabung jadi satu intensitas lewat
     * [RumbleState.combinedIntensity]. Karena game biasanya mengirim ulang
     * rumble terus-menerus selama efeknya berlangsung (bukan sekali tembak),
     * di sini cukup memicu getaran pendek tiap kali nilai baru datang -
     * selama game terus mengirim, getaran akan terasa hampir kontinu.
     */
    private fun applyRumble(rumble: RumbleState) {
        val v = vibrator ?: return
        if (!rumble.isActive) {
            v.cancel()
            rumbleStatusText.text = "Rumble dari game: tidak aktif"
            return
        }

        val intensity = rumble.combinedIntensity()
        rumbleStatusText.text = "Rumble dari game: aktif (${(intensity * 100).toInt()}%)"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = (intensity * 255).toInt().coerceIn(1, 255)
            v.vibrate(VibrationEffect.createOneShot(120, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(120)
        }
    }
}
