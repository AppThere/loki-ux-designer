package app.loki.figthere

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextEditingTest {
    private fun parse(s: String) = Json.parseToJsonElement(s)

    @Test fun theCaretAndHighlightAreRead() {
        val g = parseTextEdit(parse("""{"id":2,"caret":1,"anchor":0,"caretLine":[[10,20],[10,44]],"rects":[[[1,2],[5,2],[5,9],[1,9]],[[1,2]],"junk"]}"""))!!
        assertEquals(listOf(Offset(10f, 20f), Offset(10f, 44f)), g.caret)
        assertEquals(1, g.rects.size, "a short one and a junk one are dropped")
    }

    @Test fun noTextBeingEditedIsNull() {
        assertNull(parseTextEdit(null)); assertNull(parseTextEdit(parse("null"))); assertNull(parseTextEdit(parse("[]")))
        assertNull(parseTextEdit(parse("""{"caretLine":[[1,2]],"rects":[]}"""))!!.caret, "one point is not a caret")
    }

    @Test fun editingKeysHaveTheEnginesNames() {
        assertEquals("Backspace", editKeyName(Key.Backspace)); assertEquals("Left", editKeyName(Key.DirectionLeft)); assertEquals("Enter", editKeyName(Key.NumPadEnter))
        assertNull(editKeyName(Key.A)); assertNull(editKeyName(Key.ShiftLeft))
    }

    @Test fun pressesInARunAreCounted() {
        val c = ClickRun()
        assertEquals(1, c.press(1000, Offset(10f, 10f))); assertEquals(2, c.press(1200, Offset(12f, 11f))); assertEquals(3, c.press(1350, Offset(12f, 11f)))
        assertEquals(3, c.press(1500, Offset(12f, 11f))); assertEquals(1, c.press(2500, Offset(12f, 11f))); assertEquals(1, c.press(2600, Offset(300f, 11f)))
    }
}
