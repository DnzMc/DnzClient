package dnz.launcher

/**
 * Turns a Modrinth description (Markdown with some HTML) into simple lines the launcher can draw.
 * Images, badges and HTML are dropped, links keep their text; long descriptions are cut.
 */
object Markdown {
    enum class Kind { Text, Heading, Bullet, Quote, Code, Blank }

    data class Line(val kind: Kind, val text: String, val level: Int = 0)

    private val comment = Regex("(?s)<!--.*?-->")
    private val linkedImage = Regex("\\[!\\[[^\\]]*]\\([^)]*\\)]\\([^)]*\\)")
    private val image = Regex("!\\[[^\\]]*]\\([^)]*\\)")
    private val link = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val lineBreak = Regex("(?i)<br\\s*/?>")
    private val blockEnd = Regex("(?i)</(p|div|h[1-6]|li|ul|ol|table|tr|details|summary)>")
    private val tag = Regex("<[^>]+>")
    private val bullet = Regex("^[-*+]\\s+")
    private val rule = Regex("^[-*_=]{3,}$")
    private val tableDivider = Regex("^\\|?[\\s:|-]+\\|?$")

    /** The lines, and whether the text was cut short. */
    fun lines(markdown: String, max: Int = 140): Pair<List<Line>, Boolean> {
        val text = markdown.replace("\r", "")
            .replace(comment, "")
            .replace(linkedImage, "")
            .replace(image, "")
            .replace(link, "$1")
            .replace(lineBreak, "\n")
            .replace(blockEnd, "\n")
            .replace(tag, "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")

        val out = mutableListOf<Line>()
        var inCode = false
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("```")) {
                inCode = !inCode
                continue
            }
            if (inCode) {
                out += Line(Kind.Code, raw.trimEnd())
            } else if (line.isEmpty() || rule.matches(line) || (line.startsWith("|") && tableDivider.matches(line))) {
                if (out.isNotEmpty() && out.last().kind != Kind.Blank) out += Line(Kind.Blank, "")
            } else if (line.startsWith("#")) {
                val level = line.takeWhile { it == '#' }.length
                out += Line(Kind.Heading, clean(line.drop(level)), level.coerceAtMost(3))
            } else if (bullet.containsMatchIn(line)) {
                out += Line(Kind.Bullet, clean(line.replaceFirst(bullet, "")))
            } else if (line.startsWith(">")) {
                out += Line(Kind.Quote, clean(line.trimStart('>', ' ')))
            } else if (line.startsWith("|")) {
                out += Line(Kind.Text, line.trim('|').split('|').joinToString("   ") { clean(it) })
            } else {
                out += Line(Kind.Text, clean(line))
            }
            if (out.size >= max) return out to true
        }
        return out.dropLastWhile { it.kind == Kind.Blank } to false
    }

    private fun clean(text: String) = text.replace("**", "").replace("__", "").replace("`", "").trim()
}
