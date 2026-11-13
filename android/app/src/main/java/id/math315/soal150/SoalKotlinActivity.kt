package id.math315.soal150

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class SoalKotlinActivity : AppCompatActivity() {

    // ── Data ──────────────────────────────────────────────────────────────────

    data class Rumus(
        val nomor: Int,
        val elemen: String,
        val subelemen: String,
        val nama: String,
        val rumus: String,
        val keterangan: String,
        val soal: String,
        val langkah: List<Pair<String, String>>,
        val jawaban: String
    )

    // ── State ─────────────────────────────────────────────────────────────────

    private val formulas = mutableListOf<Rumus>()
    private var uid = ""
    private var idToken = ""
    private var isPremium = false
    private var premiumUntil = 0L

    private val colorNavy = Color.rgb(16, 43, 80)
    private val colorBlue = Color.rgb(35, 105, 190)
    private val colorBg   = Color.rgb(241, 245, 251)

    private lateinit var root: LinearLayout

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uid          = intent.getStringExtra("uid") ?: ""
        idToken      = intent.getStringExtra("idToken") ?: ""
        isPremium    = intent.getBooleanExtra("isPremium", false)
        premiumUntil = intent.getLongExtra("premiumUntil", 0L)
        loadFormulas()
        showFormulaList()

        if (isPaymentReturn(intent)) {
            refreshPremiumFromFirebase()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (isPaymentReturn(intent)) {
            refreshPremiumFromFirebase()
        }
    }

    private fun isPaymentReturn(intent: Intent?): Boolean {
        val data = intent?.data
        return data?.scheme == "math315" &&
            data.host == "payment-success"
    }

    // Periksa ulang hak Premium dari Firebase setelah kembali dari pembayaran.
    private fun refreshPremiumFromFirebase() {
        if (uid.isBlank() || idToken.isBlank()) {
            Toast.makeText(this, "Silakan login kembali untuk memeriksa Premium.",
                Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Memeriksa status pembayaran...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val token = java.net.URLEncoder.encode(idToken, "UTF-8")
                val user = java.net.URLEncoder.encode(uid, "UTF-8")
                val url = "https://math314-app-default-rtdb.firebaseio.com/users/$user.json?auth=$token"

                val conn = java.net.URL(url).openConnection()
                    as java.net.HttpURLConnection

                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream
                    else conn.errorStream

                val body = stream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() } ?: "null"

                conn.disconnect()

                if (code !in 200..299) {
                    throw Exception("HTTP $code")
                }

                val data = if (body.trim() == "null") JSONObject()
                    else JSONObject(body)

                val until = maxOf(
                    data.optLong("premium_until", 0L),
                    data.optLong("premiumExpired", 0L)
                )

                val premiumFlag =
                    data.optBoolean("isPremium") ||
                    data.optBoolean("is_premium") ||
                    data.optBoolean("tka_premium")

                val active = until > System.currentTimeMillis() ||
                    (premiumFlag && until <= 0L)

                runOnUiThread {
                    isPremium = active
                    premiumUntil = until
                    showFormulaList()

                    Toast.makeText(
                        this,
                        if (premiumActive()) "Premium aktif. Rumus telah terbuka."
                        else "Premium belum aktif. Pembayaran masih perlu diverifikasi.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Gagal memeriksa Premium: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    // ── Premium ───────────────────────────────────────────────────────────────

    private fun premiumActive(): Boolean {
        val onlineActive =
            uid.isNotBlank() &&
            isPremium &&
            (premiumUntil <= 0L || premiumUntil > System.currentTimeMillis())

        val offlineActive =
            PremiumOffline.isActive(this, uid)

        return onlineActive || offlineActive
    }

    // ── Load JSON ─────────────────────────────────────────────────────────────

    private fun loadFormulas() {
        try {
            val json = assets.open("public/data/rumus-smp.json")
                .bufferedReader().use { it.readText() }
            val array = JSONObject(json).getJSONArray("rumus")
            for (i in 0 until array.length()) {
                val item   = array.getJSONObject(i)
                val contoh = item.optJSONObject("contoh")
                val langkahList = mutableListOf<Pair<String, String>>()
                contoh?.optJSONArray("langkah")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val step = arr.getJSONObject(j)
                        langkahList.add(step.optString("teks") to step.optString("tex"))
                    }
                }
                formulas.add(Rumus(
                    nomor      = item.optInt("nomor"),
                    elemen     = item.optString("elemen"),
                    subelemen  = item.optString("subelemen"),
                    nama       = item.optString("nama"),
                    rumus      = item.optString("rumus"),
                    keterangan = item.optString("keterangan"),
                    soal       = contoh?.optString("soal") ?: "",
                    langkah    = langkahList,
                    jawaban    = contoh?.optString("jawaban") ?: ""
                ))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ── Root view ─────────────────────────────────────────────────────────────

    private fun buildRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(colorBg)
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }.also { root = it }

    // =========================================================================
    // HALAMAN 1: Daftar rumus
    // Gunakan hanya TextView biasa agar 155 item tampil cepat tanpa lag.
    // =========================================================================

    private fun showFormulaList() {
        val rootView = buildRoot()
        val scroll   = ScrollView(this)
        val content  = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        txt(content, "MATH315\n155 RUMUS MATEMATIKA SMP", 23f, colorNavy, true,  Gravity.CENTER)
        txt(content, "Latihan Matematika Native Kotlin",  14f, Color.DKGRAY, false, Gravity.CENTER)

        val status = if (premiumActive()) "✓ PREMIUM AKTIF" else "AKSES GRATIS: RUMUS 1–5"
        txt(content, "$status  |  ${formulas.size} rumus", 14f, colorBlue, true, Gravity.CENTER)

        // Tombol pembelian Premium melalui Faspay
        val purchaseButton = Button(this).apply {
            text = "BELI / PERPANJANG PREMIUM – Rp10.000"
            isEnabled = uid.isNotBlank() && idToken.isNotBlank()
            setOnClickListener { purchasePremium(this) }
        }
        content.addView(purchaseButton)

        // Tombol aktivasi lisensi Premium Offline
        val offlineButton = Button(this).apply {
            text = if (PremiumOffline.isActive(this@SoalKotlinActivity, uid))
                "PERBARUI LISENSI OFFLINE"
            else
                "AKTIFKAN OFFLINE"

            isEnabled = uid.isNotBlank() && idToken.isNotBlank()
            setOnClickListener { activateOffline(this) }
        }
        content.addView(offlineButton)

        if (uid.isBlank() || idToken.isBlank()) {
            txt(content, "Silakan login kembali untuk mengaktifkan Premium Offline.",
                12f, Color.DKGRAY)
        }


        if (formulas.isEmpty()) {
            txt(content, "Data rumus belum berhasil dimuat.", 16f, Color.RED, true)
        }

        formulas.forEach { f ->
            val unlocked = f.nomor <= 5 || premiumActive()

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setBackgroundColor(Color.WHITE)
            }

            // Nomor + nama
            txt(card, "${f.nomor}. ${f.nama}", 16f, colorNavy, true)
            // Elemen
            txt(card, "${f.elemen} • ${f.subelemen}", 12f, Color.DKGRAY)
            // Pratinjau rumus — cukup teks biasa agar cepat
            // Konversi LaTeX sederhana ke Unicode supaya terbaca
            val preview = KotlinFormulaRenderer.latexToUnicode(f.rumus)
            txt(card, preview, 13f, colorBlue)

            val btn = Button(this).apply {
                text = if (unlocked) "BUKA RUMUS" else "🔒 PREMIUM – BUKA"
                setOnClickListener {
                    if (unlocked) showFormulaDetail(f)
                    else purchasePremium()
                }
            }
            card.addView(btn)
            content.addView(card, cardParams())
            content.addView(spacer(6))
        }

        val back = Button(this).apply {
            text = "KEMBALI KE MENU"
            setOnClickListener { finish() }
        }
        content.addView(back)

        scroll.addView(content)
        rootView.addView(scroll, fullHeightParams())
        setContentView(rootView)
    }

    // Aktivasi lisensi Premium Offline melalui server Math315
    private fun activateOffline(button: Button) {
        if (uid.isBlank() || idToken.isBlank()) {
            Toast.makeText(this, "Silakan login kembali.", Toast.LENGTH_LONG).show()
            return
        }

        button.isEnabled = false
        button.text = "MENGAKTIFKAN..."

        Thread {
            var berhasil = false
            var pesan = "Aktivasi gagal."

            try {
                val conn = java.net.URL(
                    "https://math315.vercel.app/api/offline-license"
                ).openConnection() as java.net.HttpURLConnection

                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $idToken")
                conn.setRequestProperty("Content-Type", "application/json")

                conn.outputStream.use {
                    it.write("{}".toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                val body = stream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() } ?: "{}"

                val result = JSONObject(body)

                if (code == 200 && result.optBoolean("success")) {
                    val license = result.optString("license", "")
                    val until = result.optLong("expiresAt", 0L)

                    if (license.isNotBlank() &&
                        PremiumOffline.save(this, uid, until, license)) {
                        berhasil = true
                    } else {
                        pesan = "Verifikasi lisensi gagal. Periksa kunci publik RSA."
                    }
                } else {
                    pesan = result.optString("error", "Server menolak aktivasi (HTTP $code).")
                }

                conn.disconnect()

            } catch (e: Exception) {
                pesan = "Koneksi gagal: ${e.message ?: "periksa internet"}"
            }

            runOnUiThread {
                button.isEnabled = true

                if (berhasil) {
                    Toast.makeText(
                        this,
                        "Premium Offline berhasil diaktifkan!",
                        Toast.LENGTH_LONG
                    ).show()
                    showFormulaList()
                } else {
                    button.text = "AKTIFKAN OFFLINE"
                    Toast.makeText(this, pesan, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // Pembelian Premium 1 bulan melalui API Faspay
    private fun purchasePremium(button: Button? = null) {
        if (uid.isBlank() || idToken.isBlank()) {
            Toast.makeText(this, "Silakan login kembali.", Toast.LENGTH_LONG).show()
            return
        }

        button?.isEnabled = false
        button?.text = "MEMBUAT TRANSAKSI..."

        Thread {
            var paymentUrl = ""
            var pesan = "Gagal membuat transaksi."

            try {
                val conn = java.net.URL(
                    "https://math315.vercel.app/api/create-transaction"
                ).openConnection() as java.net.HttpURLConnection

                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 20000
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $idToken")
                conn.setRequestProperty("Content-Type", "application/json")

                val payload = JSONObject()
                    .put("uid", uid)
                    .put("product_id", "premium_1bulan")
                    .put("amount", 10000)
                    .put("source", "app")

                conn.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                val body = stream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() } ?: "{}"

                val result = JSONObject(body)

                if (code in 200..299 && result.optBoolean("success")) {
                    paymentUrl = result.optString("payment_url", "")
                    if (paymentUrl.isBlank()) {
                        pesan = "URL pembayaran tidak diberikan oleh Faspay."
                    }
                } else {
                    pesan = result.optString(
                        "message",
                        "Transaksi gagal dibuat (HTTP $code)."
                    )
                }

                conn.disconnect()

            } catch (e: Exception) {
                pesan = "Koneksi gagal: ${e.message ?: "periksa internet"}"
            }

            runOnUiThread {
                button?.isEnabled = true
                button?.text = "BELI / PERPANJANG PREMIUM – Rp10.000"

                if (paymentUrl.isNotBlank()) {
                    try {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))
                        )
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Tidak dapat membuka halaman pembayaran.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(this, pesan, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // =========================================================================
    // HALAMAN 2: Detail rumus — gunakan KotlinFormulaRenderer untuk math
    // =========================================================================

    private fun showFormulaDetail(f: Rumus) {
        val rootView = buildRoot()
        val scroll   = ScrollView(this)
        val content  = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Judul
        txt(content, "RUMUS ${f.nomor}", 22f, colorNavy, true, Gravity.CENTER)
        txt(content, f.nama,   19f, colorBlue, true)
        txt(content, f.elemen, 13f, Color.DKGRAY)

        // ── RUMUS MATEMATIKA ─────────────────────────────────────────────
        heading(content, "RUMUS MATEMATIKA")
        // Field rumus = LaTeX murni tanpa $, render sebagai display block
        addMath(content, f.rumus, display = true, size = 18, bold = true)

        // ── KETERANGAN ───────────────────────────────────────────────────
        heading(content, "KETERANGAN")
        // Keterangan bisa mix teks + inline $...$
        addMath(content, f.keterangan, display = false, size = 15)

        // ── CONTOH SOAL ──────────────────────────────────────────────────
        heading(content, "CONTOH SOAL")
        addMath(content, f.soal, display = false, size = 15)

        // ── PEMBAHASAN ───────────────────────────────────────────────────
        if (f.langkah.isNotEmpty()) {
            heading(content, "PEMBAHASAN")
            f.langkah.forEachIndexed { i, (teks, tex) ->
                // Teks langkah (bisa ada inline math)
                addMath(content, "${i + 1}. $teks", display = false, size = 14, color = "#555555")
                // Ekspresi LaTeX (display block)
                if (tex.isNotBlank()) {
                    addMath(content, tex, display = true, size = 16)
                }
            }
        }

        // ── JAWABAN ──────────────────────────────────────────────────────
        heading(content, "JAWABAN")
        addMath(content, f.jawaban, display = true, size = 18, bold = true, color = "#2369BE")

        val back = Button(this).apply {
            text = "KEMBALI KE DAFTAR RUMUS"
            setOnClickListener { showFormulaList() }
        }
        content.addView(back)

        scroll.addView(content)
        rootView.addView(scroll, fullHeightParams())
        setContentView(rootView)
    }

    // =========================================================================
    // Widget helpers
    // =========================================================================

    /** Tambah section heading (teks biasa) */
    private fun heading(parent: LinearLayout, text: String) =
        txt(parent, text, 17f, colorBlue, true)

    /**
     * Tambah view matematika.
     * Jika tidak mengandung math dan display=false → pakai TextView biasa (lebih ringan).
     * Jika mengandung math atau display=true  → pakai KotlinFormulaRenderer.
     */
    private fun addMath(
        parent: LinearLayout,
        text: String,
        display: Boolean,
        size: Int = 15,
        bold: Boolean = false,
        color: String = "#102B50"
    ) {
        if (text.isBlank()) return

        val needsWebView = display || KotlinFormulaRenderer.containsMath(text)

        if (!needsWebView) {
            // Teks biasa — cukup TextView
            txt(parent, text, size.toFloat(), Color.parseColor(color), bold)
            return
        }

        // Gunakan KotlinFormulaRenderer
        // Beri tinggi awal yang cukup agar tidak invisible sebelum auto-resize selesai
        val initialHeight = dp(if (display) 60 else 40)

        val renderer = KotlinFormulaRenderer(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                initialHeight
            ).apply {
                topMargin    = dp(2)
                bottomMargin = dp(4)
            }
        }
        parent.addView(renderer)
        renderer.render(
            text          = text,
            wrapAsDisplay = display,
            textColor     = color,
            fontSize      = size,
            bold          = bold,
            bgColor       = "transparent"
        )
    }

    /** Tambah TextView biasa */
    private fun txt(
        parent: LinearLayout,
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        gravity: Int = Gravity.START
    ) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            this.gravity = gravity
            setPadding(dp(8), dp(5), dp(8), dp(5))
            if (bold) setTypeface(null, Typeface.BOLD)
        })
    }

    // ── LayoutParams ──────────────────────────────────────────────────────────

    private fun fullHeightParams() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

    private fun cardParams() =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) }

    private fun spacer(h: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
