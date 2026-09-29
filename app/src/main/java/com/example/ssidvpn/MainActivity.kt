package com.example.ssidvpn

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import de.blinkt.openvpn.api.IOpenVPNAPIService
import de.blinkt.openvpn.api.IOpenVPNStatusCallback
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private companion object {
        const val RED = 0
        const val YELLOW = 1
        const val GREEN = 2
        val IP_URLS = listOf("https://api.ipify.org", "https://icanhazip.com")
    }

    private lateinit var adapter: RuleAdapter
    private var pendingAction: ((IOpenVPNAPIService) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var shownStatus = -1
    private val fetching = AtomicBoolean(false)

    // постоянное подключение к OpenVPN for Android для получения статуса
    private var statusConn: ServiceConnection? = null
    private var statusApi: IOpenVPNAPIService? = null
    private val statusCb = object : IOpenVPNStatusCallback.Stub() {
        override fun newStatus(uuid: String?, state: String?, message: String?, level: String?) {
            runOnUiThread { setVpnStatus(mapStatus(state, level)) }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (statusApi == null) {
                // нет данных от OpenVPN for Android - ориентируемся по наличию VPN-сети
                setVpnStatus(if (Net.hasVpn(this@MainActivity)) GREEN else RED)
            }
            fetchIp()
            handler.postDelayed(this, 10_000)
        }
    }

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importProfile(uri)
        }

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val p = pendingAction
            pendingAction = null
            if (r.resultCode == RESULT_OK && p != null) withApi(p) else toast(R.string.need_auth)
        }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        findViewById<View>(R.id.btnPick).setOnClickListener { pickFile.launch(arrayOf("*/*")) }
        findViewById<View>(R.id.cardInfo).setOnClickListener { fetchIp() }

        // принудительный режим
        val mode = findViewById<MaterialButtonToggleGroup>(R.id.modeGroup)
        mode.check(
            when (Prefs.mode(this)) {
                1 -> R.id.btnForceOn
                2 -> R.id.btnForceOff
                else -> R.id.btnAuto
            }
        )
        mode.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                Prefs.setMode(
                    this,
                    when (id) {
                        R.id.btnForceOn -> 1
                        R.id.btnForceOff -> 2
                        else -> 0
                    }
                )
                applyNow()
            }
        }

        val mobile = findViewById<MaterialButtonToggleGroup>(R.id.mobileGroup)
        mobile.check(if (Prefs.mobileConnect(this)) R.id.btnMobOn else R.id.btnMobOff)
        mobile.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                Prefs.setMobileConnect(this, id == R.id.btnMobOn)
                Reconciler.run(this, false)
            }
        }

        adapter = RuleAdapter(
            onChange = { ssid, connect ->
                Prefs.setRule(this, ssid, connect)
                Reconciler.run(this, false)
            },
            onDelete = { ssid ->
                Prefs.removeRule(this, ssid)
                refreshRules()
                Reconciler.run(this, false)
            }
        )
        val rv = findViewById<RecyclerView>(R.id.rules)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { showAddDialog() }

        findViewById<View>(R.id.btnPerm).setOnClickListener {
            when {
                !locOk() -> permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                !bgOk() ->
                    if (Build.VERSION.SDK_INT >= 30) {
                        toast(R.string.perm_bg)
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", packageName, null))
                        )
                    } else {
                        permLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                else -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }

        NetMonitor.register(this)
        if (savedInstanceState == null) Reconciler.run(this, true)
    }

    override fun onStart() {
        super.onStart()
        setVpnStatus(if (Net.hasVpn(this)) GREEN else RED)
        bindStatus()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        unbindStatus()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        refreshRules()
    }

    // ---------- статус VPN и внешний IP ----------

    private fun mapStatus(state: String?, level: String?): Int {
        val s = state ?: ""
        val l = level ?: ""
        return when {
            s == "CONNECTED" || l == "LEVEL_CONNECTED" -> GREEN
            l == "LEVEL_NOTCONNECTED" || l == "LEVEL_AUTH_FAILED" ||
                    s == "NOPROCESS" || s == "EXITING" || s == "AUTH_FAILED" -> RED
            else -> YELLOW
        }
    }

    private fun setVpnStatus(status: Int) {
        if (status == shownStatus) return
        shownStatus = status
        val (color, text) = when (status) {
            GREEN -> 0xFF34A853.toInt() to R.string.st_on
            YELLOW -> 0xFFF9AB00.toInt() to R.string.st_wait
            else -> 0xFFD93025.toInt() to R.string.st_off
        }
        val dot = findViewById<View>(R.id.dot)
        (dot.background.mutate() as GradientDrawable).setColor(color)
        findViewById<TextView>(R.id.tvStatus).setText(text)
        // после смены состояния маршруты меняются - обновим IP чуть позже
        handler.postDelayed({ fetchIp() }, 2500)
    }

    private fun bindStatus() {
        if (statusConn != null || !VpnController.isInstalled(this)) return
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                try {
                    val api = IOpenVPNAPIService.Stub.asInterface(binder)
                    api.registerStatusCallback(statusCb)
                    statusApi = api
                } catch (_: Exception) {
                    statusApi = null
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                statusApi = null
            }
        }
        val ok = try {
            applicationContext.bindService(
                Intent("de.blinkt.openvpn.api.IOpenVPNAPIService").setPackage(VpnController.PKG),
                conn, BIND_AUTO_CREATE
            )
        } catch (e: Exception) {
            false
        }
        if (ok) statusConn = conn
    }

    private fun unbindStatus() {
        try {
            statusApi?.unregisterStatusCallback(statusCb)
        } catch (_: Exception) {
        }
        statusApi = null
        statusConn?.let {
            try {
                applicationContext.unbindService(it)
            } catch (_: Exception) {
            }
        }
        statusConn = null
    }

    private fun fetchIp() {
        if (!fetching.compareAndSet(false, true)) return
        Thread {
            var ip: String? = null
            for (u in IP_URLS) {
                ip = queryIp(u)
                if (ip != null) break
            }
            fetching.set(false)
            runOnUiThread {
                findViewById<TextView>(R.id.tvIp).text = ip ?: getString(R.string.ip_none)
            }
        }.start()
    }

    private fun queryIp(url: String): String? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 5000
            c.readTimeout = 5000
            val s = c.inputStream.bufferedReader().use { it.readText() }.trim()
            if (s.length in 3..45 && s.matches(Regex("[0-9a-fA-F:.]+"))) s else null
        } catch (e: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    /** Применить принудительный режим сразу (без лишнего переподключения, если уже в нужном состоянии). */
    private fun applyNow() {
        val want = Net.desired(this)
        if ((want && shownStatus == GREEN) || (!want && shownStatus == RED)) {
            Prefs.setLast(this, if (want) 1 else 0)
        } else {
            Prefs.setLast(this, -1)
            Reconciler.run(this, false)
        }
    }

    // ---------- состояние экрана ----------

    private fun locOk() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun bgOk() = Build.VERSION.SDK_INT < 29 ||
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun locEnabled() =
        getSystemService(LocationManager::class.java).isLocationEnabled

    private fun refresh() {
        findViewById<TextView>(R.id.tvProfile).text =
            Prefs.profileName(this) ?: getString(R.string.no_profile)
        findViewById<TextView>(R.id.tvEngine).text =
            getString(if (VpnController.isInstalled(this)) R.string.engine_ok else R.string.engine_missing)

        val msg = when {
            !locOk() -> R.string.perm_loc
            !bgOk() -> R.string.perm_bg
            !locEnabled() -> R.string.perm_loc_on
            else -> 0
        }
        val card = findViewById<View>(R.id.cardPerm)
        if (msg == 0) {
            card.visibility = View.GONE
        } else {
            card.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvPerm).setText(msg)
        }
    }

    private fun refreshRules() {
        val list = Prefs.rules(this).toList().sortedBy { it.first.lowercase() }
        adapter.submit(list)
        findViewById<View>(R.id.tvEmpty).visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_LONG).show()

    // ---------- добавление сети ----------

    @Suppress("DEPRECATION")
    private fun suggestions(): List<String> {
        val out = LinkedHashSet<String>()
        Net.currentSsid(this)?.let { out.add(it) }
        try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            wm.configuredNetworks?.forEach { out.add(it.SSID.removeSurrounding("\"")) }
        } catch (_: Exception) {
        }
        return out.filter { it.isNotEmpty() && it != WifiManager.UNKNOWN_SSID }
    }

    private fun showAddDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_add, null)
        val input = v.findViewById<MaterialAutoCompleteTextView>(R.id.etSsid)
        val existing = Prefs.rules(this).keys
        input.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, suggestions().filter { it !in existing })
        )
        input.threshold = 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_network)
            .setView(v)
            .setPositiveButton(R.string.add) { _, _ ->
                val s = input.text.toString().trim()
                if (s.isNotEmpty()) {
                    // новая сеть по умолчанию "доверенная": VPN не подключается
                    Prefs.setRule(this, s, false)
                    refreshRules()
                    Reconciler.run(this, false)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- работа с OpenVPN for Android ----------

    /** Выполняет [action] после получения всех разрешений у OpenVPN for Android. */
    private fun withApi(action: (IOpenVPNAPIService) -> Unit) {
        VpnController.withService(this, { api ->
            val i1 = api.prepare(packageName)
            if (i1 != null) {
                pendingAction = action
                authLauncher.launch(i1)
                return@withService
            }
            val i2 = api.prepareVPNService()
            if (i2 != null) {
                pendingAction = action
                authLauncher.launch(i2)
                return@withService
            }
            action(api)
        }) { ok -> if (!ok) toast(R.string.engine_fail) }
    }

    private fun importProfile(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
        if (text.isNullOrBlank() || !text.contains("remote")) {
            toast(R.string.bad_file)
            return
        }
        if (!text.contains("<ca>")) toast(R.string.warn_inline)

        var name = "SsidVpn"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) name = c.getString(i) ?: name
            }
        }
        if (name.endsWith(".ovpn", ignoreCase = true)) name = name.dropLast(5)
        val profileName = name

        withApi { api ->
            Prefs.profileUuid(this)?.let {
                try {
                    api.removeProfile(it)
                } catch (_: Exception) {
                }
            }
            val p = api.addNewVPNProfile(profileName, false, text)
            if (p != null) {
                Prefs.setProfile(this, p.mUUID, profileName)
                Prefs.setLast(this, -1)
                window.decorView.post {
                    refresh()
                    Reconciler.run(this, false)
                }
            } else {
                toast(R.string.bad_file)
            }
        }
    }
}
