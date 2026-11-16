package app.loki.figthere

// This file is the app's own (egui2compose writes it once). FigCraft is a design tool: layers on pages;
// the tools are the engine's (`tool.*`), the canvas sends pointer events to it (`ui.pointer`).
// Written ONCE by egui2compose for an app with the art workspace and never written again. It says what the generic workspace cannot know: which
// command describes the document, which panels the dock has and what they draw, how a tool is
// picked, and what the status bar says. The defaults below work for any engine; replace them.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.unit.dp
import app.loki.kit.CanvasInput
import app.loki.kit.CanvasPhase
import app.loki.kit.CanvasView
import app.loki.kit.ColorPickerDialog
import app.loki.kit.ColorWell
import app.loki.kit.DockPanel
import app.loki.kit.EngineClient
import app.loki.kit.InputDevice
import app.loki.kit.ModifierChips
import app.loki.kit.NumberChip
import app.loki.kit.RailTool
import app.loki.kit.WorkspaceContext
import app.loki.kit.hexOf
import app.loki.kit.parseHex
import com.composables.icons.lucide.FileStack
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SlidersHorizontal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Read after every change: the document (pages, layers, selection) and the app's status (the tool in hand). */
val STATE_COMMANDS: List<String> = listOf("document.inspect", "app.status")

private fun JsonElement?.obj() = this as? JsonObject
private fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()
private fun JsonElement?.str(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.num(key: String): Double? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.doubleOrNull
private fun JsonElement?.long(key: String): Long? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.longOrNull
private fun JsonElement?.bool(key: String): Boolean? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.booleanOrNull

fun docOf(state: JsonElement?): JsonElement? = (state as? JsonObject)?.get("document.inspect")
private fun status(state: JsonElement?): JsonElement? = (state as? JsonObject)?.get("app.status").obj()?.get("status")

val PANEL_COMMANDS: Set<String> = setOf("select.set", "node.set", "fill.set", "page.select", "page.add", "edit.delete", "edit.duplicate", "tool.move", "view.set", "file.open", "file.save", "file.export")

val FIRST_PANEL: String? = "layers"

val PANEL_GROUPS: List<List<String>> = listOf(listOf("layers", "properties", "pages"))

fun documentTitle(state: JsonElement?): String = docOf(state).str("name") ?: APP_NAME

fun documentDirty(state: JsonElement?): Boolean = docOf(state).bool("dirty") == true

fun statusSegments(state: JsonElement?): List<String> {
    val d = docOf(state)
    val sel = d.obj()?.get("selection").arr().size
    return listOfNotNull(
        currentPage(state)?.str("name"),
        if (sel > 0) "$sel selected" else null,
        status(state).num("zoom")?.let { "${(it * 100).toInt()}%" },
    )
}

private fun currentPage(state: JsonElement?): JsonElement? {
    val d = docOf(state)
    val id = d.long("currentPage")
    return d.obj()?.get("pages").arr().firstOrNull { it.long("id") == id } ?: d.obj()?.get("pages").arr().firstOrNull()
}

/** The tools of the palette that make something or move it, in the desktop's order. */
private val RAIL: List<String> = listOf("move", "scale", "hand", "frame", "section", "rectangle", "ellipse", "line", "arrow", "polygon", "star", "pen", "pencil", "text", "comment")

fun loadTools(engine: EngineClient): List<RailTool> = RAIL.mapNotNull { id -> TOOLS.firstOrNull { it.id.equals(id, true) } }.map { RailTool(it.id, it.title, icon = toolIcon(it.id)) }

fun toolCommand(id: String): Pair<String, JsonObject>? = ("tool." + id.replaceFirstChar { it.lowercase() }) to JsonObject(emptyMap())

fun toolInHand(state: JsonElement?): String? = status(state).str("tool")?.let { t -> TOOLS.firstOrNull { it.id.equals(t, true) }?.id ?: t }

/** The selected layer's own box: a position, a size, a turn and an opacity to set, and its fill. */
@Composable
fun OptionsBar(tool: String, tools: List<RailTool>, state: JsonElement?, run: (String, JsonObject) -> Unit) {
    val node = selectedNode(state)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ModifierChips()
        if (node == null) {
            Text(tools.firstOrNull { it.id.equals(tool, true) }?.label ?: tool, style = MaterialTheme.typography.labelLarge)
        } else {
            val id = node.long("id") ?: return@Row
            fun set(k: String, v: Double) = run("node.set", buildJsonObject { put("ids", buildJsonArray { add(JsonPrimitive(id)) }); put(k, v) })
            NumberChip("X", node.num("x"), -100000.0, 100000.0, { set("x", it) }, decimals = 1)
            NumberChip("Y", node.num("y"), -100000.0, 100000.0, { set("y", it) }, decimals = 1)
            NumberChip("W", node.num("width"), 0.0, 100000.0, { set("width", it) }, decimals = 1)
            NumberChip("H", node.num("height"), 0.0, 100000.0, { set("height", it) }, decimals = 1)
            NumberChip("Turn", node.num("rotation") ?: 0.0, -360.0, 360.0, { set("rotation", it) }, unit = "°")
            FillWell(node, run)
        }
    }
}

@Composable
private fun FillWell(node: JsonElement, run: (String, JsonObject) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val fill = node.obj()?.get("fills").arr().firstOrNull()
    val color = if (fill.str("type") == "solid") fill.str("color")?.let { parseHex(it) } else null
    ColorWell(color, "Fill", Modifier.size(28.dp)) { open = true }
    if (open) ColorPickerDialog("Fill", color, onPick = { c ->
        open = false
        val id = node.long("id") ?: return@ColorPickerDialog
        val cmd = if (c == null) "fill.remove" else if (fill == null) "fill.add" else "fill.set"
        val params = buildJsonObject {
            put("ids", buildJsonArray { add(JsonPrimitive(id)) })
            if (c != null) put("color", hexOf(c)) else put("index", 0)
            if (c != null && fill != null) put("index", 0)
        }
        run(cmd, params)
    }, onDismiss = { open = false }, allowNone = true)
}

/** The first selected layer, found in the current page's tree. */
private fun selectedNode(state: JsonElement?): JsonElement? {
    val want = docOf(state).obj()?.get("selection").arr().firstOrNull()?.let { (it as? JsonPrimitive)?.longOrNull } ?: return null
    fun find(n: JsonElement?): JsonElement? {
        if (n.long("id") == want) return n
        for (c in n.obj()?.get("children").arr()) find(c)?.let { return it }
        return null
    }
    return find(currentPage(state))
}

@Composable
fun ToolFooter(state: JsonElement?, run: (String, JsonObject) -> Unit) {}

// Open and Save are the desktop front end's file dialogs; the engine takes paths.
val APP_ACTIONS: List<app.loki.kit.RibbonItem> = listOf(
    app.loki.kit.RibbonItem("app.open", "Open…", ""),
    app.loki.kit.RibbonItem("app.save", "Save", ""),
    app.loki.kit.RibbonItem("app.saveAs", "Save As…", ""),
    app.loki.kit.RibbonItem("app.exportPng", "Export as PNG…", ""),
)
val APP_ACTION_IDS: Set<String> = APP_ACTIONS.map { it.id }.toSet()

fun withAppActions(tabs: List<app.loki.kit.RibbonTab>): List<app.loki.kit.RibbonTab> = app.loki.kit.addToFileMenu(tabs, APP_ACTIONS)

private fun baseName(state: JsonElement?) = (docOf(state).str("name") ?: "Untitled").substringBeforeLast('.').ifBlank { "Untitled" }

suspend fun appAction(id: String, state: JsonElement?, io: app.loki.kit.FileIo) {
    val path = docOf(state).str("path")
    fun exec(command: String, params: JsonObject) = buildJsonObject { put("command", command); put("params", params) }
    suspend fun saveTo(p: String, command: String, params: JsonObject) {
        if (io.call("engine.execute", exec(command, params)) != null) { io.publish(p); io.refresh(); io.status("Saved ${p.substringAfterLast('/')}") }
    }
    when (id) {
        "app.open" -> {
            val p = io.pick(false, "") ?: return
            if (io.call("engine.execute", exec("file.open", buildJsonObject { put("path", p) })) != null) io.refresh()
        }
        "app.save" -> if (path != null) saveTo(path, "file.save", buildJsonObject { put("path", path) }) else appAction("app.saveAs", state, io)
        "app.saveAs" -> {
            val p = io.pick(true, "${baseName(state)}.fig") ?: return
            saveTo(p, "file.save", buildJsonObject { put("path", p) })
        }
        "app.exportPng" -> {
            val p = io.pick(true, "${baseName(state)}.png") ?: return
            saveTo(p, "file.export", buildJsonObject { put("path", p); put("format", "png") })
        }
    }
}

fun dockPanels(state: JsonElement?, revision: Int, run: (String, JsonObject) -> Unit): List<DockPanel> = listOf(
    DockPanel("layers", "Layers", Lucide.Layers) { m -> LayersPanel(state, run, m) },
    DockPanel("properties", "Properties", Lucide.SlidersHorizontal) { m -> PropertiesPanel(state, run, m) },
    DockPanel("pages", "Pages", Lucide.FileStack) { m -> PagesPanel(state, run, m) },
)

private fun exec(run: (String, JsonObject) -> Unit, command: String, params: JsonObject) =
    run(command, params)

private fun idList(vararg ids: Long) = buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }

/** The current page's layers, top first, nested by depth; a tap selects one. */
@Composable
private fun LayersPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val selected = docOf(state).obj()?.get("selection").arr().mapNotNull { (it as? JsonPrimitive)?.longOrNull }.toSet()
    val rows = mutableListOf<Pair<Int, JsonElement>>()
    fun walk(n: JsonElement?, depth: Int) { for (c in n.obj()?.get("children").arr().asReversed()) { rows += depth to c; walk(c, depth + 1) } }
    walk(currentPage(state), 0)
    if (rows.isEmpty()) { Text("Nothing on this page yet: pick a tool and draw on the canvas.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    LazyColumn(modifier) {
        items(rows) { (depth, n) ->
            val id = n.long("id") ?: return@items
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { exec(run, "select.set", buildJsonObject { put("ids", idList(id)) }) }.padding(start = (12 + depth * 16).dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(n.str("name") ?: "Layer", Modifier.weight(1f), maxLines = 1, fontWeight = if (id in selected) FontWeight.Bold else FontWeight.Normal)
                Text((n.str("type") ?: "").lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PropertiesPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val node = selectedNode(state)
    if (node == null) { Text("Select a layer.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val id = node.long("id") ?: return
    fun set(k: String, v: Any) = exec(run, "node.set", buildJsonObject { put("ids", idList(id)); when (v) { is Double -> put(k, v); is Boolean -> put(k, v); is String -> put(k, v) } })
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(node.str("name") ?: "Layer", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberChip("Opacity", (node.num("opacity") ?: 1.0) * 100, 0.0, 100.0, { set("opacity", it / 100) }, unit = "%", step = 5.0)
            NumberChip("Radius", node.num("cornerRadius") ?: 0.0, 0.0, 10000.0, { set("cornerRadius", it) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.TextButton({ exec(run, "edit.duplicate", JsonObject(emptyMap())) }) { Text("Duplicate") }
            androidx.compose.material3.TextButton({ exec(run, "edit.delete", JsonObject(emptyMap())) }) { Text("Delete") }
        }
    }
}

@Composable
private fun PagesPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val pages = docOf(state).obj()?.get("pages").arr()
    val current = docOf(state).long("currentPage")
    Column(modifier) {
        androidx.compose.material3.TextButton({ exec(run, "page.add", JsonObject(emptyMap())) }) { Text("Add page") }
        LazyColumn {
            items(pages) { p ->
                val id = p.long("id") ?: return@items
                Text(p.str("name") ?: "Page", fontWeight = if (id == current) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { exec(run, "page.select", buildJsonObject { put("id", id) }) }.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
    }
}

/**
 * The canvas's pointer: every press, move and lift goes to the engine's tool in hand as `ui.pointer`
 * (page coordinates), one event per call in order; a run of moves is sent as the last of them.
 */
class CanvasTools {
    var live by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    /** Where the caret and the highlight are while a text layer is edited (the engine edits it, `text.edit.*`); null when none is. */
    var text by androidx.compose.runtime.mutableStateOf<TextGeometry?>(null)
        private set
    /** The caret blinks, so the canvas draws each frame while text is edited. */
    val animating get() = text != null
    private val clicks = ClickRun()
    private val clock = kotlin.time.TimeSource.Monotonic.markNow()
    private var swallowUp = false
    private var overlay by androidx.compose.runtime.mutableStateOf<JsonElement?>(null)
    private val pending = ArrayDeque<JsonObject>()
    private var inFlight = false
    private var ending = false

    fun pans(tool: String) = tool.equals("hand", true)

    fun claims(tool: String, x: Double, y: Double, view: CanvasView) = false

    /** The engine measures tolerances in screen pixels through the zoom. */
    fun onZoom(ws: WorkspaceContext, zoom: Float) {
        ws.call("engine.execute", buildJsonObject { put("command", "view.set"); put("params", buildJsonObject { put("zoom", zoom.toDouble()) }) }) {}
    }

    fun onRevision(ws: WorkspaceContext, view: CanvasView) = readOverlay(ws)

    private fun readOverlay(ws: WorkspaceContext) {
        ws.call("ui.inspect", JsonObject(emptyMap())) { overlay = it.obj()?.get("tool").obj()?.get("overlay") }
        ws.call("engine.execute", buildJsonObject { put("command", "text.edit.selection"); put("params", JsonObject(emptyMap())) }) { text = parseTextEdit(it) }
    }

    private fun event(kind: String, p: app.loki.kit.CanvasPoint, i: CanvasInput) = buildJsonObject {
        put("kind", kind); put("x", p.x); put("y", p.y)
        if (i.device == InputDevice.Pen) put("pressure", p.pressure.toDouble())
        put("shift", i.mods.shift); put("alt", i.mods.alt); put("cmd", i.mods.command)
    }

    fun onInput(i: CanvasInput, ws: WorkspaceContext, view: CanvasView) {
        val p = i.points.lastOrNull() ?: return
        when (i.phase) {
            CanvasPhase.Hover -> if (pending.isEmpty() && !inFlight) { pending += event("hover", p, i); flush(ws) }
            CanvasPhase.Down -> {
                // A second press close in time and place is a double click (it enters a text layer's text); the lift after it is not sent.
                val run = clicks.press(clock.elapsedNow().inWholeMilliseconds, androidx.compose.ui.geometry.Offset(p.x.toFloat(), p.y.toFloat()))
                if (run >= 2) { pending += event(if (run == 2) "doubleclick" else "tripleclick", p, i); swallowUp = true } else pending += event("down", p, i)
                flush(ws)
            }
            CanvasPhase.Drag -> {
                // Moves wait for the one in flight: only the newest is kept.
                if (pending.lastOrNull().str("kind") == "move") pending.removeLast()
                pending += event("move", p, i); flush(ws)
            }
            CanvasPhase.Up, CanvasPhase.Cancel -> {
                if (swallowUp) swallowUp = false else pending += event("up", p, i)
                ending = true; flush(ws)
            }
        }
    }

    private fun flush(ws: WorkspaceContext) {
        if (inFlight) return
        val e = pending.removeFirstOrNull() ?: return
        inFlight = true
        ws.call("ui.pointer", e) {
            inFlight = false
            if (e.str("kind") != "move" && e.str("kind") != "hover") live++
            if (pending.isEmpty() && ending) { ending = false; ws.refresh() }
            flush(ws)
        }
    }

    /** The tool's feedback over the picture: a rubber band for a marquee, the shape being drawn. */
    fun DrawScope.draw(view: CanvasView, ws: WorkspaceContext?, phase: Float) {
        val accent = Color(0xFF4C9AFF)
        // The text being edited: the highlight of a range, and the caret (blinking, steady while a key is pressed).
        text?.let { t ->
            for (r in t.rects) {
                val q = r.map { view.toScreen(it.x.toDouble(), it.y.toDouble()) }
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(q[0].x, q[0].y); for (k in 1..3) lineTo(q[k].x, q[k].y); close() }
                drawPath(path, accent.copy(alpha = 0.3f))
            }
            if ((phase / 9f).toInt() % 2 == 0) t.caret?.let { c ->
                drawLine(Color(0xFF111111), view.toScreen(c[0].x.toDouble(), c[0].y.toDouble()), view.toScreen(c[1].x.toDouble(), c[1].y.toDouble()), strokeWidth = 2f)
            }
        }
        val o = overlay.obj() ?: return
        fun pt(e: JsonElement?): Offset? {
            val a = e as? JsonArray ?: return null
            val x = (a.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return null
            val y = (a.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return null
            return view.toScreen(x, y)
        }
        // A marquee: [x0, y0, x1, y1] or {x, y, width, height}; whichever the engine gives.
        val m = o["marquee"]
        if (m is JsonArray && m.size >= 4) {
            val v = m.map { (it as? JsonPrimitive)?.doubleOrNull ?: return }
            val a = view.toScreen(v[0], v[1]); val b = view.toScreen(v[2], v[3])
            drawRect(accent.copy(alpha = 0.15f), Offset(minOf(a.x, b.x), minOf(a.y, b.y)), androidx.compose.ui.geometry.Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)))
            drawRect(accent, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), androidx.compose.ui.geometry.Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = Stroke(1.5f))
        }
        for (e in o["pen"].arr()) pt(e)?.let { drawCircle(accent, 4f, it) }
    }

/** A key while text is edited goes to the engine's editor: an editing key as `text.edit.key`, a character as `text.edit.insert`; a shortcut is left to the window. */
    fun onKey(e: androidx.compose.ui.input.key.KeyEvent, ws: WorkspaceContext): Boolean {
        if (text == null || e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return false
        val cmd = e.isCtrlPressed || e.isMetaPressed
        val name = editKeyName(e.key)
        when {
            cmd && e.key == androidx.compose.ui.input.key.Key.A -> ws.run("text.edit.key", buildJsonObject { put("key", "SelectAll") }) {}
            cmd -> return false
            name != null -> ws.run("text.edit.key", buildJsonObject { put("key", name); put("shift", e.isShiftPressed); put("alt", e.isAltPressed) }) {}
            e.utf16CodePoint in 32..0xFFFF && e.utf16CodePoint != 127 && !e.isAltPressed -> ws.run("text.edit.insert", buildJsonObject { put("text", e.utf16CodePoint.toChar().toString()) }) {}
            else -> return false
        }
        return true
    }

    /** What the soft keyboard did, as the engine's editor hears it (a composition is a preedit until it is final). */
    fun onIme(a: app.loki.kit.ImeAction, ws: WorkspaceContext) {
        when (a) {
            is app.loki.kit.ImeAction.Backspace -> repeat(a.count) { ws.run("text.edit.key", buildJsonObject { put("key", "Backspace") }) {} }
            is app.loki.kit.ImeAction.Commit -> ws.run("text.edit.insert", buildJsonObject { put("text", a.text) }) {}
            is app.loki.kit.ImeAction.Preedit -> ws.run("text.edit.preedit", buildJsonObject { put("text", a.text); a.cursor?.let { put("cursor", it) } }) {}
        }
    }
}
