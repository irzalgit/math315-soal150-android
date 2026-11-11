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
 * Render LaTeX via KaTeX (online) atau Unicode fallback (offline).
 */
class KotlinFormulaRenderer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    companion object {

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
            // Fungsi
            "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan",
            "\\cot" to "cot", "\\sec" to "sec", "\\csc" to "csc",
            "\\log" to "log", "\\ln" to "ln", "\\lim" to "lim",
            "\\max" to "max", "\\min" to "min", "\\gcd" to "gcd",
            // Lain
            "\\sqrt" to "√",
            "\\ldots" to "…", "\\cdots" to "⋯",
            "\\angle" to "∠", "\\perp" to "⊥", "\\parallel" to "∥",
            "\\triangle" to "△", "\\square" to "□", "\\circ" to "°",
            "\\quad" to " ", "\\qquad" to "  ",
            "\\left(" to "(", "\\right)" to ")",
            "\\left[" to "[", "\\right]" to "]",
            "\\left|" to "|", "\\right|" to "|"
        )

        fun latexToUnicode(latex: String): String {
            var r = latex.trim()
            // \frac{a}{b} → (a)/(b)
            r = Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")
                .replace(r) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
            // \sqrt{x} → √(x)
            r = Regex("""\\sqrt\[([^\]]+)\]\{([^}]+)\}""")
                .replace(r) { "${it.groupValues[1]}√(${it.groupValues[2]})" }
            r = Regex("""\\sqrt\{([^}]+)\}""")
                .replace(r) { "√(${it.groupValues[1]})" }
            // superscript
            val supMap = mapOf('0' to "⁰", '1' to "¹", '2' to "²", '3' to "³",
                '4' to "⁴", '5' to "⁵", '6' to "⁶", '7' to "⁷", '8' to "⁸", '9' to "⁹",
                '+' to "⁺", '-' to "⁻", 'n' to "ⁿ")
            r = Regex("""\^\{([^}]+)\}""").replace(r) { m ->
                val inner = m.groupValues[1]
                if (inner.length == 1 && inner[0] in supMap) supMap[inner[0]]!! else "^$inner"
            }
            r = Regex("""\^([\d])""").replace(r) { m ->
                supMap[m.groupValues[1][0]] ?: "^${m.groupValues[1]}"
            }
            // subscript
            r = Regex("""_\{([^}]+)\}""").replace(r) { "_${it.groupValues[1]}" }
            // unicode map
            for ((cmd, uni) in UNICODE_MAP) r = r.replace(cmd, uni)
            // bersihkan sisa
            r = r.replace("{", "").replace("}", "").replace("\\\\", "\n")
            return r
        }

        fun containsMath(text: String): Boolean {
            if (text.isBlank()) return false
            return text.contains("\\") || text.contains("$") ||
                text.contains("^{") || text.contains("_{")
        }
    }

    init { setup() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setup() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            cacheMode = WebSettings.LOAD_DEFAULT
            defaultTextEncodingName = "UTF-8"
        }
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isScrollContainer = false
        overScrollMode = OVER_SCROLL_NEVER

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Setelah halaman selesai dimuat, ukur tinggi konten dan resize
                view?.evaluateJavascript(
                    "(function(){ return Math.max(document.body.scrollHeight," +
                    "document.documentElement.scrollHeight); })();"
                ) { result ->
                    val h = result?.replace("\"", "")?.toFloatOrNull()?.toInt() ?: 0
                    if (h > 0) {
                        post {
                            val lp = layoutParams as? LinearLayout.LayoutParams
                                ?: LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                            lp.height = h
                            layoutParams = lp
                        }
                    }
                }
            }
        }
    }

    /**
     * Render teks/LaTeX.
     * @param text          Teks yang mungkin berisi LaTeX
     * @param wrapAsDisplay true = bungkus seluruh text sebagai $$...$$ (untuk field rumus/jawaban)
     * @param textColor     Hex CSS warna teks
     * @param fontSize      Ukuran font px
     * @param bold          Cetak tebal
     * @param bgColor       Warna background CSS
     */
    fun render(
        text: String,
        wrapAsDisplay: Boolean = false,
        textColor: String = "#102B50",
        fontSize: Int = 15,
        bold: Boolean = false,
        bgColor: String = "transparent"
    ) {
        if (text.isBlank()) return
        if (isNetworkAvailable()) {
            renderOnline(text, wrapAsDisplay, textColor, fontSize, bold, bgColor)
        } else {
            renderOffline(text, wrapAsDisplay, textColor, fontSize, bold, bgColor)
        }
    }

    // ── Online: KaTeX CDN ──────────────────────────────────────────────────────

    private fun renderOnline(
        text: String, wrapAsDisplay: Boolean,
        textColor: String, fontSize: Int, bold: Boolean, bgColor: String
    ) {
        // Bungkus LaTeX murni menjadi $$...$$ agar KaTeX auto-render sebagai display
        val hasDollar = text.contains("$")
        val content = if (wrapAsDisplay && !hasDollar) "\$\$$text\$\$" else text

        val fw = if (bold) "bold" else "normal"

        // Delimiter $$...$$ ditulis sebagai literal karakter di sini (bukan escape Kotlin)
        // karena ini sudah dalam raw string HTML yang dikirim ke WebView
        val delimDisplay = "$$"   // dua tanda dollar
        val delimInline  = "$"    // satu tanda dollar

        val html = """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no"/>
<link rel="stylesheet"
      href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css"
      crossorigin="anonymous"/>
<script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"
        crossorigin="anonymous"></script>
<script defer
        src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"
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
      {left:'$delimDisplay',right:'$delimDisplay',display:true},
      {left:'$delimInline',right:'$delimInline',display:false},
      {left:'\\[',right:'\\]',display:true},
      {left:'\\(',right:'\\)',display:false}
    ],
    throwOnError:false,
    errorColor:'#cc0000'
  });
}
</script>
</body>
</html>"""

        loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
    }

    // ── Offline: Unicode fallback ──────────────────────────────────────────────

    private fun renderOffline(
        text: String, wrapAsDisplay: Boolean,
        textColor: String, fontSize: Int, bold: Boolean, bgColor: String
    ) {
        val stripped = text.replace(Regex("""^\$\$|\$\$$"""), "")
            .replace(Regex("""^\$|\$$"""), "").trim()
        val unicode = latexToUnicode(
            if (wrapAsDisplay && !text.contains("$")) text else stripped
        )
        val fw = if (bold) "bold" else "normal"
        val escaped = unicode
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val html = """<!DOCTYPE html>
<html><head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no"/>
<style>
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:$bgColor;width:100%;overflow-x:hidden}
#c{font-family:monospace;font-size:${fontSize}px;font-weight:$fw;
   color:$textColor;line-height:1.65;padding:6px 8px;word-break:break-word}
</style>
</head><body>
<div id="c">$escaped</div>
</body></html>"""
        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
            val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    fun defaultLayoutParams(bottomMarginPx: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            if (bottomMarginPx > 0) bottomMargin = bottomMarginPx
        }
}
