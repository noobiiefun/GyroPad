package com.gyropad.app.ui

import android.content.Context

/**
 * Simpan/muat pilihan tema crosshair user - cuma satu nilai (nama enum),
 * jadi tidak perlu JSON seperti [com.gyropad.app.profile.ProfileStore],
 * langsung satu key `SharedPreferences` biasa.
 */
object CrosshairPrefs {
    private const val PREFS_NAME = "gyropad_ui_prefs"
    private const val KEY_STYLE = "crosshair_style"

    fun loadStyle(context: Context): CrosshairStyle {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_STYLE, null) ?: return CrosshairStyle.CLASSIC
        return try {
            CrosshairStyle.valueOf(name)
        } catch (e: IllegalArgumentException) {
            CrosshairStyle.CLASSIC
        }
    }

    fun saveStyle(context: Context, style: CrosshairStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.name)
            .apply()
    }
}
