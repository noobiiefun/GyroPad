package com.gyropad.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Gaya visual crosshair yang bisa dipilih user. Nama & bentuk diadaptasi
 * dari referensi HUD scope game (mis. tampilan bidikan Monster Hunter) dan
 * kumpulan ikon crosshair dekoratif bergaya sci-fi/HUD.
 *
 * [displayName] dipakai langsung sebagai label di dropdown pemilihan tema
 * - ubah di sini kalau mau ganti teksnya, tidak perlu ubah tempat lain.
 */
enum class CrosshairStyle(val displayName: String) {
    CLASSIC("Klasik"),
    SCOPE_RETICLE("Scope Presisi (ala MH)"),
    TACTICAL_BRACKETS("Bracket Taktis"),
    DASHED_RING("Cincin Putus-putus"),
    CHEVRON_DIAMOND("Chevron Berlian"),
    HEX_DECO("Heksagon")
}

/**
 * Panel visual buat "merasakan" gerakan gyro sebelum masuk game -
 * BUKAN overlay yang muncul di atas layar game (app HP tidak punya akses ke
 * layar PC). Anggap ini semacam alat kalibrasi/testing sensitivitas gyro.
 *
 * Titik crosshair bergerak menjauhi tengah sesuai [setOffset], diklem ke
 * radius area yang tersedia, lalu balik ke tengah sendiri kalau
 * [setOffset] dipanggil dengan (0,0) berturut-turut (dikendalikan dari
 * GyroManager lewat decay).
 *
 * [style] menentukan bentuk yang digambar - lihat [CrosshairStyle]. Semua
 * gaya memakai titik (px,py) yang sama (posisi hasil offset gyro saat ini),
 * cuma beda cara menggambarnya - jadi mengganti tema TIDAK memengaruhi
 * logika gerakan sama sekali, murni tampilan.
 */
class CrosshairView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var offsetX = 0f
    private var offsetY = 0f

    var style: CrosshairStyle = CrosshairStyle.CLASSIC
        set(value) {
            field = value
            invalidate()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#331565C0")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#29B6F6")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val accentThinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#29B6F6")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val accentFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#29B6F6")
        style = Paint.Style.FILL
    }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#801565C0")
        style = Paint.Style.FILL
    }
    private val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#29B6F6")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
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
        val px = cx + offsetX * radius
        val py = cy + offsetY * radius

        // Area batas gerak (lingkaran tipis) + titik tengah referensi (posisi
        // netral) - sama di semua gaya, supaya user tetap bisa lihat batas
        // gerak & posisi netral apapun tema yang dipilih.
        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawCircle(cx, cy, 6f, centerDotPaint)

        when (style) {
            CrosshairStyle.CLASSIC -> drawClassic(canvas, px, py)
            CrosshairStyle.SCOPE_RETICLE -> drawScopeReticle(canvas, cx, cy, radius, px, py)
            CrosshairStyle.TACTICAL_BRACKETS -> drawTacticalBrackets(canvas, px, py)
            CrosshairStyle.DASHED_RING -> drawDashedRing(canvas, px, py)
            CrosshairStyle.CHEVRON_DIAMOND -> drawChevronDiamond(canvas, px, py)
            CrosshairStyle.HEX_DECO -> drawHexDeco(canvas, px, py)
        }
    }

    /** Gaya asli GyroPad sejak v0.1: cross sederhana + lingkaran kecil. */
    private fun drawClassic(canvas: Canvas, px: Float, py: Float) {
        val armLength = 22f
        canvas.drawLine(px - armLength, py, px + armLength, py, accentPaint)
        canvas.drawLine(px, py - armLength, px, py + armLength, accentPaint)
        canvas.drawCircle(px, py, armLength * 0.55f, accentPaint)
    }

    /**
     * Terinspirasi dari HUD bidikan Monster Hunter: garis horizontal &
     * vertikal panjang menyilang seluruh area, cincin luar dengan tanda
     * graduasi radial, dan lingkaran kecil di tengah target.
     */
    private fun drawScopeReticle(canvas: Canvas, cx: Float, cy: Float, radius: Float, px: Float, py: Float) {
        // Garis horizontal & vertikal penuh, dengan celah kecil di tengah
        // target (biar tidak menutupi titik bidik itu sendiri).
        val gap = 26f
        canvas.drawLine(0f, py, px - gap, py, accentThinPaint)
        canvas.drawLine(px + gap, py, width.toFloat(), py, accentThinPaint)
        canvas.drawLine(px, 0f, px, py - gap, accentThinPaint)
        canvas.drawLine(px, py + gap, px, height.toFloat(), accentThinPaint)

        // Cincin luar + tanda graduasi kecil sekeliling (mirip scope game)
        val tickRingRadius = radius * 0.55f
        canvas.drawCircle(px, py, tickRingRadius, accentThinPaint)
        val tickCount = 16
        for (i in 0 until tickCount) {
            val angle = (2 * Math.PI * i / tickCount).toFloat()
            val outer = tickRingRadius + 6f
            val inner = tickRingRadius - 6f
            val x1 = px + cos(angle) * inner
            val y1 = py + sin(angle) * inner
            val x2 = px + cos(angle) * outer
            val y2 = py + sin(angle) * outer
            canvas.drawLine(x1, y1, x2, y2, accentThinPaint)
        }

        // Lingkaran kecil solid di titik bidik, seperti reticle dot target lock
        canvas.drawCircle(px, py, 7f, accentFillPaint)
        canvas.drawCircle(px, py, 14f, accentPaint)
    }

    /** Empat bracket sudut (kayak framing kamera/target lock kotak). */
    private fun drawTacticalBrackets(canvas: Canvas, px: Float, py: Float) {
        val half = 26f
        val armLength = 12f

        // kiri-atas
        canvas.drawLine(px - half, py - half, px - half + armLength, py - half, accentPaint)
        canvas.drawLine(px - half, py - half, px - half, py - half + armLength, accentPaint)
        // kanan-atas
        canvas.drawLine(px + half, py - half, px + half - armLength, py - half, accentPaint)
        canvas.drawLine(px + half, py - half, px + half, py - half + armLength, accentPaint)
        // kiri-bawah
        canvas.drawLine(px - half, py + half, px - half + armLength, py + half, accentPaint)
        canvas.drawLine(px - half, py + half, px - half, py + half - armLength, accentPaint)
        // kanan-bawah
        canvas.drawLine(px + half, py + half, px + half - armLength, py + half, accentPaint)
        canvas.drawLine(px + half, py + half, px + half, py + half - armLength, accentPaint)

        // cross kecil di tengah
        val crossLen = 8f
        canvas.drawLine(px - crossLen, py, px + crossLen, py, accentThinPaint)
        canvas.drawLine(px, py - crossLen, px, py + crossLen, accentThinPaint)
    }

    /** Cincin dengan garis putus-putus + titik tengah solid. */
    private fun drawDashedRing(canvas: Canvas, px: Float, py: Float) {
        canvas.drawCircle(px, py, 24f, dashedPaint)
        canvas.drawCircle(px, py, 4f, accentFillPaint)
    }

    /** Dua chevron (atas & bawah, seperti panah "V"/"^") + belah ketupat di tengah. */
    private fun drawChevronDiamond(canvas: Canvas, px: Float, py: Float) {
        val chevronWidth = 16f
        val chevronHeight = 10f
        val gapFromCenter = 20f

        // chevron atas, menghadap ke bawah (^)
        val topChevron = Path().apply {
            moveTo(px - chevronWidth, py - gapFromCenter - chevronHeight)
            lineTo(px, py - gapFromCenter)
            lineTo(px + chevronWidth, py - gapFromCenter - chevronHeight)
        }
        canvas.drawPath(topChevron, accentPaint)

        // chevron bawah, menghadap ke atas (v terbalik)
        val bottomChevron = Path().apply {
            moveTo(px - chevronWidth, py + gapFromCenter + chevronHeight)
            lineTo(px, py + gapFromCenter)
            lineTo(px + chevronWidth, py + gapFromCenter + chevronHeight)
        }
        canvas.drawPath(bottomChevron, accentPaint)

        // belah ketupat kecil di tengah
        val diamondSize = 9f
        val diamond = Path().apply {
            moveTo(px, py - diamondSize)
            lineTo(px + diamondSize, py)
            lineTo(px, py + diamondSize)
            lineTo(px - diamondSize, py)
            close()
        }
        canvas.drawPath(diamond, accentThinPaint)
    }

    /** Dua heksagon dekoratif di kiri-kanan titik bidik + titik tengah. */
    private fun drawHexDeco(canvas: Canvas, px: Float, py: Float) {
        val hexRadius = 14f
        val offsetFromCenter = 24f

        drawHexagon(canvas, px - offsetFromCenter, py, hexRadius, accentThinPaint)
        drawHexagon(canvas, px + offsetFromCenter, py, hexRadius, accentThinPaint)
        canvas.drawCircle(px, py, 5f, accentFillPaint)
    }

    private fun drawHexagon(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val path = Path()
        for (i in 0..6) {
            val angle = (Math.PI / 3 * i).toFloat()
            val x = cx + cos(angle) * r
            val y = cy + sin(angle) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}
