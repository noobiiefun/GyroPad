package com.gyropad.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Panel visual buat "merasakan" gerakan gyro sebelum masuk game -
 * BUKAN overlay yang muncul di atas layar game (app HP tidak punya akses ke
 * layar PC). Anggap ini semacam alat kalibrasi/testing sensitivitas gyro.
 *
 * Titik crosshair bergerak menjauhi tengah sesuai [setOffset], diklem ke
 * radius area yang tersedia, lalu balik ke tengah sendiri kalau
 * [setOffset] dipanggil dengan (0,0) berturut-turut (dikendalikan dari
 * GyroManager lewat decay).
 */
class CrosshairView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var offsetX = 0f
    private var offsetY = 0f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#331565C0")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#29B6F6")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#801565C0")
        style = Paint.Style.FILL
    }

    /**
     * Update posisi crosshair.
     * [normX] dan [normY] ada di rentang -1f..1f (relatif terhadap radius area).
     */
    fun setOffset(normX: Float, normY: Float) {
        offsetX = normX.coerceIn(-1f, 1f)
        offsetY = normY.coerceIn(-1f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.85f

        // Area batas gerak (lingkaran tipis)
        canvas.drawCircle(cx, cy, radius, ringPaint)
        // Titik tengah referensi (posisi netral)
        canvas.drawCircle(cx, cy, 6f, centerDotPaint)

        val px = cx + offsetX * radius
        val py = cy + offsetY * radius
        val armLength = 22f

        canvas.drawLine(px - armLength, py, px + armLength, py, crosshairPaint)
        canvas.drawLine(px, py - armLength, px, py + armLength, crosshairPaint)
        canvas.drawCircle(px, py, armLength * 0.55f, crosshairPaint)
    }
}
