package dev.linktotv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.text.InputType
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

data class Tv(val name: String, val host: String, val port: Int)

/** Phone screen: finds TVs, pairs once, then sends links (also works from the Share menu). */
class PhoneActivity : Activity() {
    private val tvs = mutableListOf<Tv>()
    private lateinit var spin: Spinner
    private lateinit var link: EditText
    private lateinit var status: TextView
    private lateinit var nsd: NsdManager
    private val queue = ArrayDeque<NsdServiceInfo>()
    @Volatile private var busy = false

    private val disc = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(i: NsdServiceInfo) { synchronized(queue) { queue.add(i) }; next() }
        override fun onServiceLost(i: NsdServiceInfo) { runOnUiThread { tvs.removeAll { it.name == i.serviceName }; refresh() } }
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        link = EditText(this).apply { hint = "Paste a video link"; inputType = InputType.TYPE_TEXT_VARIATION_URI }
        spin = Spinner(this); status = TextView(this).apply { text = "Looking for TVs on your Wi-Fi…" }
        val pair = Button(this).apply { text = "Pair with this TV"; setOnClickListener { pairDialog() } }
        val send = Button(this).apply { text = "Send to TV"; setOnClickListener { send() } }
        listOf(TextView(this).apply { text = "Link to TV"; textSize = 26f }, link, spin, pair, send, status).forEach { col.addView(it) }
        setContentView(col)
        handleShare(intent)
        nsd = getSystemService(NsdManager::class.java)
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, disc)
    }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); handleShare(i) }
    override fun onDestroy() { super.onDestroy(); try { nsd.stopServiceDiscovery(disc) } catch (e: Exception) {} }

    private fun handleShare(i: Intent) {
        val t = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        Regex("https?://\\S+").find(t)?.let { link.setText(it.value) }
    }

    private fun next() {
        val i = synchronized(queue) { if (busy || queue.isEmpty()) return; busy = true; queue.removeFirst() }
        nsd.resolveService(i, object : NsdManager.ResolveListener {
            override fun onResolveFailed(s: NsdServiceInfo, e: Int) { busy = false; next() }
            override fun onServiceResolved(s: NsdServiceInfo) {
                busy = false
                runOnUiThread { if (tvs.none { it.name == s.serviceName }) { tvs.add(Tv(s.serviceName, s.host.hostAddress!!, s.port)); refresh() } }
                next()
            }
        })
    }

    private fun refresh() {
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tvs.map { it.name })
        status.text = if (tvs.isEmpty()) "No TVs found. Open Link to TV on your TV, on the same Wi-Fi." else "${tvs.size} TV(s) found."
    }
    private fun selected(): Tv? = tvs.getOrNull(spin.selectedItemPosition)
    private fun say(s: String) = runOnUiThread { status.text = s }

    private fun pairDialog() {
        val tv = selected() ?: return say("Pick a TV first.")
        val code = EditText(this).apply { hint = "Code shown on the TV"; inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle("Pair with ${tv.name}").setView(code)
            .setPositiveButton("Pair") { _, _ ->
                post(tv, "/pair", "code=" + code.text, null) { c, body ->
                    if (c == 200) {
                        getSharedPreferences("tv", 0).edit().putString(tv.name, JSONObject(body).getString("token")).apply()
                        say("Paired with ${tv.name}.")
                    } else say("Wrong code. Check the TV screen and try again.")
                }
            }.show()
    }

    private fun send() {
        val tv = selected() ?: return say("Pick a TV first.")
        val token = getSharedPreferences("tv", 0).getString(tv.name, null) ?: return say("Pair with this TV first.")
        val url = link.text.toString().trim()
        if (!Regex("^https?://\\S+$").matches(url)) return say("Enter a link starting with http:// or https://")
        post(tv, "/play", "url=" + URLEncoder.encode(url, "UTF-8"), token) { c, _ ->
            when (c) {
                200 -> say("Sent. It should start on the TV.")
                401 -> { getSharedPreferences("tv", 0).edit().remove(tv.name).apply(); say("TV forgot this phone. Pair again.") }
                else -> say("Could not reach the TV ($c).")
            }
        }
    }

    private fun post(tv: Tv, path: String, body: String, token: String?, cb: (Int, String) -> Unit) = thread {
        try {
            val c = URL("http://${tv.host}:${tv.port}$path").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 4000; c.readTimeout = 4000
            token?.let { c.setRequestProperty("X-Token", it) }
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val txt = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
            cb(code, txt)
        } catch (e: Exception) { cb(-1, e.message ?: "error") }
    }
}
