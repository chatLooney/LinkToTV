package dev.linktotv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface

private const val BG = 0xFF1C1C1E.toInt()
private const val FG = 0xFFFAFAF7.toInt()
private const val MUTED = 0xFFB8B8B2.toInt()
private const val ACCENT = 0xFFFFD23F.toInt()
private const val CHIP = 0xFF2C2C2F.toInt()

/** TV screen: shows the web address, QR code and pairing code, and keeps the receiver running. */
class TvActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var address: TextView
    private lateinit var code: TextView
    private lateinit var qr: ImageView
    private var shown = ""

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun box(fill: Int, stroke: Int = 0) = GradientDrawable().apply {
        cornerRadius = 24f; setColor(fill); if (stroke != 0) setStroke(6, stroke)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val logo = ImageView(this).apply { setImageResource(R.mipmap.ic_launcher); layoutParams = LinearLayout.LayoutParams(96, 96) }
        val brand = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; addView(logo); addView(text("  Link to TV", 26f, FG, true)) }
        address = text("", 22f, FG, true).apply { background = box(CHIP); setPadding(32, 16, 32, 16) }
        code = text("", 46f, ACCENT, true).apply { letterSpacing = 0.12f }
        val btn = Button(this).apply {
            text = "Paired devices"; isAllCaps = false; textSize = 20f; setTextColor(BG); setPadding(48, 16, 48, 16)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), box(ACCENT, FG)); addState(intArrayOf(), box(ACCENT))
            }
            setOnClickListener { devicesDialog() }
        }
        fun gap(px: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, px) }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(brand); addView(gap(28))
            addView(text("Scan with your iPhone to send a video here", 36f, FG, true)); addView(gap(16))
            addView(text("Or open this address in Safari, on the same Wi\u2011Fi:", 18f, MUTED)); addView(gap(12))
            addView(address); addView(gap(20))
            addView(text("Pairing code", 16f, MUTED)); addView(code); addView(gap(20)); addView(btn)
        }
        qr = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(440, 440).apply { marginStart = 64 }; setBackgroundColor(Color.WHITE); setPadding(20, 20, 20, 20) }
        val main = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, 0, 1f); addView(left); addView(qr) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(96, 48, 96, 32); setBackgroundColor(BG)
            addView(main); addView(text("Made by Looney \u00b7 looney.is-a.dev", 14f, MUTED))
        })
        // Many TV firmwares have no such settings screen, so never let this crash the app.
        try {
            if (!Settings.canDrawOverlays(this))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {}
        try { startForegroundService(Intent(this, ReceiverService::class.java)) }
        catch (e: Exception) { address.text = "Could not start the receiver: ${e.message}" }
    }
    override fun onResume() { super.onResume(); tick() }
    override fun onPause() { super.onPause(); h.removeCallbacksAndMessages(null) }

    private fun ip(): String? = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
    } catch (e: Exception) { null }

    private fun tick() {
        val url = ip()?.let { "http://$it:$PORT" }
        address.text = url ?: "Connect this TV to Wi\u2011Fi"
        code.text = ReceiverService.code.chunked(3).joinToString(" ")
        if (url != null && url != shown) { shown = url; qr.setImageBitmap(qrBitmap(url)) }
        h.postDelayed({ tick() }, 1000)
    }

    private fun devicesDialog() {
        val d = Store.devices(this); val keys = d.keys().asSequence().toList()
        if (keys.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Paired devices").setMessage("No phones are paired yet.").setPositiveButton("OK", null).show(); return
        }
        val names = keys.map { d.getJSONObject(it).getString("name") }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Paired devices: select one to remove")
            .setItems(names) { _, i -> Store.removeDevice(this, keys[i]); Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("Close", null).show()
    }

    private fun qrBitmap(text: String): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 400, 400)
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.RGB_565)
        for (x in 0 until 400) for (y in 0 until 400) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }
}
