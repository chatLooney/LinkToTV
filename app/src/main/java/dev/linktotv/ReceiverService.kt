package dev.linktotv

import android.app.*
import android.content.*
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.*
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.util.UUID

const val SERVICE_TYPE = "_linktotv._tcp."
const val PORT = 8765

/** Runs on the TV: advertises itself on the LAN and accepts paired "play this link" requests. */
class ReceiverService : Service() {
    companion object {
        fun newCode() = (100000..999999).random().toString()
        @Volatile var code = newCode()
    }
    private var server: Server? = null
    private var nsd: NsdManager? = null
    private var reg: NsdManager.RegistrationListener? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (server == null) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel("rx", "Receiver", NotificationManager.IMPORTANCE_LOW))
            startForeground(1, Notification.Builder(this, "rx").setContentTitle("Link to TV is ready")
                .setSmallIcon(android.R.drawable.ic_media_play).build())
            try { server = Server(this).also { it.start() } } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
            nsd = getSystemService(NsdManager::class.java)
            reg = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(s: NsdServiceInfo) {}
                override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) {}
                override fun onServiceUnregistered(s: NsdServiceInfo) {}
                override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
            }
            try {
                nsd!!.registerService(NsdServiceInfo().apply {
                    serviceName = Build.MODEL; serviceType = SERVICE_TYPE; port = PORT
                }, NsdManager.PROTOCOL_DNS_SD, reg)
            } catch (e: Exception) {}
        }
        return START_STICKY
    }

    override fun onDestroy() { server?.stop(); reg?.let { nsd?.unregisterService(it) } }
}

class Server(private val c: Context) : NanoHTTPD(PORT) {
    private var fails = 0
    private fun resp(code: Int, body: String) = newFixedLengthResponse(
        when (code) { 200 -> Response.Status.OK; 400 -> Response.Status.BAD_REQUEST; 401 -> Response.Status.UNAUTHORIZED
            403 -> Response.Status.FORBIDDEN; 405 -> Response.Status.METHOD_NOT_ALLOWED; else -> Response.Status.NOT_FOUND },
        "application/json", body)

    override fun serve(s: IHTTPSession): Response {
        if (s.uri == "/" && s.method == Method.GET) return newFixedLengthResponse(Response.Status.OK, "text/html", PAGE)
        if (s.uri == "/info") return resp(200, """{"app":"linktotv"}""")
        if (s.method != Method.POST) return resp(405, "{}")
        try { s.parseBody(HashMap()) } catch (e: Exception) { return resp(400, "{}") }
        val p = s.parms
        val prefs = c.getSharedPreferences("tokens", 0)
        return when (s.uri) {
            "/pair" -> if (p["code"] == ReceiverService.code) {
                val t = UUID.randomUUID().toString()
                prefs.edit().putStringSet("t", (prefs.getStringSet("t", emptySet())!! + t)).apply()
                ReceiverService.code = ReceiverService.newCode(); fails = 0
                resp(200, JSONObject().put("token", t).toString())
            } else {
                if (++fails >= 5) { ReceiverService.code = ReceiverService.newCode(); fails = 0 }
                resp(403, "{}")
            }
            "/play" -> {
                val t = s.headers["x-token"]; val u = p["url"] ?: ""
                when {
                    t == null || !prefs.getStringSet("t", emptySet())!!.contains(t) -> resp(401, "{}")
                    !Regex("^https?://\\S+$").matches(u) -> resp(400, "{}")
                    else -> { Handler(Looper.getMainLooper()).post { open(u) }; resp(200, "{}") }
                }
            }
            else -> resp(404, "{}")
        }
    }

    /** Hands the link to VLC if installed, otherwise to any app that can play it. */
    private fun open(url: String) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { c.packageManager.getPackageInfo("org.videolan.vlc", 0); i.setPackage("org.videolan.vlc") } catch (e: Exception) {}
        try { c.startActivity(i) } catch (e: ActivityNotFoundException) {}
    }
}
