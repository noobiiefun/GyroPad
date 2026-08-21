package com.gyropad.app

import android.graphics.Color
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gyropad.app.input.GamepadInputManager
import com.gyropad.app.input.GyroManager
import com.gyropad.app.model.ControllerState
import com.gyropad.app.model.RumbleState
import com.gyropad.app.net.GyroPadTransport
import com.gyropad.app.net.TcpAdbTransport
import com.gyropad.app.net.UdpTransport
import com.gyropad.app.profile.ProfileStore
import com.gyropad.app.profile.SensitivityProfile
import com.gyropad.app.ui.CrosshairPrefs
import com.gyropad.app.ui.CrosshairStyle
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
 *  - [ProfileStore] menyimpan beberapa preset sensitivitas gyro (biasanya
 *    satu per game) secara lokal, dipilih lewat dropdown profil.
 *  - Dua indikator status TERPISAH ditampilkan: "PC" (status [transport],
 *    berdasarkan paket yang berhasil terkirim/error) dan "Gamepad" (status
 *    device Bluetooth fisik, dipantau lewat [InputManager.InputDeviceListener]).
 *    Keduanya sengaja dipisah supaya saat ada masalah, jelas bagian mana yang
 *    putus - PC atau gamepad-nya - tanpa perlu menebak dari satu status umum.
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
    private lateinit var sensitivitySeek: SeekBar

    private var packetsSent = 0
    private var usbModeSelected = false

    // --- Indikator status terpisah: PC<->HP vs Gamepad<->HP ---
    private lateinit var pcStatusBadge: TextView
    private lateinit var gamepadStatusBadge: TextView
    private var pcConnected = false
    private val inputManager by lazy { getSystemService(INPUT_SERVICE) as InputManager }
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshGamepadStatus()
        override fun onInputDeviceRemoved(deviceId: Int) = refreshGamepadStatus()
        override fun onInputDeviceChanged(deviceId: Int) = refreshGamepadStatus()
    }

    // --- Profil sensitivitas ---
    private lateinit var profileStore: ProfileStore
    private lateinit var profileAdapter: ArrayAdapter<String>
    private lateinit var spinnerProfile: Spinner
    private var profiles: MutableList<SensitivityProfile> = mutableListOf()
    /** Dipakai supaya perubahan spinner yang kita picu sendiri (mis. saat
     * profil baru dipilih otomatis) tidak dianggap sebagai "user memilih
     * profil lain" dan memicu logika ganda. */
    private var suppressSpinnerCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gamepadInput = GamepadInputManager(state)
        gyroManager = GyroManager(this, state)
        vibrator = getVibrator()
        profileStore = ProfileStore(this)

        ipInput = findViewById(R.id.editTextIp)
        portInput = findViewById(R.id.editTextPort)
        labelIp = findViewById(R.id.labelIp)
        usbHint = findViewById(R.id.textUsbHint)
        radioGroupMode = findViewById(R.id.radioGroupMode)
        val connectButton = findViewById<Button>(R.id.buttonConnect)
        val gyroSwitch = findViewById<Switch>(R.id.switchGyro)
        sensitivitySeek = findViewById(R.id.seekBarSensitivity)
        val gyroHoldButton = findViewById<Button>(R.id.buttonGyroHold)
        val recalibrateButton = findViewById<Button>(R.id.buttonRecalibrate)
        val calibrationStatusText = findViewById<TextView>(R.id.textCalibrationStatus)
        spinnerProfile = findViewById(R.id.spinnerProfile)
        val addProfileButton = findViewById<Button>(R.id.buttonAddProfile)
        val deleteProfileButton = findViewById<Button>(R.id.buttonDeleteProfile)
        statusText = findViewById(R.id.textStatus)
        packetCountText = findViewById(R.id.textPacketCount)
        rumbleStatusText = findViewById(R.id.textRumbleStatus)
        crosshairView = findViewById(R.id.crosshairView)
        val spinnerCrosshairStyle = findViewById<Spinner>(R.id.spinnerCrosshairStyle)
        pcStatusBadge = findViewById(R.id.textPcStatusBadge)
        gamepadStatusBadge = findViewById(R.id.textGamepadStatusBadge)

        setupCrosshairStyleUi(spinnerCrosshairStyle)

        radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            usbModeSelected = checkedId == R.id.radioUsb
            labelIp.visibility = if (usbModeSelected) android.view.View.GONE else android.view.View.VISIBLE
            ipInput.visibility = if (usbModeSelected) android.view.View.GONE else android.view.View.VISIBLE
            usbHint.visibility = if (usbModeSelected) android.view.View.VISIBLE else android.view.View.GONE
        }

        connectButton.setOnClickListener {
            transport?.stop()
            setPcConnected(false)
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
                setPcConnected(false)
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            }
            transport?.onPacketSent = {
                packetsSent++
                if (!pcConnected) setPcConnected(true)
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

        setupProfileUi(addProfileButton, deleteProfileButton)

        sensitivitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // progress 0..100 dipetakan ke sensitivitas 0.2x..3.0x
                val newSensitivity = progressToSensitivity(progress)
                gyroManager.sensitivity = newSensitivity
                // Hanya simpan ke profil kalau ini gara-gara user geser slider
                // sendiri - bukan gara-gara applyProfile() men-set progress
                // secara terprogram saat pindah/muat profil.
                if (fromUser) {
                    updateActiveProfileSensitivity(newSensitivity)
                }
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

        inputManager.registerInputDeviceListener(inputDeviceListener, null)
        refreshGamepadStatus() // scan device yang sudah terpasang SEBELUM app dibuka -
                                // listener di atas cuma menangkap perubahan SETELAH ini
    }

    override fun onDestroy() {
        super.onDestroy()
        gyroManager.stop()
        transport?.stop()
        vibrator?.cancel()
        inputManager.unregisterInputDeviceListener(inputDeviceListener)
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

    // ------------------------------------------------------------------
    // Profil sensitivitas
    // ------------------------------------------------------------------

    private fun progressToSensitivity(progress: Int): Float = 0.2f + (progress / 100f) * 2.8f

    private fun sensitivityToProgress(sensitivity: Float): Int =
        (((sensitivity - 0.2f) / 2.8f) * 100).toInt().coerceIn(0, 100)

    private fun setupProfileUi(addProfileButton: Button, deleteProfileButton: Button) {
        profiles = profileStore.loadProfiles()
        profileAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            profiles.map { it.name }
        )
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerProfile.adapter = profileAdapter

        val activeName = profileStore.getActiveProfileName()
        val initialIndex = profiles.indexOfFirst { it.name == activeName }.let { if (it >= 0) it else 0 }
        suppressSpinnerCallback = true
        spinnerProfile.setSelection(initialIndex)
        suppressSpinnerCallback = false
        applyProfile(profiles[initialIndex])

        spinnerProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (suppressSpinnerCallback) return
                val profile = profiles.getOrNull(position) ?: return
                applyProfile(profile)
                profileStore.setActiveProfileName(profile.name)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        addProfileButton.setOnClickListener { showAddProfileDialog() }
        deleteProfileButton.setOnClickListener { deleteActiveProfile() }
    }

    /** Terapkan sensitivitas dari [profile] ke GyroManager & slider. */
    private fun applyProfile(profile: SensitivityProfile) {
        gyroManager.sensitivity = profile.sensitivity
        sensitivitySeek.progress = sensitivityToProgress(profile.sensitivity)
    }

    /** Dipanggil tiap slider digeser MANUAL oleh user - nilai baru langsung
     * disimpan ke profil yang lagi aktif, supaya tweak-nya persisten tanpa
     * perlu langkah "simpan" terpisah. */
    private fun updateActiveProfileSensitivity(sensitivity: Float) {
        val position = spinnerProfile.selectedItemPosition
        if (position < 0 || position >= profiles.size) return
        profiles[position] = profiles[position].copy(sensitivity = sensitivity)
        profileStore.saveProfiles(profiles)
    }

    private fun showAddProfileDialog() {
        val input = EditText(this)
        input.hint = "Nama game (mis. Monster Hunter Rise)"

        AlertDialog.Builder(this)
            .setTitle("Profil Sensitivitas Baru")
            .setMessage("Sensitivitas slider saat ini akan disimpan sebagai profil baru.")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    statusText.text = "Nama profil tidak boleh kosong"
                    return@setPositiveButton
                }
                if (profiles.any { it.name.equals(name, ignoreCase = true) }) {
                    statusText.text = "Profil \"$name\" sudah ada"
                    return@setPositiveButton
                }

                val newProfile = SensitivityProfile(name, gyroManager.sensitivity)
                profiles.add(newProfile)
                profileStore.saveProfiles(profiles)

                profileAdapter.clear()
                profileAdapter.addAll(profiles.map { it.name })
                profileAdapter.notifyDataSetChanged()

                suppressSpinnerCallback = true
                spinnerProfile.setSelection(profiles.size - 1)
                suppressSpinnerCallback = false
                profileStore.setActiveProfileName(newProfile.name)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteActiveProfile() {
        if (profiles.size <= 1) {
            statusText.text = "Minimal harus ada satu profil"
            return
        }
        val position = spinnerProfile.selectedItemPosition
        if (position < 0 || position >= profiles.size) return
        val removedName = profiles[position].name

        AlertDialog.Builder(this)
            .setTitle("Hapus Profil")
            .setMessage("Hapus profil \"$removedName\"?")
            .setPositiveButton("Hapus") { _, _ ->
                profiles.removeAt(position)
                profileStore.saveProfiles(profiles)

                profileAdapter.clear()
                profileAdapter.addAll(profiles.map { it.name })
                profileAdapter.notifyDataSetChanged()

                suppressSpinnerCallback = true
                spinnerProfile.setSelection(0)
                suppressSpinnerCallback = false
                applyProfile(profiles[0])
                profileStore.setActiveProfileName(profiles[0].name)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ------------------------------------------------------------------
    // Indikator status terpisah: PC<->HP vs Gamepad<->HP
    // ------------------------------------------------------------------

    /**
     * Dipanggil setiap ada sinyal konektivitas PC berubah - paket berhasil
     * terkirim pertama kali (jadi true) atau transport melempar error
     * (jadi false). Terpisah dari [statusText] yang menampilkan pesan detail
     * (mis. alamat IP, pesan error) - badge ini murni ringkasan ya/tidak.
     */
    private fun setPcConnected(connected: Boolean) {
        if (pcConnected == connected) return
        pcConnected = connected
        runOnUiThread {
            pcStatusBadge.text = if (connected) "● PC: Terhubung" else "● PC: Tidak terhubung"
            pcStatusBadge.setTextColor(if (connected) COLOR_CONNECTED else COLOR_DISCONNECTED)
        }
    }

    /**
     * Cek apakah ada device dengan sumber SOURCE_GAMEPAD/SOURCE_JOYSTICK
     * yang sedang terpasang (mis. iPega 9076 yang sudah dipasangkan lewat
     * Bluetooth). Dipanggil sekali saat app dibuka (buat device yang SUDAH
     * terpasang sebelum listener dipasang), dan setiap kali
     * [InputManager.InputDeviceListener] melaporkan perubahan.
     */
    private fun refreshGamepadStatus() {
        val connected = InputDevice.getDeviceIds().any { id ->
            val device = InputDevice.getDevice(id) ?: return@any false
            val sources = device.sources
            (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }
        runOnUiThread {
            gamepadStatusBadge.text = if (connected) "● Gamepad: Terhubung" else "● Gamepad: Tidak terhubung"
            gamepadStatusBadge.setTextColor(if (connected) COLOR_CONNECTED else COLOR_DISCONNECTED)
        }
    }

    // ------------------------------------------------------------------
    // Tema/gaya crosshair
    // ------------------------------------------------------------------

    /**
     * Isi dropdown tema dari semua nilai [CrosshairStyle], muat pilihan
     * terakhir dari [CrosshairPrefs], dan simpan lagi setiap kali user
     * pilih tema lain - jadi tema yang dipilih bertahan walau app ditutup.
     */
    private fun setupCrosshairStyleUi(spinner: Spinner) {
        val styles = CrosshairStyle.values()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            styles.map { it.displayName }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val savedStyle = CrosshairPrefs.loadStyle(this)
        val initialIndex = styles.indexOf(savedStyle).coerceAtLeast(0)
        spinner.setSelection(initialIndex)
        crosshairView.style = styles[initialIndex]

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selected = styles.getOrNull(position) ?: return
                crosshairView.style = selected
                CrosshairPrefs.saveStyle(this@MainActivity, selected)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    companion object {
        private val COLOR_CONNECTED = Color.parseColor("#2E7D32")
        private val COLOR_DISCONNECTED = Color.parseColor("#C62828")
    }
}
