package dev.linktotv

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface

/** TV screen: shows the web address, QR code and pairing code, and keeps the receiver running. */
class TvActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var tv: TextView
    private lateinit var qr: ImageView
    private var shown = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tv = TextView(this).apply { textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        qr = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(420, 420).apply { topMargin = 32 } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(80, 40, 80, 40)
            addView(tv); addView(qr)
        })
        window.decorView.setBackgroundColor(0xFF101820.toInt())
        // Needed so links can start VLC while this screen is in the background.
        // Many TV firmwares have no such settings screen, so never let this crash the app.
        try {
            if (!Settings.canDrawOverlays(this))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {}
        try { startForegroundService(Intent(this, ReceiverService::class.java)) }
        catch (e: Exception) { tv.text = "Could not start the receiver: ${e.message}" }
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
        if (url == null) tv.text = "Connect this TV to Wi-Fi."
        else {
            tv.text = "On your iPhone, scan the code or open Safari and go to\n$url\n\nPairing code: ${ReceiverService.code}"
            if (url != shown) { shown = url; qr.setImageBitmap(qrBitmap(url)) }
        }
        h.postDelayed({ tick() }, 1000)
    }

    private fun qrBitmap(text: String): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 420, 420)
        val bmp = Bitmap.createBitmap(420, 420, Bitmap.Config.RGB_565)
        for (x in 0 until 420) for (y in 0 until 420) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }
}
