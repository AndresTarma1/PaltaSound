package example.nucleus.utils.cipher

object NativePlayerScriptAnalyzer {

    data class Analysis(
        val nClassName: String,
        val sigExpression: String,
    )

    fun analyze(playerJs: String): Analysis {
        val nClassName = extractNClassName(playerJs)
            ?: throw CipherException("Native analyzer: n-class not found in player.js")
        val sigExpression = extractSigExpression(playerJs)
            ?: throw CipherException("Native analyzer: signature expression not found in player.js")
        return Analysis(nClassName, sigExpression)
    }

    private fun extractNClassName(playerJs: String): String? {
        val idx = playerJs.indexOf(".get(\"n\")")
        if (idx < 0) return null
        val prefix = playerJs.substring(0, idx)
        val newIdx = prefix.lastIndexOf("new g.")
        if (newIdx < 0) return null
        val nameStart = newIdx + "new g.".length
        val openParen = playerJs.indexOf('(', nameStart)
        if (openParen < 0 || openParen > idx) return null
        val name = playerJs.substring(nameStart, openParen).trim()
        if (!name.matches(Regex("[A-Za-z_$][\\w$]*"))) return null
        return name
    }

    private fun extractSigExpression(playerJs: String): String? {
        // YouTube antepone otras llamadas a `set("alr","yes")` (redirector, DD) que no son
        // la función de firma: probar TODAS las ocurrencias y quedarse con la primera que
        // tenga la forma `c&&(c=<expr>,...)` completa. Antes solo se probaba la primera y
        // cualquier señuelo anterior rompía el análisis ("signature expression not found").
        val marker = "set(\"alr\",\"yes\");"
        var fromIndex = 0
        while (true) {
            val markerIdx = playerJs.indexOf(marker, fromIndex)
            if (markerIdx < 0) return null
            trySigCandidate(playerJs, markerIdx)?.let { return it }
            fromIndex = markerIdx + marker.length
        }
    }

    private fun trySigCandidate(playerJs: String, markerIdx: Int): String? {
        val marker = "set(\"alr\",\"yes\");"
        val afterMarker = playerJs.substring(markerIdx + marker.length)
        val andIdx = afterMarker.indexOf("&&(")
        if (andIdx < 0 || andIdx > 256) return null

        val openParen = markerIdx + marker.length + andIdx + 3
        var depth = 1
        var i = openParen
        while (i < playerJs.length && depth > 0) {
            when (playerJs[i]) {
                '(' -> depth++
                ')' -> depth--
            }
            i++
        }
        if (depth != 0) return null
        val inner = playerJs.substring(openParen, i - 1).trim()
        if (!inner.contains("=")) return null
        val rhs = inner.substringAfter("=").trim()
        if (rhs.isBlank()) return null

        val expr = topLevelUntilComma(rhs)
        val inputVar = extractInputParamName(playerJs, markerIdx) ?: "c"
        return replaceWholeWord(expr, inputVar, "input")
    }

    private fun topLevelUntilComma(s: String): String {
        var depth = 0
        for (i in s.indices) {
            when (s[i]) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 0) return s.substring(0, i).trim()
            }
        }
        return s.trim()
    }

    private fun extractInputParamName(playerJs: String, markerIdx: Int): String? {
        val funcKeyword = playerJs.lastIndexOf("function", markerIdx)
        if (funcKeyword < 0) return null
        val open = playerJs.indexOf('(', funcKeyword)
        if (open < 0 || open > markerIdx) return null
        val close = playerJs.indexOf(')', open)
        if (close < 0 || close > markerIdx) return null
        val params = playerJs.substring(open + 1, close).split(",")
        if (params.size < 3) return null
        return params[2].trim().takeIf { it.matches(Regex("[A-Za-z_$][\\w$]*")) }
    }

    private fun replaceWholeWord(text: String, from: String, to: String): String {
        if (from == to) return text
        val regex = Regex("\\b${Regex.escape(from)}\\b")
        return regex.replace(text, to)
    }
}
