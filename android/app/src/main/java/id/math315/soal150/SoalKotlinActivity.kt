package id.math315.soal150

import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.Locale

class SoalKotlinActivity : AppCompatActivity() {

    // -------------------------------------------------------------------------
    // Data
    // -------------------------------------------------------------------------

    data class Rumus(
        val nomor: Int,
        val elemen: String,
        val subelemen: String,
        val nama: String,
        val rumus: String,       // LaTeX tanpa $  → selalu di-wrap sebagai display
        val keterangan: String,  // Teks biasa, bisa ada inline $...$
        val soal: String,        // Teks soal, bisa ada inline $...$
        val langkah: List<Pair<String, String>>, // teks, tex
        val jawaban: String      // LaTeX tanpa $  → selalu di-wrap sebagai display
    )

    // -------------------------------------------------------------------------
    // State & Warna
    // -------------------------------------------------------------------------

    private val formulas = mutableListOf<Rumus>()
    private var uid = ""
    private var isPremium = false
    private var premiumUntil = 0L

    private val colorNavy = Color.rgb(16, 43, 80)
    private val colorBlue = Color.rgb(35, 105, 190)
    private val colorBg = Color.rgb(241, 245, 251)

    // Hex strings untuk KotlinFormulaRenderer
    private val hexNavy = "#102B50"
    private val hexBlue = "#2369BE"
    private val hexGray = "#555555"

    private lateinit var root: LinearLayout

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uid          = intent.getStringExtra("uid") ?: ""
        isPremium    = intent.getBooleanExtra("isPremium", false)
        premiumUntil = intent.getLongExtra("premiumUntil", 0L)
        loadFormulas()
        showFormulaList()
    }

    // -------------------------------------------------------------------------
    // Helper: premium
    // -------------------------------------------------------------------------

    private fun premiumActive(): Boolean =
        uid.isNotBlank() && isPremium && premiumUntil > System.currentTimeMillis()

    // -------------------------------------------------------------------------
    // Load JSON
    // -------------------------------------------------------------------------

    private fun loadFormulas() {
        try {
            val json = assets.open("public/data/rumus-smp.json")
                .bufferedReader()
                .use { it.readText() }

            val array = JSONObject(json).getJSONArray("rumus")

            for (i in 0 until array.length()) {
                val item   = array.getJSONObject(i)
                val contoh = item.optJSONObject("contoh")
                val langkahList = mutableListOf<Pair<String, String>>()

                contoh?.optJSONArray("langkah")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val step = arr.getJSONObject(j)
                        langkahList.add(
                            Pair(
                                step.optString("teks"),
                                step.optString("tex")
                            )
                        )
                    }
                }

                formulas.add(
                    Rumus(
                        nomor      = item.optInt("nomor"),
                        elemen     = item.optString("elemen"),
                        subelemen  = item.optString("subelemen"),
                        nama       = item.optString("nama"),
                        rumus      = item.optString("rumus"),
                        keterangan = item.optString("keterangan"),
                        soal       = contoh?.optString("soal") ?: "",
                        langkah    = langkahList,
                        jawaban    = contoh?.optString("jawaban") ?: ""
                    )
                )
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // Root view
    // -------------------------------------------------------------------------

    private fun createRoot() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
    }

    // =========================================================================
    // HALAMAN 1: Daftar rumus
    // =========================================================================

    private fun showFormulaList() {
        createRoot()

        val scroll  = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        addPlainText(content, "MATH315\n155 RUMUS MATEMATIKA SMP", 23f, colorNavy, true, Gravity.CENTER)
        addPlainText(content, "Latihan Matematika Native Kotlin", 14f, Color.DKGRAY, false, Gravity.CENTER)

        val status = if (premiumActive()) "PREMIUM AKTIF" else "AKSES GRATIS: RUMUS 1–5"
        addPlainText(content, "$status\nJumlah rumus: ${formulas.size}", 15f, colorBlue, true, Gravity.CENTER)

        if (formulas.isEmpty()) {
            addPlainText(content, "Data rumus belum berhasil dimuat.", 16f, Color.RED, true)
        }

        formulas.forEach { formula ->
            val unlocked = formula.nomor <= 5 || premiumActive()

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(Color.WHITE)
            }

            addPlainText(card, "${formula.nomor}. ${formula.nama}", 17f, colorNavy, true)
            addPlainText(card, "${formula.elemen} • ${formula.subelemen}", 12f, Color.DKGRAY)

            // Preview rumus dalam card — render matematika
            addFormulaView(card, formula.rumus, wrapAsDisplay = true, fontSize = 14)

            val button = Button(this).apply {
                text = if (unlocked) "BUKA RUMUS" else "🔒 PREMIUM – BUKA"
                setOnClickListener {
                    if (unlocked) {
                        showFormulaDetail(formula)
                    } else {
                        Toast.makeText(
                            this@SoalKotlinActivity,
                            "Rumus ini memerlukan Premium Math315.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            card.addView(button)
            content.addView(card, cardParams())
            content.addView(space(8))
        }

        val back = Button(this).apply {
            text = "KEMBALI KE MENU"
            setOnClickListener { finish() }
        }
        content.addView(back)

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    // =========================================================================
    // HALAMAN 2: Detail rumus
    // =========================================================================

    private fun showFormulaDetail(formula: Rumus) {
        createRoot()

        val scroll  = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Judul
        addPlainText(content, "RUMUS ${formula.nomor}", 22f, colorNavy, true, Gravity.CENTER)
        addPlainText(content, formula.nama, 19f, colorBlue, true)
        addPlainText(content, formula.elemen, 13f, Color.DKGRAY)

        // ── RUMUS MATEMATIKA ─────────────────────────────────────────────────
        addSectionHeading(content, "RUMUS MATEMATIKA")
        // Field "rumus" berisi LaTeX murni tanpa $, bungkus sebagai display block
        addFormulaView(content, formula.rumus, wrapAsDisplay = true, fontSize = 18, bold = true)

        // ── KETERANGAN ───────────────────────────────────────────────────────
        addSectionHeading(content, "KETERANGAN")
        // Keterangan bisa mix teks + inline $...$
        addFormulaView(content, formula.keterangan, wrapAsDisplay = false, fontSize = 15)

        // ── CONTOH SOAL ──────────────────────────────────────────────────────
        addSectionHeading(content, "CONTOH SOAL")
        addFormulaView(content, formula.soal, wrapAsDisplay = false, fontSize = 15)

        // ── PEMBAHASAN ───────────────────────────────────────────────────────
        if (formula.langkah.isNotEmpty()) {
            addSectionHeading(content, "PEMBAHASAN")

            formula.langkah.forEachIndexed { index, (teks, tex) ->
                // Nomor langkah + teks penjelasan (bisa ada inline math)
                addFormulaView(
                    content,
                    "${index + 1}. $teks",
                    wrapAsDisplay = false,
                    fontSize = 14,
                    textColor = hexGray
                )
                // Ekspresi LaTeX langkah (selalu display)
                if (tex.isNotBlank()) {
                    addFormulaView(
                        content,
                        tex,
                        wrapAsDisplay = true,
                        fontSize = 16,
                        textColor = hexNavy
                    )
                }
            }
        }

        // ── JAWABAN ──────────────────────────────────────────────────────────
        addSectionHeading(content, "JAWABAN")
        addFormulaView(content, formula.jawaban, wrapAsDisplay = true, fontSize = 18, bold = true, textColor = hexBlue)

        val back = Button(this).apply {
            text = "KEMBALI KE DAFTAR RUMUS"
            setOnClickListener { showFormulaList() }
        }
        content.addView(back)

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    // =========================================================================
    // Widget helpers
    // =========================================================================

    /**
     * Tambahkan heading seksi (teks biasa, tidak perlu render math).
     */
    private fun addSectionHeading(parent: LinearLayout, heading: String) {
        addPlainText(parent, heading, 17f, colorBlue, true)
    }

    /**
     * Tambahkan KotlinFormulaRenderer ke parent.
     * Jika teks tidak mengandung math sama sekali DAN wrapAsDisplay=false,
     * gunakan TextView biasa agar lebih ringan.
     */
    private fun addFormulaView(
        parent: LinearLayout,
        text: String,
        wrapAsDisplay: Boolean,
        fontSize: Int = 15,
        bold: Boolean = false,
        textColor: String = hexNavy
    ) {
        if (text.isBlank()) return

        // Jika tidak ada math → pakai TextView biasa (lebih cepat)
        if (!wrapAsDisplay && !KotlinFormulaRenderer.containsMath(text)) {
            addPlainText(
                parent,
                text,
                fontSize.toFloat(),
                Color.parseColor(textColor),
                bold
            )
            return
        }

        val renderer = KotlinFormulaRenderer(this).apply {
            layoutParams = defaultWebViewParams()
        }
        parent.addView(renderer)
        renderer.render(
            text          = text,
            wrapAsDisplay = wrapAsDisplay,
            textColor     = textColor,
            fontSize      = fontSize,
            bold          = bold,
            bgColor       = "transparent"
        )
    }

    /**
     * Tambahkan TextView biasa (tanpa render math) ke parent.
     */
    private fun addPlainText(
        parent: LinearLayout,
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        gravity: Int = Gravity.START
    ) {
        parent.addView(
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                this.gravity = gravity
                setPadding(dp(8), dp(6), dp(8), dp(6))
                if (bold) setTypeface(null, Typeface.BOLD)
            }
        )
    }

    // -------------------------------------------------------------------------
    // LayoutParams helpers
    // -------------------------------------------------------------------------

    private fun defaultWebViewParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin    = dp(2)
            bottomMargin = dp(2)
        }

    private fun cardParams() =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }

    private fun space(height: Int) =
        android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(height))
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
