package dev.linktotv

import android.app.*
import android.content.*
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.*
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import android.webkit.MimeTypeMap
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

/** Paired phones, history and favourites, kept on the TV. */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("linktotv", 0)
    fun devices(c: Context) = JSONObject(p(c).getString("devices", "{}")!!)
    private fun saveDevices(c: Context, d: JSONObject) = p(c).edit().putString("devices", d.toString()).apply()
    fun addDevice(c: Context, name: String): String {
        val t = UUID.randomUUID().toString(); val d = devices(c)
        val day = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date())
        d.put(t, JSONObject().put("name", "$name, paired $day")); saveDevices(c, d); return t
    }
    fun valid(c: Context, t: String?) = t != null && devices(c).has(t)
    fun removeDevice(c: Context, t: String) { val d = devices(c); d.remove(t); saveDevices(c, d) }
    fun library(c: Context) = JSONObject(p(c).getString("lib", null) ?: """{"history":[],"favs":[]}""")
    private fun saveLib(c: Context, l: JSONObject) = p(c).edit().putString("lib", l.toString()).apply()
    fun addHistory(c: Context, url: String) {
        val l = library(c); val old = l.getJSONArray("history")
        val n = JSONArray().put(JSONObject().put("url", url).put("t", System.currentTimeMillis()))
        for (i in 0 until old.length()) { val o = old.getJSONObject(i); if (o.getString("url") != url && n.length() < 20) n.put(o) }
        saveLib(c, l.put("history", n))
    }
    fun queue(c: Context): JSONArray = library(c).optJSONArray("queue") ?: JSONArray()
    private fun setQueue(c: Context, a: JSONArray) = saveLib(c, library(c).put("queue", a))
    fun queueAdd(c: Context, url: String) = setQueue(c, queue(c).put(url))
    fun queueClear(c: Context) = setQueue(c, JSONArray())
    fun queueRemove(c: Context, url: String) {
        val q = queue(c); val n = JSONArray(); var done = false
        for (i in 0 until q.length()) if (!done && q.getString(i) == url) done = true else n.put(q.getString(i))
        setQueue(c, n)
    }
    fun toggleFav(c: Context, url: String) {
        val l = library(c); val f = l.getJSONArray("favs"); val n = JSONArray(); var found = false
        for (i in 0 until f.length()) if (f.getString(i) == url) found = true else n.put(f.getString(i))
        if (!found) n.put(url); saveLib(c, l.put("favs", n))
    }
}

class Server(private val c: Context) : NanoHTTPD(PORT) {
    private var fails = 0
    private fun resp(code: Int, body: String) = newFixedLengthResponse(
        when (code) { 200 -> Response.Status.OK; 400 -> Response.Status.BAD_REQUEST; 401 -> Response.Status.UNAUTHORIZED
            403 -> Response.Status.FORBIDDEN; 405 -> Response.Status.METHOD_NOT_ALLOWED; else -> Response.Status.NOT_FOUND },
        "application/json", body)
    private fun deviceName(ua: String?) = when {
        ua == null -> "Browser"; ua.contains("iPhone") -> "iPhone"; ua.contains("iPad") -> "iPad"
        ua.contains("Android") -> "Android"; else -> "Browser" }
    private fun uploads() = File(c.cacheDir, "uploads").apply { mkdirs() }

    override fun serve(s: IHTTPSession): Response {
        val tok = s.headers["x-token"]; val get = s.method == Method.GET
        if (get && s.uri == "/") return newFixedLengthResponse(Response.Status.OK, "text/html", PAGE)
        if (s.uri == "/info") return resp(200, """{"app":"linktotv"}""")
        if (get && s.uri == "/library")
            return if (Store.valid(c, tok)) resp(200, Store.library(c).toString()) else resp(401, "{}")
        if (get && s.uri == "/queue.m3u") {
            val q = Store.queue(c)
            return newFixedLengthResponse(Response.Status.OK, "audio/x-mpegurl",
                "#EXTM3U\n" + (0 until q.length()).joinToString("\n") { q.getString(it) } + "\n")
        }
        if (get && s.uri.startsWith("/files/")) {
            val id = s.uri.removePrefix("/files/")
            val f = File(uploads(), id)
            if (!Regex("^[A-Za-z0-9]{8}(\\.[a-z0-9]{1,5})?$").matches(id) || !f.isFile) return resp(404, "{}")
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension) ?: "application/octet-stream"
            return newFixedLengthResponse(Response.Status.OK, mime, f.inputStream(), f.length())
        }
        if (s.method != Method.POST) return resp(405, "{}")
        val files = HashMap<String, String>()
        try { s.parseBody(files) } catch (e: Exception) { return resp(400, "{}") }
        val p = s.parms; val u = p["url"] ?: ""
        val okUrl = Regex("^https?://\\S+$").matches(u)
        val authed = Store.valid(c, tok)
        return when (s.uri) {
            "/pair" -> if (p["code"] == ReceiverService.code) {
                val t = Store.addDevice(c, deviceName(s.headers["user-agent"]))
                ReceiverService.code = ReceiverService.newCode(); fails = 0
                resp(200, JSONObject().put("token", t).toString())
            } else {
                if (++fails >= 5) { ReceiverService.code = ReceiverService.newCode(); fails = 0 }
                resp(403, "{}")
            }
            "/play", "/fav", "/queue/add", "/queue/remove" -> when {
                !authed -> resp(401, "{}")
                !okUrl -> resp(400, "{}")
                else -> {
                    when (s.uri) {
                        "/play" -> { Store.addHistory(c, u); Handler(Looper.getMainLooper()).post { open(u) } }
                        "/fav" -> Store.toggleFav(c, u)
                        "/queue/add" -> Store.queueAdd(c, u)
                        else -> Store.queueRemove(c, u)
                    }
                    resp(200, "{}")
                }
            }
            "/queue/clear" -> if (!authed) resp(401, "{}") else { Store.queueClear(c); resp(200, "{}") }
            "/queue/play" -> when {
                !authed -> resp(401, "{}")
                Store.queue(c).length() == 0 -> resp(400, "{}")
                else -> { Handler(Looper.getMainLooper()).post { open("http://127.0.0.1:$PORT/queue.m3u", null) }; resp(200, "{}") }
            }
            "/upload" -> {
                val tmp = files["file"]; val orig = p["file"]
                if (!authed) resp(401, "{}") else if (tmp == null || orig == null) resp(400, "{}") else {
                    val ext = orig.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(5)
                    val id = UUID.randomUUID().toString().replace("-", "").take(8) + (if (ext.isEmpty()) "" else ".$ext")
                    File(tmp).copyTo(File(uploads(), id), true)
                    uploads().listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/*"
                    Handler(Looper.getMainLooper()).post { open("http://127.0.0.1:$PORT/files/$id", mime) }
                    resp(200, "{}")
                }
            }
            else -> resp(404, "{}")
        }
    }

    /** Hands the link to VLC if installed (images go to the default viewer), otherwise to any app that can play it. */
    private fun open(url: String, type: String? = "video/*") {
        val i = Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (type == null) i.data = Uri.parse(url) else i.setDataAndType(Uri.parse(url), type)
        if (type?.startsWith("image/") != true)
            try { c.packageManager.getPackageInfo("org.videolan.vlc", 0); i.setPackage("org.videolan.vlc") } catch (e: Exception) {}
        try { c.startActivity(i) } catch (e: ActivityNotFoundException) {}
    }
}
