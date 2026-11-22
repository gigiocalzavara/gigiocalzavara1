package com.gigio.treinodopredio

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Abre o app web (assets/index.html) num WebView e expõe a ponte
 * `window.AndroidHealth`, que lê os dados do Health Connect.
 * O Samsung Health sincroniza passos, batimentos, sono, peso e exercícios
 * com o Health Connect; daqui eles vão para o app.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var pendingCallback: String? = null

    private val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    private val permissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            val cb = pendingCallback ?: return@registerForActivityResult
            pendingCallback = null
            if (granted.isEmpty()) reply(cb, error("denied")) else readAndReply(cb)
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0E1115")
        window.navigationBarColor = Color.parseColor("#161B21")

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0E1115"))
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme == "file") return false
                // links externos abrem no navegador
                try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: ActivityNotFoundException) {}
                return true
            }
        }
        web.addJavascriptInterface(Bridge(), "AndroidHealth")
        setContentView(web)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState == null) web.loadUrl("file:///android_asset/index.html")
        else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun sdkStatus(): String = when (HealthConnectClient.getSdkStatus(this)) {
        HealthConnectClient.SDK_AVAILABLE -> "ok"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update"
        else -> "unavailable"
    }

    inner class Bridge {
        @JavascriptInterface
        fun status(): String = sdkStatus()

        @JavascriptInterface
        fun sync(callbackId: String, askPermission: Boolean) {
            runOnUiThread { startSync(callbackId, askPermission) }
        }

        @JavascriptInterface
        fun openStore() {
            runOnUiThread {
                val pkg = "com.google.android.apps.healthdata"
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
                }
            }
        }
    }

    private fun startSync(cb: String, ask: Boolean) {
        val st = sdkStatus()
        if (st != "ok") { reply(cb, error(st)); return }
        lifecycleScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(this@MainActivity)
                val granted = client.permissionController.getGrantedPermissions()
                if (granted.intersect(permissions).isEmpty()) {
                    if (ask) {
                        pendingCallback = cb
                        permissionLauncher.launch(permissions)
                    } else reply(cb, error("noperm"))
                } else readAndReply(cb)
            } catch (e: Exception) {
                reply(cb, error("read"))
            }
        }
    }

    private fun readAndReply(cb: String) {
        lifecycleScope.launch {
            val json = try { readAll() } catch (e: Exception) { error("read") }
            reply(cb, json)
        }
    }

    private fun reply(cb: String, json: String) {
        runOnUiThread {
            web.evaluateJavascript(
                "window.__hc&&window.__hc(${JSONObject.quote(cb)},${JSONObject.quote(json)})", null
            )
        }
    }

    private fun error(code: String) = JSONObject().put("error", code).toString()

    private suspend fun <T : Record> readRecords(
        client: HealthConnectClient, type: KClass<T>, filter: TimeRangeFilter
    ): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val res = client.readRecords(
                ReadRecordsRequest(recordType = type, timeRangeFilter = filter, pageSize = 5000, pageToken = token)
            )
            out.addAll(res.records)
            token = res.pageToken
        } while (token != null && out.size < 80000)
        return out
    }

    /** Lê os últimos 60 dias e devolve no mesmo formato da importação de arquivos do app web. */
    private suspend fun readAll(): String {
        val client = HealthConnectClient.getOrCreate(this)
        val granted = client.permissionController.getGrantedPermissions()
        val zone = ZoneId.systemDefault()
        val endDay = LocalDate.now(zone).plusDays(1)
        val startDay = endDay.minusDays(DAYS)
        val localFilter = TimeRangeFilter.between(startDay.atStartOfDay(), endDay.atStartOfDay())
        val instantFilter = TimeRangeFilter.between(
            startDay.atStartOfDay(zone).toInstant(), endDay.atStartOfDay(zone).toInstant()
        )
        val days = JSONObject()
        fun day(d: String): JSONObject = days.optJSONObject(d) ?: JSONObject().also { days.put(d, it) }
        fun has(t: KClass<out Record>) = HealthPermission.getReadPermission(t) in granted
        var used = 0

        if (has(StepsRecord::class)) try {
            val res = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = localFilter,
                    timeRangeSlicer = Period.ofDays(1)
                )
            )
            for (r in res) {
                val n = r.result[StepsRecord.COUNT_TOTAL] ?: continue
                day(r.startTime.toLocalDate().toString()).put("steps", n)
            }
            used++
        } catch (_: Exception) {}

        if (has(HeartRateRecord::class)) try {
            val byDay = HashMap<String, MutableList<Long>>()
            for (rec in readRecords(client, HeartRateRecord::class, instantFilter)) for (s in rec.samples) {
                val d = s.time.atZone(zone).toLocalDate().toString()
                byDay.getOrPut(d) { mutableListOf() }.add(s.beatsPerMinute)
            }
            for ((d, list) in byDay) {
                list.sort()
                val o = day(d)
                o.put("hrRest", list[(list.size * 0.1).toInt()])
                o.put("hrAvg", list.average().toInt())
                o.put("hrMax", list.last())
            }
            if (byDay.isNotEmpty()) used++
        } catch (_: Exception) {}

        if (has(RestingHeartRateRecord::class)) try {
            val byDay = HashMap<String, MutableList<Long>>()
            for (rec in readRecords(client, RestingHeartRateRecord::class, instantFilter)) {
                val d = rec.time.atZone(zone).toLocalDate().toString()
                byDay.getOrPut(d) { mutableListOf() }.add(rec.beatsPerMinute)
            }
            for ((d, list) in byDay) { list.sort(); day(d).put("hrRest", list[list.size / 2]) }
            if (byDay.isNotEmpty()) used++
        } catch (_: Exception) {}

        if (has(SleepSessionRecord::class)) try {
            val byDay = HashMap<String, Double>()
            for (rec in readRecords(client, SleepSessionRecord::class, instantFilter)) {
                val h = Duration.between(rec.startTime, rec.endTime).toMinutes() / 60.0
                if (h <= 0 || h > 20) continue
                val d = rec.endTime.atZone(zone).toLocalDate().toString()
                byDay[d] = (byDay[d] ?: 0.0) + h
            }
            for ((d, h) in byDay) day(d).put("sleep", Math.round(h * 100) / 100.0)
            if (byDay.isNotEmpty()) used++
        } catch (_: Exception) {}

        val weights = JSONObject()
        if (has(WeightRecord::class)) try {
            for (rec in readRecords(client, WeightRecord::class, instantFilter)) {
                val d = rec.time.atZone(zone).toLocalDate().toString()
                weights.put(d, Math.round(rec.weight.inKilograms * 10) / 10.0)
            }
            if (weights.length() > 0) used++
        } catch (_: Exception) {}

        val exercises = JSONArray()
        if (has(ExerciseSessionRecord::class)) try {
            for (rec in readRecords(client, ExerciseSessionRecord::class, instantFilter)) {
                val min = Duration.between(rec.startTime, rec.endTime).toMinutes()
                if (min <= 0) continue
                exercises.put(
                    JSONObject()
                        .put("date", rec.startTime.atZone(zone).toLocalDate().toString())
                        .put("type", exerciseName(rec.exerciseType))
                        .put("min", min)
                        .put("kcal", 0)
                        .put("hr", 0)
                )
            }
            if (exercises.length() > 0) used++
        } catch (_: Exception) {}

        return JSONObject()
            .put("days", days)
            .put("ex", exercises)
            .put("wts", weights)
            .put("used", used)
            .put("files", 0)
            .toString()
    }

    private fun exerciseName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Caminhada"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Corrida"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Ciclismo"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Trilha"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Natação"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elíptico"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Musculação"
        else -> "Exercício"
    }

    companion object {
        private const val DAYS = 60L
    }
}
