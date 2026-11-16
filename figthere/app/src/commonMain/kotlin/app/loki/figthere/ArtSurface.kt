package app.loki.figthere

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import app.loki.kit.ArtCanvas
import app.loki.kit.CanvasTextEditor
import app.loki.kit.CanvasView
import app.loki.kit.EngineClient
import app.loki.kit.LocalWorkspace
import app.loki.kit.renderScaleFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** The most the engine is asked to render per page unit: the engine caps the bitmap itself. */
const val MAX_RENDER_SCALE = 4f

/**
 * The canvas on the engine: the part of the page `loki.surface` names, as the engine renders it at the
 * zoom it is shown at (again when the zoom settles and after every change), under [CanvasTools]'s
 * overlay, with every press handed to the engine's tool in hand.
 */
@OptIn(FlowPreview::class)
@Composable
fun ArtSurface(native: SurfaceEngine, revision: Int, modifier: Modifier) {
    val ws = LocalWorkspace.current
    val client = remember(native) { EngineClient(native.transport) }
    val view = remember { CanvasView() }
    val tools = remember { CanvasTools() }
    var page by remember { mutableStateOf<Rect?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var scale by remember { mutableStateOf(1f) }
    LaunchedEffect(view) {
        snapshotFlow { view.zoom }.debounce(150).collect { z ->
            if (z > 0f) { scale = renderScaleFor(z, MAX_RENDER_SCALE); ws?.let { tools.onZoom(it, z) } }
        }
    }
    LaunchedEffect(revision, scale, tools.live) {
        withContext(Dispatchers.Default) {
            val info = runCatching { client.call("loki.surface") }.getOrNull() as? JsonObject
            fun num(k: String) = (info?.get(k) as? JsonPrimitive)?.doubleOrNull
            val w = num("width"); val h = num("height"); val x0 = num("x") ?: 0.0; val y0 = num("y") ?: 0.0
            if (w != null && h != null && w > 0 && h > 0) page = Rect(x0.toFloat(), y0.toFloat(), (x0 + w).toFloat(), (y0 + h).toFloat())
            image = native.renderPage(0, scale)?.let { rgbaToImageBitmap(it.width, it.height, it.rgba) } ?: image
        }
    }
    LaunchedEffect(ws?.state, ws?.tool, tools.live) { ws?.let { tools.onRevision(it, view) } }
    Box(modifier) {
        ArtCanvas(
            page, image, view,
            onInput = { input -> ws?.let { tools.onInput(input, it, view) } },
            modifier = Modifier.fillMaxSize(),
            panTool = tools.pans(ws?.tool.orEmpty()),
            overlay = { v -> with(tools) { draw(v, ws, 0f) } },
        )
        val edit = tools.editing
        if (edit != null && ws != null) CanvasTextEditor(edit, view.toScreen(edit.x, edit.y), onDone = { text -> tools.commitText(ws, edit, text) }, onCancel = { tools.editing = null })
    }
}
