package com.retrochan.app

import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Kontroler layar v2.
 * - Setiap jari "memegang" satu area (D-pad / tombol aksi / tombol tunggal) sejak disentuh,
 *   sehingga jari yang bergeser tidak menekan tombol area lain secara tidak sengaja.
 * - D-pad berbasis sudut dengan hysteresis (diagonal tidak mudah terpicu).
 * - Zona sentuh lebih luas dari gambar tombol, kombinasi A+B hanya di titik pertemuan tombol.
 */
class ControlsView(
    context: Context,
    private val onKey: (keyCode: Int, down: Boolean) -> Unit,
    private val onMenu: () -> Unit,
    private val onFastForward: () -> Unit
) : View(context) {

    var translucent = false
        set(v) { field = v; invalidate() }
    var alphaLevel = 120
        set(v) { field = v; invalidate() }
    var scale = 1f
        set(v) { field = v; relayout() }
    var leftHanded = false
        set(v) { field = v; relayout() }
    var haptic = true
    var turbo = false
        set(v) { field = v; releaseAll() }
    var ffActive = false
        set(v) { field = v; invalidate() }

    private companion object {
        const val UP = 1
        const val DOWN = 2
        const val LEFT = 4
        const val RIGHT = 8
        const val A = 16
        const val B = 32
        const val X = 64
        const val Y = 128
        const val L = 256
        const val R = 512
        const val SEL = 1024
        const val START = 2048
        // urutan harus sama dengan bit di atas (bit i -> KEYS[i])
        val KEYS = intArrayOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START
        )
        const val MAX_POINTERS = 16
    }

    private class Btn(val label: String, val mask: Int, val circle: Boolean, val special: Int = 0) {
        val r = RectF()
        val cx: Float get() = r.centerX()
        val cy: Float get() = r.centerY()
        fun contains(x: Float, y: Float, pad: Float) =
            x >= r.left - pad && x <= r.right + pad && y >= r.top - pad && y <= r.bottom + pad
    }

    private val btnA = Btn("A", A, true)
    private val btnB = Btn("B", B, true)
    private val btnX = Btn("X", X, true)
    private val btnY = Btn("Y", Y, true)
    private val btnL = Btn("L", L, false)
    private val btnR = Btn("R", R, false)
    private val btnSel = Btn("SELECT", SEL, false)
    private val btnStart = Btn("START", START, false)
    private val btnMenu = Btn("☰ MENU", 0, false, 1)
    private val btnFF = Btn("FF", 0, false, 2)
    private val faceBtns = arrayOf(btnX, btnB, btnY, btnA)
    private val smallBtns = arrayOf(btnL, btnR, btnSel, btnStart, btnMenu, btnFF)

    private var dCx = 0f; private var dCy = 0f; private var dR = 0f
    private var fCx = 0f; private var fCy = 0f; private var fr = 0f; private var off = 0f

    private var mask = 0
    private var lastDpad = 0
    private val ownKind = IntArray(MAX_POINTERS)   // 0 kosong, 1 dpad, 2 aksi, 3 tombol tunggal, 4 sudah diproses
    private val ownMask = IntArray(MAX_POINTERS)
    private val dist = FloatArray(4)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val tmp = RectF()
    private val path = Path()

    // ---------------- Layout ----------------
    fun relayout() { if (width > 0 && height > 0) layoutAll(width.toFloat(), height.toFloat()); invalidate() }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { layoutAll(w.toFloat(), h.toFloat()) }

    private fun layoutAll(W: Float, H: Float) {
        val d = resources.displayMetrics.density
        val land = W > H
        val m = (if (land) 28 else 16) * d
        dCy = if (land) H * 0.58f else H * 0.56f
        fCy = dCy

        val avail = W - 2 * m - 24 * d
        val base = if (land) min(H * 0.12f, 44 * d) else 38 * d
        var f = min(base * scale, avail / 8.5f)
        f = min(f, (H - 46 * d - dCy) / 2.5f)           // muat di bawah (baris start/select)
        f = min(f, (dCy - 56 * d) / 2.5f)               // muat di bawah baris bahu
        f = max(f, 20 * d)
        fr = f; off = f * 1.55f; dR = f * 1.75f

        if (!leftHanded) { dCx = m + dR; fCx = W - m - off - fr }
        else { dCx = W - m - dR; fCx = m + off + fr }

        fun circ(b: Btn, x: Float, y: Float) = b.r.set(x - fr, y - fr, x + fr, y + fr)
        circ(btnX, fCx, fCy - off); circ(btnB, fCx, fCy + off)
        circ(btnY, fCx - off, fCy); circ(btnA, fCx + off, fCy)

        val sc = scale.coerceIn(0.9f, 1.15f)
        val sw = min(84 * d * sc, W / 2 - 66 * d - m)
        val sh = 42 * d * sc
        btnL.r.set(m, 8 * d, m + sw, 8 * d + sh)
        btnR.r.set(W - m - sw, 8 * d, W - m, 8 * d + sh)
        btnFF.r.set(W / 2 - 61 * d, 10 * d, W / 2 - 17 * d, 10 * d + 36 * d)
        btnMenu.r.set(W / 2 - 11 * d, 10 * d, W / 2 + 61 * d, 10 * d + 36 * d)

        val pw = 70 * d; val ph = 32 * d; val y = H - 8 * d - ph
        btnSel.r.set(W / 2 - pw - 8 * d, y, W / 2 - 8 * d, y + ph)
        btnStart.r.set(W / 2 + 8 * d, y, W / 2 + 8 * d + pw, y + ph)
    }

    // ---------------- Gambar ----------------
    override fun onDraw(c: Canvas) {
        val aa = if (translucent) alphaLevel else 255
        drawDpad(c, aa)
        for (b in faceBtns) drawBtn(c, b, aa)
        for (b in smallBtns) drawBtn(c, b, aa)
    }

    private fun isOn(b: Btn) = if (b.special == 2) ffActive else b.mask != 0 && (mask and b.mask) != 0

    private fun drawBtn(c: Canvas, b: Btn, aa: Int) {
        val on = isOn(b)
        tmp.set(b.r)
        if (on) { val s = tmp.height() * 0.05f; tmp.inset(s, s) }
        fill.color = if (on) 0xFF6C4DFF.toInt() else 0xFF3A3158.toInt(); fill.alpha = aa
        ring.color = if (b.mask == A || b.mask == B) 0xFFFF7BB5.toInt() else 0xFF8F7CFF.toInt()
        ring.alpha = aa; ring.strokeWidth = resources.displayMetrics.density * 1.6f
        if (b.circle) {
            c.drawCircle(tmp.centerX(), tmp.centerY(), tmp.width() / 2, fill)
            c.drawCircle(tmp.centerX(), tmp.centerY(), tmp.width() / 2, ring)
            txt.textSize = tmp.width() * 0.42f
        } else {
            val rad = tmp.height() / 2
            c.drawRoundRect(tmp, rad, rad, fill); c.drawRoundRect(tmp, rad, rad, ring)
            txt.textSize = tmp.height() * 0.36f
        }
        txt.alpha = if (translucent) min(255, aa + 70) else 255
        c.drawText(b.label, tmp.centerX(), tmp.centerY() - (txt.ascent() + txt.descent()) / 2, txt)
    }

    private fun drawDpad(c: Canvas, aa: Int) {
        val t = dR * 0.66f
        val rad = t * 0.3f
        fill.color = 0xFF3A3158.toInt(); fill.alpha = aa
        c.drawRoundRect(dCx - dR, dCy - t / 2, dCx + dR, dCy + t / 2, rad, rad, fill)
        c.drawRoundRect(dCx - t / 2, dCy - dR, dCx + t / 2, dCy + dR, rad, rad, fill)
        fill.color = 0xFF6C4DFF.toInt(); fill.alpha = aa
        if (mask and UP != 0) c.drawRoundRect(dCx - t / 2, dCy - dR, dCx + t / 2, dCy, rad, rad, fill)
        if (mask and DOWN != 0) c.drawRoundRect(dCx - t / 2, dCy, dCx + t / 2, dCy + dR, rad, rad, fill)
        if (mask and LEFT != 0) c.drawRoundRect(dCx - dR, dCy - t / 2, dCx, dCy + t / 2, rad, rad, fill)
        if (mask and RIGHT != 0) c.drawRoundRect(dCx, dCy - t / 2, dCx + dR, dCy + t / 2, rad, rad, fill)
        fill.color = Color.WHITE; fill.alpha = if (translucent) 170 else 230
        val s = dR * 0.13f; val p = dR * 0.70f
        tri(c, dCx, dCy - p, 0f, -1f, s); tri(c, dCx, dCy + p, 0f, 1f, s)
        tri(c, dCx - p, dCy, -1f, 0f, s); tri(c, dCx + p, dCy, 1f, 0f, s)
    }

    private fun tri(c: Canvas, x: Float, y: Float, dx: Float, dy: Float, s: Float) {
        path.rewind()
        path.moveTo(x + dx * s, y + dy * s)
        path.lineTo(x - dx * s * 0.6f - dy * s, y - dy * s * 0.6f + dx * s)
        path.lineTo(x - dx * s * 0.6f + dy * s, y - dy * s * 0.6f - dx * s)
        path.close(); c.drawPath(path, fill)
    }

    // ---------------- Sentuhan ----------------
    private fun dpadMask(x: Float, y: Float): Int {
        val dx = x - dCx; val dy = y - dCy
        if (hypot(dx, dy) < dR * 0.2f) { lastDpad = 0; return 0 }
        val ax = abs(dx); val ay = abs(dy)
        val ratio = min(ax, ay) / max(ax, ay)
        val wasDiag = (lastDpad and (UP or DOWN)) != 0 && (lastDpad and (LEFT or RIGHT)) != 0
        val diag = if (wasDiag) ratio > 0.45f else ratio > 0.62f
        val m = if (diag) (if (dx < 0) LEFT else RIGHT) or (if (dy < 0) UP else DOWN)
        else if (ax > ay) (if (dx < 0) LEFT else RIGHT) else (if (dy < 0) UP else DOWN)
        lastDpad = m
        return m
    }

    private fun faceMask(x: Float, y: Float): Int {
        var best = 0
        for (i in 0..3) { dist[i] = hypot(x - faceBtns[i].cx, y - faceBtns[i].cy); if (dist[i] < dist[best]) best = i }
        if (dist[best] > fr * 1.7f) return 0
        var second = -1
        for (i in 0..3) if (i != best && (second < 0 || dist[i] < dist[second])) second = i
        var m = faceBtns[best].mask
        // kombinasi 2 tombol hanya jika jari tepat di titik pertemuan
        if (second >= 0 && dist[best] < fr * 1.12f && dist[second] < fr * 1.12f) m = m or faceBtns[second].mask
        return m
    }

    private fun assign(pid: Int, x: Float, y: Float) {
        ownKind[pid] = 0
        val pad = 5 * resources.displayMetrics.density
        for (b in smallBtns) if (b.contains(x, y, pad)) {
            when (b.special) {
                1 -> { releaseAll(); ownKind[pid] = 4; onMenu() }
                2 -> { ownKind[pid] = 4; onFastForward(); if (haptic) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); invalidate() }
                else -> { ownKind[pid] = 3; ownMask[pid] = b.mask }
            }
            return
        }
        val dn = hypot(x - dCx, y - dCy) / (dR * 1.3f)
        val fn = hypot(x - fCx, y - fCy) / (off + fr * 1.35f)
        if (dn <= 1f || fn <= 1f) ownKind[pid] = if (dn <= fn) 1 else 2
    }

    private fun computeMask(e: MotionEvent, skip: Int): Int {
        var m = 0
        val pad = 14 * resources.displayMetrics.density
        for (i in 0 until e.pointerCount) {
            if (i == skip) continue
            val pid = e.getPointerId(i)
            if (pid >= MAX_POINTERS) continue
            val x = e.getX(i); val y = e.getY(i)
            when (ownKind[pid]) {
                1 -> m = m or dpadMask(x, y)
                2 -> m = m or faceMask(x, y)
                3 -> for (b in smallBtns) if (b.mask == ownMask[pid] && b.contains(x, y, pad)) m = m or b.mask
            }
        }
        return m
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex; val pid = e.getPointerId(i)
            if (pid < MAX_POINTERS) assign(pid, e.getX(i), e.getY(i))
        }
        val ending = act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL
        val skip = if (act == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        val newMask = if (ending) 0 else computeMask(e, skip)
        applyMask(newMask)
        if (ending) { ownKind.fill(0); lastDpad = 0 }
        else if (skip >= 0) { val pid = e.getPointerId(skip); if (pid < MAX_POINTERS) ownKind[pid] = 0 }
        return true
    }

    // ---------------- Kirim tombol ----------------
    private var turboPhase = true
    private var turboRunning = false
    private val turboTick = object : Runnable {
        override fun run() {
            val held = mask and (A or B)
            if (!turbo || held == 0) { turboRunning = false; return }
            turboPhase = !turboPhase
            if (held and A != 0) onKey(KEYS[4], turboPhase)
            if (held and B != 0) onKey(KEYS[5], turboPhase)
            postDelayed(this, 45)
        }
    }

    private fun applyMask(newMask: Int) {
        if (newMask == mask) return
        val changed = newMask xor mask
        val pressedNow = newMask and mask.inv()
        for (i in KEYS.indices) {
            val bit = 1 shl i
            if (changed and bit == 0) continue
            val down = newMask and bit != 0
            onKey(KEYS[i], down)
            if (turbo && down && (bit == A || bit == B)) {
                turboPhase = true
                if (!turboRunning) { turboRunning = true; postDelayed(turboTick, 45) }
            }
        }
        mask = newMask
        if (haptic && pressedNow != 0) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        invalidate()
    }

    fun releaseAll() {
        for (i in KEYS.indices) if (mask and (1 shl i) != 0) onKey(KEYS[i], false)
        mask = 0; lastDpad = 0; ownKind.fill(0); invalidate()
    }

    override fun onDetachedFromWindow() { removeCallbacks(turboTick); super.onDetachedFromWindow() }
}
