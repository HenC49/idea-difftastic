package com.github.henc.difftastic.engine

import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

/**
 * Wire model for `difft --display json` (difftastic >= 0.60, format gated by DFT_UNSTABLE).
 *
 * Upstream reference: src/display/json.rs. One file pair produces a single object;
 * directory mode produces an array (not used by this plugin, but tolerated).
 */
data class DifftJsonFile(
    /** Full-file line alignment as [lhsLineNumber|null, rhsLineNumber|null] pairs, 0-based. */
    @SerializedName("aligned_lines") val alignedLines: List<List<Int?>>? = null,
    /** Hunks containing only the lines that carry novel (added/changed) atoms. */
    @SerializedName("chunks") val chunks: List<List<DifftJsonLine>>? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("path") val path: String? = null,
    /** "unchanged" | "changed" | "created" | "deleted". */
    @SerializedName("status") val status: String? = null,
)

data class DifftJsonLine(
    @SerializedName("lhs") val lhs: DifftJsonSide? = null,
    @SerializedName("rhs") val rhs: DifftJsonSide? = null,
)

data class DifftJsonSide(
    /** 0-based line number. */
    @SerializedName("line_number") val lineNumber: Int? = null,
    @SerializedName("changes") val changes: List<DifftJsonChange> = emptyList(),
)

data class DifftJsonChange(
    /** Start byte offset of the atom within the line, inclusive (UTF-8). */
    @SerializedName("start") val start: Int,
    /** End byte offset of the atom within the line, exclusive (UTF-8). */
    @SerializedName("end") val end: Int,
    @SerializedName("content") val content: String? = null,
    /** One of: delimiter, normal, string, type, comment, keyword, tree_sitter_error. */
    @SerializedName("highlight") val highlight: String? = null,
)

object DifftResultParser {
    /**
     * Parses difft JSON against the actual compared texts and returns a normalized
     * [DifftResult]. Line numbers become 0-based, byte offsets become char offsets,
     * and spans are sorted deterministically (difft emits chunks in BTreeMap order).
     */
    fun parse(json: String, leftText: String, rightText: String): DifftResult {
        val element = JsonParser.parseString(json)
        val file = GSON.fromJson<DifftJsonFile>(
            if (element.isJsonArray) element.asJsonArray.firstOrNull() ?: return DifftResult.Unsupported("empty difft output") else element,
            DifftJsonFile::class.java,
        )
        return when (file.status) {
            "unchanged" -> DifftResult.Unchanged

            "created", "deleted" -> DifftResult.Changed(
                alignment = null,
                spans = parseSpans(file, leftText, rightText),
                language = file.language,
            )

            "changed" -> {
                val aligned = file.alignedLines
                if (aligned.isNullOrEmpty()) {
                    DifftResult.Unsupported("difft returned no line alignment (likely an internal text-diff fallback)")
                } else {
                    DifftResult.Changed(
                        alignment = normalizeAlignment(aligned, leftText, rightText),
                        spans = parseSpans(file, leftText, rightText),
                        language = file.language,
                    )
                }
            }

            else -> DifftResult.Unsupported("unknown difft status: ${file.status}")
        }
    }

    private fun normalizeAlignment(
        alignedLines: List<List<Int?>>,
        leftText: String,
        rightText: String,
    ): List<Pair<Int?, Int?>> = alignedLines.map { pair ->
        require(pair.size == 2) { "aligned_lines entry must have exactly two elements: $pair" }
        // difft emits 0-based line numbers and counts lines as if every file ends
        // with a newline, so a phantom trailing line can appear; out-of-range
        // indices become null on that side.
        val left = pair[0]?.takeIf { it in 0 until TextLines.count(leftText) }
        val right = pair[1]?.takeIf { it in 0 until TextLines.count(rightText) }
        left to right
    }.filter { it.first != null || it.second != null }

    private fun parseSpans(file: DifftJsonFile, leftText: String, rightText: String): List<DifftSpan> {
        val leftLines = TextLines.split(leftText)
        val rightLines = TextLines.split(rightText)
        val spans = ArrayList<DifftSpan>()

        for (line in file.chunks.orEmpty().asSequence().flatten()) {
            for ((side, jsonSide) in listOf(DifftSide.LEFT to line.lhs, DifftSide.RIGHT to line.rhs)) {
                val lineIndex = jsonSide?.lineNumber ?: continue
                val lines = if (side == DifftSide.LEFT) leftLines else rightLines
                if (lineIndex !in lines.indices) continue
                val lineText = lines[lineIndex]
                for (change in jsonSide.changes) {
                    val start = Utf8Offsets.byteToCharOffset(lineText, change.start)
                    val end = Utf8Offsets.byteToCharOffset(lineText, change.end)
                    if (start >= end) continue
                    spans.add(DifftSpan(side, lineIndex, start, end, DifftHighlight.fromWire(change.highlight)))
                }
            }
        }
        return spans.sortedWith(compareBy({ it.side }, { it.line }, { it.start }, { it.end }))
    }

    private val GSON = com.google.gson.Gson()
}
