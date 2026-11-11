package id.math315.soal150

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SoalKotlinActivity : AppCompatActivity() {

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

    private val formulas = mutableListOf<Rumus>()
    private var uid = ""
    private var isPremium = false
    private var premiumUntil = 0L

    private val navy = Color.rgb(16, 43, 80)
    private val blue = Color.rgb(35, 105, 190)
    private val backgroundColor = Color.rgb(241, 245, 251)

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uid = intent.getStringExtra("uid") ?: ""
        isPremium = intent.getBooleanExtra("isPremium", false)
        premiumUntil = intent.getLongExtra("premiumUntil", 0L)

        loadFormulas()
        showFormulaList()
    }

    private fun premiumActive(): Boolean {
        return uid.isNotBlank() &&
            isPremium &&
            premiumUntil > System.currentTimeMillis()
    }

    private fun loadFormulas() {
        try {
            val json = assets.open("public/data/rumus-smp.json")
                .bufferedReader()
                .use { it.readText() }

            val array = JSONObject(json).getJSONArray("rumus")

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val contoh = item.optJSONObject("contoh")
                val langkahList = mutableListOf<Pair<String, String>>()

                val langkahArray = contoh?.optJSONArray("langkah")

                if (langkahArray != null) {
                    for (j in 0 until langkahArray.length()) {
                        val step = langkahArray.getJSONObject(j)
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
                        nomor = item.optInt("nomor"),
                        elemen = item.optString("elemen"),
                        subelemen = item.optString("subelemen"),
                        nama = item.optString("nama"),
                        rumus = item.optString("rumus"),
                        keterangan = item.optString("keterangan"),
                        soal = contoh?.optString("soal") ?: "",
                        langkah = langkahList,
                        jawaban = contoh?.optString("jawaban") ?: ""
                    )
                )
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Gagal membaca data rumus: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun createRoot() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
    }

    private fun showFormulaList() {
        createRoot()

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        addText(
            content,
            "MATH315\n155 RUMUS MATEMATIKA SMP",
            23f, navy, true, Gravity.CENTER
        )

        addText(
            content,
            "Latihan Matematika Native Kotlin",
            14f, Color.DKGRAY, false, Gravity.CENTER
        )

        val status = if (premiumActive()) {
            "PREMIUM AKTIF"
        } else {
            "AKSES GRATIS: RUMUS 1–5"
        }

        addText(
            content,
            "$status\nJumlah rumus: ${formulas.size}",
            15f, blue, true, Gravity.CENTER
        )

        if (formulas.isEmpty()) {
            addText(
                content,
                "Data rumus belum berhasil dimuat.",
                16f, Color.RED, true
            )
        }

        formulas.forEach { formula ->
            val unlocked = formula.nomor <= 5 || premiumActive()

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(Color.WHITE)
            }

            addText(
                card,
                "${formula.nomor}. ${formula.nama}",
                17f, navy, true
            )

            addText(
                card,
                "${formula.elemen} • ${formula.subelemen}",
                12f, Color.DKGRAY
            )

            val button = Button(this).apply {
                text = if (unlocked) {
                    "BUKA RUMUS"
                } else {
                    "🔒 PREMIUM - BUKA"
                }

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
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        )

        setContentView(root)
    }

    private fun showFormulaDetail(formula: Rumus) {
        createRoot()

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        addText(
            content,
            "RUMUS ${formula.nomor}",
            22f, navy, true, Gravity.CENTER
        )

        addText(content, formula.nama, 19f, blue, true)
        addText(content, formula.elemen, 13f, Color.DKGRAY)

        addSection(content, "RUMUS MATEMATIKA", formula.rumus)
        addSection(content, "KETERANGAN", formula.keterangan)
        addSection(content, "CONTOH SOAL", formula.soal)

        if (formula.langkah.isNotEmpty()) {
            addText(content, "PEMBAHASAN", 19f, navy, true)

            formula.langkah.forEachIndexed { index, step ->
                addText(
                    content,
                    "${index + 1}. ${step.first}",
                    15f, Color.DKGRAY, true
                )
                addText(
                    content,
                    step.second,
                    15f, navy
                )
            }
        }

        addSection(content, "JAWABAN", formula.jawaban)

        val back = Button(this).apply {
            text = "KEMBALI KE DAFTAR RUMUS"
            setOnClickListener { showFormulaList() }
        }

        content.addView(back)
        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        )

        setContentView(root)
    }

    private fun addSection(
        parent: LinearLayout,
        heading: String,
        value: String
    ) {
        addText(parent, heading, 17f, blue, true)
        addText(parent, value, 15f, navy)
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        gravity: Int = Gravity.START
    ) {
        val view = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            this.gravity = gravity
            setPadding(dp(8), dp(9), dp(8), dp(9))
            if (bold) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }

        parent.addView(view)
    }

    private fun cardParams() =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(8)
        }

    private fun space(height: Int) =
        android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1, dp(height)
            )
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
