package app.loki.figthere

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull

/**
 * Where the caret and the highlight are while a text layer is edited, in page points, from
 * `text.edit.selection`: a segment `[top, bottom]` and a quadrilateral per selected line. A null
 * [caret] means the layer has no layout to show one on.
 */
class TextGeometry(val caret: List<Offset>?, val rects: List<List<Offset>>)

private fun point(e: JsonElement?): Offset? {
    val a = e as? JsonArray ?: return null
    val x = (a.getOrNull(0) as? JsonPrimitive)?.floatOrNull ?: return null
    val y = (a.getOrNull(1) as? JsonPrimitive)?.floatOrNull ?: return null
    return if (x.isFinite() && y.isFinite()) Offset(x, y) else null
}

/** `text.edit.selection`'s answer; null when no text is being edited (the engine answers null). */
fun parseTextEdit(answer: JsonElement?): TextGeometry? {
    val o = answer as? JsonObject ?: return null
    if (answer is JsonNull) return null
    val caret = (o["caretLine"] as? JsonArray)?.mapNotNull { point(it) }?.takeIf { it.size == 2 }
    val rects = (o["rects"] as? JsonArray).orEmpty().mapNotNull { r -> (r as? JsonArray)?.mapNotNull { point(it) }?.takeIf { it.size == 4 } }
    return TextGeometry(caret, rects)
}

/** The engine's name (`text.edit.key`) for a key, or null for one that is not an editing key. */
fun editKeyName(k: Key): String? = when (k) {
    Key.Backspace -> "Backspace"; Key.Delete -> "Delete"; Key.Enter, Key.NumPadEnter -> "Enter"; Key.Tab -> "Tab"; Key.Escape -> "Escape"
    Key.DirectionLeft -> "Left"; Key.DirectionRight -> "Right"; Key.DirectionUp -> "Up"; Key.DirectionDown -> "Down"
    Key.MoveHome -> "Home"; Key.MoveEnd -> "End"
    else -> null
}

/** Presses in a row: a press within [WINDOW] ms and [REACH] units of the last one adds to the run, up to three. */
class ClickRun {
    private var time = Long.MIN_VALUE / 2
    private var at = Offset.Zero
    private var n = 0

    fun press(nowMillis: Long, where: Offset): Int {
        n = if (nowMillis - time <= WINDOW && (where - at).getDistance() <= REACH) (n + 1).coerceAtMost(3) else 1
        time = nowMillis; at = where
        return n
    }

    companion object { const val WINDOW = 400L; const val REACH = 12f }
}
