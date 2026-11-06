package com.retrochan.app

import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/** Kontroler layar: multi-touch, geser jari antar tombol, D-pad 8 arah, otomatis menyesuaikan portrait/landscape. */
class ControlsView(
    context: Context,
    private val onKey: (keyCode: Int, down: Boolean) -> Unit,
    private val onMenu: () -> Unit
) : View(context) {

    var translucent = false
        set(v) { field = v; invalidate() }

    private class Btn(val label: String, val key: Int, val circle: Boolean) {
        val r = RectF()
        fun hit(x: Float, y: Float): Boolean =
            if (circle) hypot(x - r.centerX(), y - r.centerY()) <= r.width() / 2f * 1.2f
            else RectF(r).apply { inset(-r.height() * 0.15f, -r.height() * 0.15f) }.contains(x, y)
    }

    private val MENU = -1
    private val btnA = Btn("A", KeyEvent.KEYCODE_BUTTON_A, true)
    private val btnB = Btn("B", KeyEvent.KEYCODE_BUTTON_B, true)
    private val btnX = Btn("X", KeyEvent.KEYCODE_BUTTON_X, true)
    private val btnY = Btn("Y", KeyEvent.KEYCODE_BUTTON_Y, true)
    private val btnL = Btn("L", KeyEvent.KEYCODE_BUTTON_L1, false)
    private val btnR = Btn("R", KeyEvent.KEYCODE_BUTTON_R1, false)
    private val btnSel = Btn("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, false)
    private val btnStart = Btn("START", KeyEvent.KEYCODE_BUTTON_START, false)
    private val btnMenu = Btn("☰ MENU", MENU, false)
    private val all = listOf(btnA, btnB, btnX, btnY, btnL, btnR, btnSel, btnStart, btnMenu)

    private var dCx = 0f; private var dCy = 0f; private var dR = 0f
    private val pressed = mutableSetOf<Int>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val W = w.toFloat(); val H = h.toFloat(); val d = resources.displayMetrics.density
        val land = W > H
        val m = 22 * d
        dR = if (land) min(H * 0.20f, 68 * d) else min(W * 0.19f, 74 * d)
        dCx = m + dR
        dCy = if (land) H * 0.64f else H * 0.56f

        val fr = if (land) min(H * 0.10f, 32 * d) else min(W * 0.095f, 34 * d)
        val off = fr * 1.6f
        val fcx = W - m - off - fr
        val fcy = dCy
        fun circ(b: Btn, x: Float, y: Float) = b.r.set(x - fr, y - fr, x + fr, y + fr)
        circ(btnX, fcx, fcy - off); circ(btnB, fcx, fcy + off)
        circ(btnY, fcx - off, fcy); circ(btnA, fcx + off, fcy)

        val sw = 84 * d; val sh = 36 * d
        btnL.r.set(m, 10 * d, m + sw, 10 * d + sh)
        btnR.r.set(W - m - sw, 10 * d, W - m, 10 * d + sh)
        btnMenu.r.set(W / 2 - 42 * d, 10 * d, W / 2 + 42 * d, 10 * d + 32 * d)

        val pw = 64 * d; val ph = 28 * d; val y = H - 10 * d - ph
        btnSel.r.set(W / 2 - pw - 8 * d, y, W / 2 - 8 * d, y + ph)
        btnStart.r.set(W / 2 + 8 * d, y, W / 2 + 8 * d + pw, y + ph)
    }

    private fun a() = if (translucent) 110 else 255

    override fun onDraw(c: Canvas) {
        drawDpad(c)
        all.forEach { drawBtn(c, it) }
    }

    private fun drawBtn(c: Canvas, b: Btn) {
        val on = b.key in pressed
        fill.color = if (on) 0xFF6C4DFF.toInt() else 0xFF3A3158.toInt(); fill.alpha = a()
        ring.color = if (b.key == KeyEvent.KEYCODE_BUTTON_A || b.key == KeyEvent.KEYCODE_BUTTON_B) 0xFFFF7BB5.toInt() else 0xFF8F7CFF.toInt()
        ring.alpha = a(); ring.strokeWidth = resources.displayMetrics.density * 1.5f
        if (b.circle) {
            c.drawCircle(b.r.centerX(), b.r.centerY(), b.r.width() / 2, fill)
            c.drawCircle(b.r.centerX(), b.r.centerY(), b.r.width() / 2, ring)
            txt.textSize = b.r.width() * 0.42f
        } else {
            val rad = b.r.height() / 2
            c.drawRoundRect(b.r, rad, rad, fill); c.drawRoundRect(b.r, rad, rad, ring)
            txt.textSize = b.r.height() * 0.38f
        }
        txt.alpha = if (translucent) 190 else 255
        c.drawText(b.label, b.r.centerX(), b.r.centerY() - (txt.ascent() + txt.descent()) / 2, txt)
    }

    private fun drawDpad(c: Canvas) {
        val t = dR * 0.64f
        fill.color = 0xFF3A3158.toInt(); fill.alpha = a()
        val rad = t * 0.3f
        c.drawRoundRect(dCx - dR, dCy - t / 2, dCx + dR, dCy + t / 2, rad, rad, fill)
        c.drawRoundRect(dCx - t / 2, dCy - dR, dCx + t / 2, dCy + dR, rad, rad, fill)
        fill.color = 0xFF6C4DFF.toInt(); fill.alpha = a()
        if (KeyEvent.KEYCODE_DPAD_UP in pressed) c.drawRoundRect(dCx - t / 2, dCy - dR, dCx + t / 2, dCy, rad, rad, fill)
        if (KeyEvent.KEYCODE_DPAD_DOWN in pressed) c.drawRoundRect(dCx - t / 2, dCy, dCx + t / 2, dCy + dR, rad, rad, fill)
        if (KeyEvent.KEYCODE_DPAD_LEFT in pressed) c.drawRoundRect(dCx - dR, dCy - t / 2, dCx, dCy + t / 2, rad, rad, fill)
        if (KeyEvent.KEYCODE_DPAD_RIGHT in pressed) c.drawRoundRect(dCx, dCy - t / 2, dCx + dR, dCy + t / 2, rad, rad, fill)
        fill.color = Color.WHITE; fill.alpha = if (translucent) 170 else 230
        val s = dR * 0.13f; val p = dR * 0.70f
        tri(c, dCx, dCy - p, 0f, -1f, s); tri(c, dCx, dCy + p, 0f, 1f, s)
        tri(c, dCx - p, dCy, -1f, 0f, s); tri(c, dCx + p, dCy, 1f, 0f, s)
    }

    private fun tri(c: Canvas, x: Float, y: Float, dx: Float, dy: Float, s: Float) {
        val path = Path()
        path.moveTo(x + dx * s, y + dy * s)
        path.lineTo(x - dx * s * 0.6f - dy * s, y - dy * s * 0.6f + dx * s)
        path.lineTo(x - dx * s * 0.6f + dy * s, y - dy * s * 0.6f - dx * s)
        path.close(); c.drawPath(path, fill)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val now = mutableSetOf<Int>()
        val ending = e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL
        if (!ending) {
            for (i in 0 until e.pointerCount) {
                if (e.actionMasked == MotionEvent.ACTION_POINTER_UP && i == e.actionIndex) continue
                val x = e.getX(i); val y = e.getY(i)
                val dx = x - dCx; val dy = y - dCy; val dist = hypot(dx, dy)
                if (dist <= dR * 1.35f && dist > dR * 0.15f) {
                    val th = dR * 0.33f
                    if (dx < -th) now += KeyEvent.KEYCODE_DPAD_LEFT
                    if (dx > th) now += KeyEvent.KEYCODE_DPAD_RIGHT
                    if (dy < -th) now += KeyEvent.KEYCODE_DPAD_UP
                    if (dy > th) now += KeyEvent.KEYCODE_DPAD_DOWN
                }
                for (b in all) if (b.hit(x, y)) {
                    if (b.key == MENU) {
                        if ((e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) && i == e.actionIndex) {
                            releaseAll(); onMenu(); return true
                        }
                    } else now += b.key
                }
            }
        }
        for (k in now - pressed) { onKey(k, true); performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
        for (k in pressed - now) onKey(k, false)
        pressed.clear(); pressed.addAll(now)
        invalidate()
        return true
    }

    fun releaseAll() { pressed.forEach { onKey(it, false) }; pressed.clear(); invalidate() }
}
