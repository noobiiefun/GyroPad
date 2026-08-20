package com.gyropad.app.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Penyimpanan lokal profil sensitivitas gyro per-game, pakai
 * `SharedPreferences` biasa - tidak perlu database untuk data sekecil ini
 * (paling banter beberapa puluh profil, tiap profil cuma nama + 1 angka).
 *
 * Disimpan sebagai satu JSON array di bawah satu key, bukan satu key per
 * profil - lebih gampang di-load/save utuh sekaligus, dan cukup ringan
 * untuk ukuran data seperti ini.
 */
class ProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadProfiles(): MutableList<SensitivityProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return defaultProfiles()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<SensitivityProfile>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(SensitivityProfile(o.getString("name"), o.getDouble("sensitivity").toFloat()))
            }
            if (list.isEmpty()) defaultProfiles() else list
        } catch (e: Exception) {
            defaultProfiles()
        }
    }

    fun saveProfiles(profiles: List<SensitivityProfile>) {
        val arr = JSONArray()
        profiles.forEach { profile ->
            val o = JSONObject()
            o.put("name", profile.name)
            o.put("sensitivity", profile.sensitivity)
            arr.put(o)
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun getActiveProfileName(): String? = prefs.getString(KEY_ACTIVE, null)

    fun setActiveProfileName(name: String) {
        prefs.edit().putString(KEY_ACTIVE, name).apply()
    }

    private fun defaultProfiles(): MutableList<SensitivityProfile> =
        mutableListOf(SensitivityProfile("Default", DEFAULT_SENSITIVITY))

    companion object {
        private const val PREFS_NAME = "gyropad_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE = "active_profile"

        // Sesuai default slider di activity_main.xml (progress=35 -> 0.2 + 0.35*2.8)
        const val DEFAULT_SENSITIVITY = 1.18f
    }
}
