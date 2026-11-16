//! What is FigCraft's own: the session, how methods reach it, and the page renderer.

use figcraft_geom::Rect;
use figcraft_mcp::{Backend, Headless};
use figcraft_render::View;
use serde_json::{Value, json};

use crate::Rendered;

pub(crate) struct Inner {
    h: Headless,
}

/// A session with one new, empty document.
pub(crate) fn new() -> Inner {
    // Text has no glyphs until the installed fonts are known: they are scanned the first time text needs them, as the CLI does.
    figcraft_text::scan_system_fonts_on_demand();
    Inner { h: Headless::new() }
}

/// Every control-protocol method the headless backend answers (`engine.execute`, `engine.commands`,
/// `document.inspect`, `node.get`, `ui.pointer`, `ui.key`, `ui.render`, …).
pub(crate) fn call(inner: &mut Inner, method: &str, params: Value) -> Result<Value, String> {
    // `document.inspect` and `ui.inspect` are control-protocol methods, not commands; the front end
    // reads them as state commands, so `engine.execute` reaches them too and the listing names them.
    if method == "engine.execute" {
        let cmd = params.get("command").and_then(Value::as_str);
        if let Some(m @ ("document.inspect" | "app.status")) = cmd {
            return inner.h.call(if m == "app.status" { "ui.inspect" } else { m }, params.get("params").cloned().unwrap_or_else(|| json!({})));
        }
    }
    if method == "engine.commands" {
        let mut list = inner.h.call(method, params)?;
        if let Some(a) = list.as_array_mut() {
            for (id, label, what) in [("document.inspect", "Inspect Document", "the document: pages, layers, selection, history"), ("app.status", "App Status", "the app's status: tool in hand, zoom, undo depth")] {
                a.push(json!({"id": id, "label": label, "menu": "", "shortcut": null, "params": "{}", "description": what, "undoable": false, "journal": false}));
            }
        }
        return Ok(list);
    }
    inner.h.call(method, params)
}

/// The headless backend drops the requests commands make of a window; nothing is queued for the
/// front end yet (the playbook's phase 6).
pub(crate) fn ui_requests(_inner: &mut Inner) -> Value {
    json!([])
}

/// The canvas is unbounded: the surface is the current page's content with a margin, and at least
/// this large, so an empty page shows a canvas to draw on.
const MIN_W: f64 = 1200.0;
const MIN_H: f64 = 800.0;
const MARGIN: f64 = 96.0;

/// The part of the current page that is shown, in page units.
fn region(inner: &mut Inner) -> Option<Rect> {
    let s = &inner.h.session;
    let page = s.page().ok()?;
    let doc = s.doc().ok()?;
    let b = doc.page_content_bounds(page).unwrap_or(Rect::new(0.0, 0.0, 0.0, 0.0)).inflate(MARGIN, MARGIN);
    let (w, h) = (b.width().max(MIN_W), b.height().max(MIN_H));
    let (cx, cy) = ((b.x0 + b.x1) / 2.0, (b.y0 + b.y1) / 2.0);
    Some(Rect::new(cx - w / 2.0, cy - h / 2.0, cx + w / 2.0, cy + h / 2.0))
}

/// `canvas`: the current page, as the region [`region`] gives.
pub(crate) fn surface(inner: &mut Inner) -> Value {
    match region(inner) {
        Some(r) => json!({"kind": "canvas", "pages": 1, "current": 0, "x": r.x0, "y": r.y0, "width": r.width(), "height": r.height()}),
        None => json!({"kind": "canvas", "pages": 0, "current": 0}),
    }
}

/// The surface at `scale` pixels per page unit, straight alpha.
pub(crate) fn render(inner: &mut Inner, page: u32, scale: f32, max_pixels: u64) -> Option<Rendered> {
    if page != 0 {
        return None;
    }
    let r = region(inner)?;
    let scale = f64::from(scale);
    let (w, h) = ((r.width() * scale).ceil().max(1.0), (r.height() * scale).ceil().max(1.0));
    if w * h > max_pixels as f64 || w > 16384.0 || h > 16384.0 {
        return None;
    }
    let s = &inner.h.session;
    let (page_id, doc) = (s.page().ok()?, s.doc().ok()?);
    let view = View { origin: (r.x0, r.y0), scale, width: w as u32, height: h as u32 };
    let pix = figcraft_render::render_view(doc, page_id, &view, &Default::default())?;
    let mut rgba = Vec::with_capacity(pix.pixels().len() * 4);
    for p in pix.pixels() {
        let c = p.demultiply();
        rgba.extend_from_slice(&[c.red(), c.green(), c.blue(), c.alpha()]);
    }
    Some(Rendered { width: pix.width(), height: pix.height(), pages: 1, rgba })
}
