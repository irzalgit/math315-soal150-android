package id.math315.soal150

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout

/**
 * KotlinFormulaRenderer
 *
 * Komponen WebView untuk merender ekspresi matematika LaTeX menggunakan KaTeX.
 *
 * Fitur:
 *  - Online mode  : KaTeX via CDN jsdelivr (render sempurna)
 *  - Offline mode : fallback Unicode sederhana agar simbol tetap terbaca
 *  - Mendukung inline math  : $...$ atau \(...\)
 *  - Mendukung display math : $$...$$ atau \[...\]
 *  - Teks campuran (mix teks + LaTeX)
 *  - LaTeX murni tanpa $ (wrap otomatis menjadi display block)
 *  - Auto-resize tinggi WebView sesuai konten
 */
class KotlinFormulaRenderer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    companion object {

        /**
         * Peta konversi LaTeX sederhana → Unicode untuk mode offline.
         * Digunakan sebagai fallback jika tidak ada koneksi internet.
         */
        private val UNICODE_MAP = linkedMapOf(
            // Pecahan umum
            "\\frac{1}{2}" to "½", "\\frac{1}{3}" to "⅓", "\\frac{2}{3}" to "⅔",
            "\\frac{1}{4}" to "¼", "\\frac{3}{4}" to "¾",
            // Operator
            "\\times" to "×", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓",
            "\\cdot" to "·", "\\bullet" to "•",
            // Relasi
            "\\leq" to "≤", "\\geq" to "≥", "\\neq" to "≠",
            "\\approx" to "≈", "\\equiv" to "≡", "\\sim" to "∼",
            "\\subset" to "⊂", "\\supset" to "⊃", "\\subseteq" to "⊆",
            "\\in" to "∈", "\\notin" to "∉",
            // Panah
            "\\rightarrow" to "→", "\\leftarrow" to "←",
            "\\Rightarrow" to "⇒", "\\Leftarrow" to "⇐",
            "\\leftrightarrow" to "↔",
            // Huruf Yunani
            "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
            "\\epsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ",
            "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ", "\\mu" to "μ",
            "\\nu" to "ν", "\\xi" to "ξ", "\\pi" to "π", "\\rho" to "ρ",
            "\\sigma" to "σ", "\\tau" to "τ", "\\upsilon" to "υ", "\\phi" to "φ",
            "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω",
            "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ",
            "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Phi" to "Φ",
            "\\Psi" to "Ψ", "\\Omega" to "Ω",
            // Kalkulus
            "\\infty" to "∞", "\\partial" to "∂", "\\nabla" to "∇",
            "\\sum" to "Σ", "\\prod" to "Π", "\\int" to "∫",
            "\\oint" to "∮", "\\iint" to "∬",
            // Fungsi trig & lain
            "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan",
            "\\cot" to "cot", "\\sec" to "sec", "\\csc" to "csc",
            "\\log" to "log", "\\ln" to "ln", "\\lim" to "lim",
            "\\max" to "max", "\\min" to "min", "\\gcd" to "gcd",
            // Akar
            "\\sqrt" to "√",
            // Lain
            "\\ldots" to "…", "\\cdots" to "⋯", "\\vdots" to "⋮", "\\ddots" to "⋱",
            "\\angle" to "∠", "\\perp" to "⊥", "\\parallel" to "∥",
            "\\triangle" to "△", "\\square" to "□",
            "\\circ" to "°",
            // Spasi
            "\\quad" to " ", "\\qquad" to "  ", "\\ " to " ",
            "\\!" to "", "\\," to " ", "\\;" to " ",
            // Bracket
            "\\left(" to "(", "\\right)" to ")",
            "\\left[" to "[", "\\right]" to "]",
            "\\left\\{" to "{", "\\right\\}" to "}",
            "\\left|" to "|", "\\right|" to "|"
        )

        /**
         * Konversi LaTeX ke teks Unicode sederhana (offline fallback).
         * Tidak sempurna untuk ekspresi kompleks, tapi tetap terbaca.
         */
        fun latexToUnicode(latex: String): String {
            var result = latex.trim()

            // Bungkus \frac{a}{b} → (a)/(b)
            val fracRegex = Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")
            result = fracRegex.replace(result) { m ->
                "(${m.groupValues[1]})/(${m.groupValues[2]})"
            }

            // \sqrt{x} → √(x), \sqrt[n]{x} → ⁿ√(x)
            val sqrtNRegex = Regex("""\\sqrt\[([^\]]+)\]\{([^}]+)\}""")
            result = sqrtNRegex.replace(result) { m -> "${m.groupValues[1]}√(${m.groupValues[2]})" }
            val sqrtRegex = Regex("""\\sqrt\{([^}]+)\}""")
            result = sqrtRegex.replace(result) { m -> "√(${m.groupValues[1]})" }

            // Superscript: x^{...} → x^...  x^2 → x²
            val supDigits = mapOf('0' to "⁰", '1' to "¹", '2' to "²", '3' to "³",
                '4' to "⁴", '5' to "⁵", '6' to "⁶", '7' to "⁷", '8' to "⁸", '9' to "⁹",
                '+' to "⁺", '-' to "⁻", 'n' to "ⁿ", 'a' to "ᵃ", 'b' to "ᵇ", 'c' to "ᶜ")
            val supBraceRegex = Regex("""\^\{([^}]+)\}""")
            result = supBraceRegex.replace(result) { m ->
                val inner = m.groupValues[1]
                if (inner.length == 1 && inner[0] in supDigits) supDigits[inner[0]]!! else "^$inner"
            }
            val supCharRegex = Regex("""\^([\d])""")
            result = supCharRegex.replace(result) { m ->
                supDigits[m.groupValues[1][0]] ?: "^${m.groupValues[1]}"
            }

            // Subscript: x_{...} → x_...
            val subBraceRegex = Regex("""_\{([^}]+)\}""")
            result = subBraceRegex.replace(result) { m -> "_${m.groupValues[1]}" }

            // Ganti perintah LaTeX dengan Unicode dari peta
            for ((cmd, unicode) in UNICODE_MAP) {
                result = result.replace(cmd, unicode)
            }

            // Bersihkan kurung kurawal sisa & backslash ganda
            result = result.replace("{", "").replace("}", "").replace("\\\\", "\n")

            return result
        }

        /**
         * Deteksi apakah string mengandung LaTeX math.
         */
        fun containsMath(text: String): Boolean {
            if (text.isBlank()) return false
            return text.contains("\\") ||
                text.contains("\$") ||
                text.contains("^{") ||
                text.contains("_{")
        }
    }

    private var onHeightReady: ((Int) -> Unit)? = null

    init {
        setup()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setup() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            cacheMode = WebSettings.LOAD_DEFAULT   // gunakan cache jika ada
            loadsImagesAutomatically = true
            defaultTextEncodingName = "UTF-8"
        }
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isScrollContainer = false
        overScrollMode = OVER_SCROLL_NEVER

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Ukur tinggi konten setelah render selesai lalu resize WebView
                evaluateJavascript(
                    "(function(){ return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight); })();"
                ) { result ->
                    val h = result?.replace("\"", "")?.toFloatOrNull()?.toInt() ?: 0
                    if (h > 0) {
                        post {
                            layoutParams = (layoutParams ?: defaultLayoutParams()).also { lp ->
                                lp.height = h
                            }
                            onHeightReady?.invoke(h)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Render teks yang bisa berisi LaTeX.
     *
     * @param text          String yang akan dirender.
     * @param wrapAsDisplay Jika true DAN text tidak mengandung $, bungkus seluruh text
     *                      sebagai display math (berguna untuk field "rumus" / "jawaban"
     *                      yang berisi LaTeX murni tanpa pembungkus $).
     * @param textColor     Warna teks dalam format hex CSS (mis. "#102B50")
     * @param fontSize      Ukuran font dalam px
     * @param bold          Cetak tebal
     * @param bgColor       Warna background CSS ("transparent" atau hex)
     * @param onReady       Callback setelah tinggi komponen diketahui
     */
    fun render(
        text: String,
        wrapAsDisplay: Boolean = false,
        textColor: String = "#102B50",
        fontSize: Int = 15,
        bold: Boolean = false,
        bgColor: String = "transparent",
        onReady: ((Int) -> Unit)? = null
    ) {
        onHeightReady = onReady
        if (isNetworkAvailable()) {
            renderWithKaTeX(text, wrapAsDisplay, textColor, fontSize, bold, bgColor)
        } else {
            renderWithUnicode(text, wrapAsDisplay, textColor, fontSize, bold, bgColor)
        }
    }

    // =========================================================================
    // Online rendering via KaTeX CDN
    // =========================================================================

    private fun renderWithKaTeX(
        text: String,
        wrapAsDisplay: Boolean,
        textColor: String,
        fontSize: Int,
        bold: Boolean,
        bgColor: String
    ) {
        val content = prepareContent(text, wrapAsDisplay)
        val html    = buildKaTeXHtml(content, textColor, fontSize, bold, bgColor)
        loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
    }

    private fun buildKaTeXHtml(
        content: String,
        textColor: String,
        fontSize: Int,
        bold: Boolean,
        bgColor: String
    ): String {
        val fw = if (bold) "bold" else "normal"
        return """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no"/>
<link rel="stylesheet"
      href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css"
      integrity="sha384-n8MVd4RsNIU0tAv4ct0nTaAbDJwPJzDEaqSD1odI+WdtXRGWt2kTvGFasHpSy3SV"
      crossorigin="anonymous"/>
<script defer
        src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"
        integrity="sha384-XjKyOOlGwcjNTAIQHIpgOno0Hl1YQqzUOEleOLALmuqehneUG+vnGctmUb0ZY0l8"
        crossorigin="anonymous"></script>
<script defer
        src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"
        integrity="sha384-+VBxd3r6XgURycqtZ117nYw44OOcIax56Z4dCRWbxyPt0Koah1uHoK0o4+/RRE05"
        crossorigin="anonymous"
        onload="go()"></script>
<style>
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:$bgColor;width:100%;overflow-x:hidden}
#c{font-family:'Roboto',sans-serif;font-size:${fontSize}px;font-weight:$fw;
   color:$textColor;line-height:1.65;padding:6px 8px;word-break:break-word}
.katex-display{overflow-x:auto;overflow-y:hidden;padding:4px 0}
.katex{font-size:1.1em}
</style>
</head>
<body>
<div id="c">$content</div>
<script>
function go(){
  renderMathInElement(document.getElementById('c'),{
    delimiters:[
      {left:'$$$$',right:'$$$$',display:true},
      {left:'$',right:'$',display:false},
      {left:'\\\\[',right:'\\\\]',display:true},
      {left:'\\\\(',right:'\\\\)',display:false}
    ],
    throwOnError:false,
    errorColor:'#cc0000'
  });
}
</script>
</body>
</html>"""
    }

    // =========================================================================
    // Offline rendering via Unicode fallback
    // =========================================================================

    private fun renderWithUnicode(
        text: String,
        wrapAsDisplay: Boolean,
        textColor: String,
        fontSize: Int,
        bold: Boolean,
        bgColor: String
    ) {
        // Strip pembungkus $ jika ada lalu konversi
        val stripped = text
            .replace(Regex("""^\$\$|\$\$$"""), "")
            .replace(Regex("""^\$|\$$"""), "")
            .trim()
        val unicode = latexToUnicode(if (wrapAsDisplay && !text.contains("\$")) text else stripped)
        val fw      = if (bold) "bold" else "normal"
        val html    = """<!DOCTYPE html>
<html><head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no"/>
<style>
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:$bgColor;width:100%;overflow-x:hidden}
#c{font-family:'Roboto',monospace;font-size:${fontSize}px;font-weight:$fw;
   color:$textColor;line-height:1.65;padding:6px 8px;word-break:break-word}
</style>
</head><body>
<div id="c">${htmlEscape(unicode)}</div>
</body></html>"""
        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Siapkan konten: jika wrapAsDisplay dan tidak ada $, bungkus dengan $$...$$
     */
    private fun prepareContent(text: String, wrapAsDisplay: Boolean): String {
        if (text.isBlank()) return ""
        val hasDollar = text.contains("\$")
        return if (wrapAsDisplay && !hasDollar) "\$\$$text\$\$" else text
    }

    private fun htmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // -------------------------------------------------------------------------
    // LayoutParams helper
    // -------------------------------------------------------------------------

    fun defaultLayoutParams(bottomMarginPx: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            if (bottomMarginPx > 0) bottomMargin = bottomMarginPx
        }
}
