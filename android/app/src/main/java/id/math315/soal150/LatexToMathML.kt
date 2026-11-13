package id.math315.soal150

/**
 * LatexToMathML
 *
 * Konverter LaTeX → MathML untuk matematika SMP.
 * Bekerja fully offline tanpa library eksternal.
 *
 * Mendukung:
 *  - Pecahan      : \frac{a}{b}
 *  - Akar         : \sqrt{x}, \sqrt[n]{x}
 *  - Pangkat      : x^2, x^{n+1}
 *  - Indeks       : x_i, x_{ij}
 *  - Pangkat+Indeks: x_i^2 → <msubsup>
 *  - Huruf Yunani : \alpha, \beta, ... , \Omega
 *  - Operator     : \times, \div, \pm, \leq, \geq, ...
 *  - Fungsi       : \sin, \cos, \tan, \log, \ln, \lim, ...
 *  - Teks         : \text{...}
 *  - Bracket      : \left( \right), \left[ \right], ...
 *  - Dekorasi     : \hat, \bar, \vec, \tilde, \overline
 *  - Teks campuran: "Hitunglah $\frac{2}{3}$." → HTML + MathML inline
 */
object LatexToMathML {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Konversi string LaTeX murni (tanpa $) ke elemen <math> MathML.
     * @param latex   String LaTeX tanpa pembungkus dollar
     * @param display true = display block, false = inline
     */
    fun convert(latex: String, display: Boolean = true): String {
        if (latex.isBlank()) return ""
        val inner = Parser(latex.trim()).parseExpr()
        val attr  = if (display) " display=\"block\"" else ""
        return "<math xmlns=\"http://www.w3.org/1998/Math/MathML\"$attr>$inner</math>"
    }

    /**
     * Konversi teks campuran yang bisa berisi:
     *  - $...$ (inline math)
     *  - $$...$$ (display math)
     *  - Teks biasa
     * Menghasilkan HTML dengan MathML embedded.
     */
    fun convertMixed(text: String): String {
        if (text.isBlank()) return ""

        val sb     = StringBuilder()
        // Urutan: cek $$ dulu baru $
        val regex  = Regex("""\$\$([\s\S]+?)\$\$|\$([^\$\n]+?)\$""")
        var last   = 0

        for (match in regex.findAll(text)) {
            if (match.range.first > last) {
                sb.append(htmlEscape(text.substring(last, match.range.first)))
            }
            val (displayLatex, inlineLatex) = match.destructured
            if (displayLatex.isNotEmpty()) {
                sb.append(convert(displayLatex, display = true))
            } else {
                sb.append(convert(inlineLatex, display = false))
            }
            last = match.range.last + 1
        }

        if (last < text.length) {
            sb.append(htmlEscape(text.substring(last)))
        }

        return sb.toString()
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    fun htmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    // ── Recursive Parser ──────────────────────────────────────────────────────

    private class Parser(private val src: String) {
        var pos = 0

        /** Parse urutan atom, kembalikan MathML string. */
        fun parseExpr(stopAt: Char? = null): String {
            val parts = mutableListOf<String>()
            while (pos < src.length) {
                val c = src[pos]
                if (c == '}') break
                if (stopAt != null && c == stopAt) break
                skipWS()
                if (pos >= src.length || src[pos] == '}') break
                if (stopAt != null && src[pos] == stopAt) break

                val base = parseAtom() ?: break

                // Periksa apakah diikuti ^ dan/atau _
                skipWS()
                when {
                    pos < src.length && src[pos] == '^' -> {
                        pos++
                        val sup = parseArg()
                        skipWS()
                        if (pos < src.length && src[pos] == '_') {
                            pos++
                            val sub = parseArg()
                            parts += "<msubsup>$base$sub$sup</msubsup>"
                        } else {
                            parts += "<msup>$base$sup</msup>"
                        }
                    }
                    pos < src.length && src[pos] == '_' -> {
                        pos++
                        val sub = parseArg()
                        skipWS()
                        if (pos < src.length && src[pos] == '^') {
                            pos++
                            val sup = parseArg()
                            parts += "<msubsup>$base$sub$sup</msubsup>"
                        } else {
                            parts += "<msub>$base$sub</msub>"
                        }
                    }
                    else -> parts += base
                }
            }
            return parts.joinToString("")
        }

        /** Parse satu argumen: {expr} atau satu atom */
        fun parseArg(): String {
            skipWS()
            if (pos >= src.length) return ""
            return if (src[pos] == '{') {
                pos++ // skip {
                val r = parseExpr()
                if (pos < src.length && src[pos] == '}') pos++
                "<mrow>$r</mrow>"
            } else {
                parseAtom() ?: ""
            }
        }

        /** Parse satu atom: perintah, group, angka, huruf, simbol */
        fun parseAtom(): String? {
            skipWS()
            if (pos >= src.length) return null
            return when {
                src[pos] == '\\'           -> parseCommand()
                src[pos] == '{'           -> parseGroup()
                src[pos].isDigit()        -> parseNumber()
                src[pos].isLetter()       -> { val c = src[pos++]; "<mi>$c</mi>" }
                src[pos] == '+'           -> { pos++; "<mo>+</mo>" }
                src[pos] == '-'           -> { pos++; "<mo>\u2212</mo>" } // proper minus
                src[pos] == '='           -> { pos++; "<mo>=</mo>" }
                src[pos] == '*'           -> { pos++; "<mo>\u00D7</mo>" }
                src[pos] == '/'           -> { pos++; "<mo>/</mo>" }
                src[pos] == '<'           -> { pos++; "<mo>&lt;</mo>" }
                src[pos] == '>'           -> { pos++; "<mo>&gt;</mo>" }
                src[pos] == '('           -> { pos++; "<mo stretchy=\"false\">(</mo>" }
                src[pos] == ')'           -> { pos++; "<mo stretchy=\"false\">)</mo>" }
                src[pos] == '['           -> { pos++; "<mo stretchy=\"false\">[</mo>" }
                src[pos] == ']'           -> { pos++; "<mo stretchy=\"false\">]</mo>" }
                src[pos] == '|'           -> { pos++; "<mo>|</mo>" }
                src[pos] == ','           -> { pos++; "<mo>,</mo>" }
                src[pos] == '.'           -> { pos++; "<mo>.</mo>" }
                src[pos] == ':'           -> { pos++; "<mo>:</mo>" }
                src[pos] == '!'           -> { pos++; "<mo>!</mo>" }
                src[pos] == '%'           -> { pos++; "<mo>%</mo>" }
                src[pos] == '\''          -> { pos++; "<mo>\u2032</mo>" }
                src[pos] == '\u00B0'      -> { pos++; "<mo>\u00B0</mo>" }
                src[pos] == '&'           -> { pos++; "" } // align separator, skip
                else                       -> { val c = src[pos++]; "<mo>${htmlEsc(c.toString())}</mo>" }
            }
        }

        private fun parseGroup(): String {
            pos++ // skip {
            val r = parseExpr()
            if (pos < src.length && src[pos] == '}') pos++
            return "<mrow>$r</mrow>"
        }

        private fun parseNumber(): String {
            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
            return "<mn>${src.substring(start, pos)}</mn>"
        }

        private fun parseCommand(): String {
            pos++ // skip backslash
            if (pos >= src.length) return "<mo>\\</mo>"

            val name = if (src[pos].isLetter()) {
                val start = pos
                while (pos < src.length && src[pos].isLetter()) pos++
                val n = src.substring(start, pos)
                skipWS()
                n
            } else {
                val c = src[pos++]; c.toString()
            }

            return dispatchCommand(name)
        }

        @Suppress("ComplexMethod")
        private fun dispatchCommand(name: String): String = when (name) {

            // ── Pecahan ─────────────────────────────────────────────────────
            "frac", "dfrac", "tfrac", "cfrac" -> {
                val num = parseArg(); val den = parseArg()
                "<mfrac>$num$den</mfrac>"
            }
            "binom" -> {
                val top = parseArg(); val bot = parseArg()
                "<mrow><mo>(</mo><mfrac linethickness=\"0\">$top$bot</mfrac><mo>)</mo></mrow>"
            }

            // ── Akar ────────────────────────────────────────────────────────
            "sqrt" -> {
                skipWS()
                if (pos < src.length && src[pos] == '[') {
                    pos++ // skip [
                    val idx = parseExpr(stopAt = ']')
                    if (pos < src.length && src[pos] == ']') pos++
                    val rad = parseArg()
                    "<mroot>$rad<mrow>$idx</mrow></mroot>"
                } else {
                    "<msqrt>${parseArg()}</msqrt>"
                }
            }

            // ── Teks ────────────────────────────────────────────────────────
            "text", "mathrm", "textrm", "mbox", "textnormal" -> {
                "<mtext>${htmlEsc(parseRawArg())}</mtext>"
            }
            "mathbf", "textbf" -> {
                val arg = parseArg()
                "<mstyle mathvariant=\"bold\">$arg</mstyle>"
            }
            "mathit", "textit" -> {
                val arg = parseArg()
                "<mstyle mathvariant=\"italic\">$arg</mstyle>"
            }
            "mathbb" -> {
                val arg = parseArg()
                "<mstyle mathvariant=\"double-struck\">$arg</mstyle>"
            }

            // ── Huruf Yunani (kecil) ─────────────────────────────────────────
            "alpha"      -> "<mi>\u03B1</mi>"
            "beta"       -> "<mi>\u03B2</mi>"
            "gamma"      -> "<mi>\u03B3</mi>"
            "delta"      -> "<mi>\u03B4</mi>"
            "epsilon"    -> "<mi>\u03F5</mi>"
            "varepsilon" -> "<mi>\u03B5</mi>"
            "zeta"       -> "<mi>\u03B6</mi>"
            "eta"        -> "<mi>\u03B7</mi>"
            "theta"      -> "<mi>\u03B8</mi>"
            "vartheta"   -> "<mi>\u03D1</mi>"
            "iota"       -> "<mi>\u03B9</mi>"
            "kappa"      -> "<mi>\u03BA</mi>"
            "lambda"     -> "<mi>\u03BB</mi>"
            "mu"         -> "<mi>\u03BC</mi>"
            "nu"         -> "<mi>\u03BD</mi>"
            "xi"         -> "<mi>\u03BE</mi>"
            "pi"         -> "<mi>\u03C0</mi>"
            "varpi"      -> "<mi>\u03D6</mi>"
            "rho"        -> "<mi>\u03C1</mi>"
            "sigma"      -> "<mi>\u03C3</mi>"
            "varsigma"   -> "<mi>\u03C2</mi>"
            "tau"        -> "<mi>\u03C4</mi>"
            "upsilon"    -> "<mi>\u03C5</mi>"
            "phi"        -> "<mi>\u03D5</mi>"
            "varphi"     -> "<mi>\u03C6</mi>"
            "chi"        -> "<mi>\u03C7</mi>"
            "psi"        -> "<mi>\u03C8</mi>"
            "omega"      -> "<mi>\u03C9</mi>"
            // Huruf Yunani (Besar)
            "Gamma"      -> "<mi>\u0393</mi>"
            "Delta"      -> "<mi>\u0394</mi>"
            "Theta"      -> "<mi>\u0398</mi>"
            "Lambda"     -> "<mi>\u039B</mi>"
            "Xi"         -> "<mi>\u039E</mi>"
            "Pi"         -> "<mi>\u03A0</mi>"
            "Sigma"      -> "<mi>\u03A3</mi>"
            "Upsilon"    -> "<mi>\u03A5</mi>"
            "Phi"        -> "<mi>\u03A6</mi>"
            "Psi"        -> "<mi>\u03A8</mi>"
            "Omega"      -> "<mi>\u03A9</mi>"

            // ── Fungsi matematika ────────────────────────────────────────────
            "sin"    -> "<mi>sin</mi>"
            "cos"    -> "<mi>cos</mi>"
            "tan"    -> "<mi>tan</mi>"
            "cot"    -> "<mi>cot</mi>"
            "sec"    -> "<mi>sec</mi>"
            "csc"    -> "<mi>csc</mi>"
            "arcsin" -> "<mi>arcsin</mi>"
            "arccos" -> "<mi>arccos</mi>"
            "arctan" -> "<mi>arctan</mi>"
            "sinh"   -> "<mi>sinh</mi>"
            "cosh"   -> "<mi>cosh</mi>"
            "tanh"   -> "<mi>tanh</mi>"
            "log"    -> "<mi>log</mi>"
            "ln"     -> "<mi>ln</mi>"
            "exp"    -> "<mi>exp</mi>"
            "lim"    -> "<mo>lim</mo>"
            "max"    -> "<mo>max</mo>"
            "min"    -> "<mo>min</mo>"
            "sup"    -> "<mo>sup</mo>"
            "inf"    -> "<mo>inf</mo>"
            "gcd"    -> "<mo>gcd</mo>"
            "lcm"    -> "<mo>lcm</mo>"
            "det"    -> "<mo>det</mo>"
            "deg"    -> "<mo>deg</mo>"
            "dim"    -> "<mo>dim</mo>"
            "ker"    -> "<mo>ker</mo>"
            "mod"    -> "<mo>mod</mo>"
            "pmod"   -> { val a = parseArg(); "<mo>(mod</mo>$a<mo>)</mo>" }

            // ── Operator besar ───────────────────────────────────────────────
            "sum"    -> "<mo>\u2211</mo>"
            "prod"   -> "<mo>\u220F</mo>"
            "int"    -> "<mo>\u222B</mo>"
            "iint"   -> "<mo>\u222C</mo>"
            "iiint"  -> "<mo>\u222D</mo>"
            "oint"   -> "<mo>\u222E</mo>"
            "bigcup" -> "<mo>\u22C3</mo>"
            "bigcap" -> "<mo>\u22C2</mo>"
            "bigvee" -> "<mo>\u22C1</mo>"
            "bigwedge" -> "<mo>\u22C0</mo>"

            // ── Operator biner ───────────────────────────────────────────────
            "times"   -> "<mo>\u00D7</mo>"
            "div"     -> "<mo>\u00F7</mo>"
            "pm"      -> "<mo>\u00B1</mo>"
            "mp"      -> "<mo>\u2213</mo>"
            "cdot"    -> "<mo>\u22C5</mo>"
            "bullet"  -> "<mo>\u2022</mo>"
            "ast"     -> "<mo>\u2217</mo>"
            "star"    -> "<mo>\u22C6</mo>"
            "circ"    -> "<mo>\u2218</mo>"
            "oplus"   -> "<mo>\u2295</mo>"
            "otimes"  -> "<mo>\u2297</mo>"
            "cup"     -> "<mo>\u222A</mo>"
            "cap"     -> "<mo>\u2229</mo>"
            "wedge", "land" -> "<mo>\u2227</mo>"
            "vee",   "lor"  -> "<mo>\u2228</mo>"

            // ── Relasi ───────────────────────────────────────────────────────
            "leq", "le"       -> "<mo>\u2264</mo>"
            "geq", "ge"       -> "<mo>\u2265</mo>"
            "neq", "ne"       -> "<mo>\u2260</mo>"
            "approx"          -> "<mo>\u2248</mo>"
            "equiv"           -> "<mo>\u2261</mo>"
            "sim"             -> "<mo>\u223C</mo>"
            "simeq"           -> "<mo>\u2243</mo>"
            "cong"            -> "<mo>\u2245</mo>"
            "propto"          -> "<mo>\u221D</mo>"
            "subset"          -> "<mo>\u2282</mo>"
            "supset"          -> "<mo>\u2283</mo>"
            "subseteq"        -> "<mo>\u2286</mo>"
            "supseteq"        -> "<mo>\u2287</mo>"
            "in"              -> "<mo>\u2208</mo>"
            "notin"           -> "<mo>\u2209</mo>"
            "parallel"        -> "<mo>\u2225</mo>"
            "perp"            -> "<mo>\u22A5</mo>"
            "mid"             -> "<mo>\u2223</mo>"
            "nmid"            -> "<mo>\u2224</mo>"
            "not"             -> "<mo>\u00AC</mo>"

            // ── Panah ────────────────────────────────────────────────────────
            "rightarrow", "to"  -> "<mo>\u2192</mo>"
            "leftarrow"         -> "<mo>\u2190</mo>"
            "Rightarrow"        -> "<mo>\u21D2</mo>"
            "Leftarrow"         -> "<mo>\u21D0</mo>"
            "leftrightarrow"    -> "<mo>\u2194</mo>"
            "Leftrightarrow"    -> "<mo>\u21D4</mo>"
            "uparrow"           -> "<mo>\u2191</mo>"
            "downarrow"         -> "<mo>\u2193</mo>"
            "mapsto"            -> "<mo>\u21A6</mo>"

            // ── Simbol lain ──────────────────────────────────────────────────
            "infty"    -> "<mi>\u221E</mi>"
            "partial"  -> "<mo>\u2202</mo>"
            "nabla"    -> "<mo>\u2207</mo>"
            "angle"    -> "<mo>\u2220</mo>"
            "triangle" -> "<mo>\u25B3</mo>"
            "square"   -> "<mo>\u25A1</mo>"
            "emptyset" -> "<mo>\u2205</mo>"
            "forall"   -> "<mo>\u2200</mo>"
            "exists"   -> "<mo>\u2203</mo>"
            "neg", "lnot" -> "<mo>\u00AC</mo>"
            "ldots"    -> "<mo>\u2026</mo>"
            "cdots"    -> "<mo>\u22EF</mo>"
            "vdots"    -> "<mo>\u22EE</mo>"
            "ddots"    -> "<mo>\u22F1</mo>"
            "prime"    -> "<mo>\u2032</mo>"
            "degree"   -> "<mo>\u00B0</mo>"
            "hbar"     -> "<mi>\u210F</mi>"

            // ── Bracket ──────────────────────────────────────────────────────
            "left"     -> parseBracket(open = true)
            "right"    -> parseBracket(open = false)
            "lfloor"   -> "<mo>\u230A</mo>"
            "rfloor"   -> "<mo>\u230B</mo>"
            "lceil"    -> "<mo>\u2308</mo>"
            "rceil"    -> "<mo>\u2309</mo>"
            "langle"   -> "<mo>\u27E8</mo>"
            "rangle"   -> "<mo>\u27E9</mo>"
            "lbrace", "{" -> "<mo>{</mo>"
            "rbrace", "}" -> "<mo>}</mo>"
            "lvert",  "|" -> "<mo>|</mo>"
            "rvert"       -> "<mo>|</mo>"

            // ── Aksen / dekorasi ─────────────────────────────────────────────
            "hat"       -> "<mover>${parseArg()}<mo>^</mo></mover>"
            "bar", "overline" -> "<mover>${parseArg()}<mo>\u203E</mo></mover>"
            "tilde"     -> "<mover>${parseArg()}<mo>~</mo></mover>"
            "vec"       -> "<mover>${parseArg()}<mo>\u2192</mo></mover>"
            "dot"       -> "<mover>${parseArg()}<mo>.</mo></mover>"
            "ddot"      -> "<mover>${parseArg()}<mo>..</mo></mover>"
            "acute"     -> "<mover>${parseArg()}<mo>\u00B4</mo></mover>"
            "grave"     -> "<mover>${parseArg()}<mo>`</mo></mover>"
            "underline" -> "<munder>${parseArg()}<mo>\u005F</mo></munder>"
            "overbrace" -> "<mover>${parseArg()}<mo>\u23DE</mo></mover>"
            "underbrace"-> "<munder>${parseArg()}<mo>\u23DF</mo></munder>"
            "widehat"   -> "<mover>${parseArg()}<mo>^</mo></mover>"
            "widetilde" -> "<mover>${parseArg()}<mo>~</mo></mover>"

            // ── Spasi ────────────────────────────────────────────────────────
            "quad"   -> "<mspace width=\"1em\"/>"
            "qquad"  -> "<mspace width=\"2em\"/>"
            ","      -> "<mspace width=\"0.167em\"/>"
            ";"      -> "<mspace width=\"0.278em\"/>"
            ":"      -> "<mspace width=\"0.222em\"/>"
            "!"      -> "<mspace width=\"-0.167em\"/>"
            " "      -> "<mspace width=\"0.25em\"/>"

            // ── Newline / alignment ──────────────────────────────────────────
            "\\"     -> "<mspace linebreak=\"newline\"/>"
            "newline"-> "<mspace linebreak=\"newline\"/>"

            // ── Warna (lewati, kembalikan konten) ────────────────────────────
            "color"  -> { parseRawArg(); parseArg() }
            "colorbox" -> { parseRawArg(); parseArg() }

            // ── Fallback ─────────────────────────────────────────────────────
            else -> "<mi>\\$name</mi>"
        }

        private fun parseBracket(open: Boolean): String {
            skipWS()
            if (pos >= src.length) return if (open) "<mo>(</mo>" else "<mo>)</mo>"
            val sym = when {
                src[pos] == '(' -> { pos++; "(" }
                src[pos] == ')' -> { pos++; ")" }
                src[pos] == '[' -> { pos++; "[" }
                src[pos] == ']' -> { pos++; "]" }
                src[pos] == '|' -> { pos++; "|" }
                src[pos] == '.' -> { pos++; return "" } // \left. invisible
                src[pos] == '\\' -> {
                    pos++
                    val start = pos
                    while (pos < src.length && src[pos].isLetter()) pos++
                    when (src.substring(start, pos)) {
                        "lfloor" -> "\u230A"
                        "rfloor" -> "\u230B"
                        "lceil"  -> "\u2308"
                        "rceil"  -> "\u2309"
                        "langle" -> "\u27E8"
                        "rangle" -> "\u27E9"
                        "vert", "|" -> "|"
                        "{" -> "{"
                        "}" -> "}"
                        else -> if (open) "(" else ")"
                    }
                }
                else -> { pos++; if (open) "(" else ")" }
            }
            return "<mo stretchy=\"true\">${htmlEsc(sym)}</mo>"
        }

        /** Baca raw string dari {…} tanpa parse MathML */
        private fun parseRawArg(): String {
            skipWS()
            if (pos >= src.length || src[pos] != '{') return ""
            pos++ // skip {
            val start = pos
            var depth = 1
            while (pos < src.length && depth > 0) {
                when (src[pos]) { '{' -> depth++; '}' -> depth-- }
                pos++
            }
            return src.substring(start, pos - 1)
        }

        private fun skipWS() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun htmlEsc(s: String) = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
