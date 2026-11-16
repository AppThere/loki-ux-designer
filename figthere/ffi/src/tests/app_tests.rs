//! FigCraft's own: a session starts with a page, and a shape drawn on it renders.

use super::*;

fn exec(s: *mut LokiSession, command: &str, params: serde_json::Value) -> serde_json::Value {
    let body = serde_json::json!({"command": command, "params": params}).to_string();
    let r = call(s, "engine.execute", Some(&body));
    assert_eq!(r["ok"], true, "{command}: {r}");
    r["result"].clone()
}

#[test]
fn a_new_session_has_an_empty_canvas_that_renders() {
    let s = loki_session_new();
    let info = call(s, "loki.surface", None)["result"].clone();
    assert_eq!(info["pages"], 1, "{info}");
    assert!(info["width"].as_f64().unwrap_or(0.0) >= 1200.0, "{info}");
    let (mut w, mut h, mut n) = (0, 0, 0);
    // SAFETY: valid session and out-pointers.
    let px = unsafe { loki_render_page(s, 0, 0.25, &mut w, &mut h, &mut n) };
    assert!(!px.is_null() && w > 0 && h > 0 && n == 1);
    unsafe { loki_buffer_free(px, w as usize * h as usize * 4) };
    // Past the one page: no picture, no crash.
    assert!(unsafe { loki_render_page(s, 1, 1.0, &mut w, &mut h, &mut n) }.is_null());
    unsafe { loki_session_free(s) };
}

#[test]
fn a_drawn_shape_shows_in_the_render() {
    let s = loki_session_new();
    let before = call(s, "loki.surface", None)["result"].clone();
    let _ = exec(s, "node.create", serde_json::json!({"type": "rectangle", "x": 100, "y": 100, "width": 300, "height": 200}));
    let after = call(s, "loki.surface", None)["result"].clone();
    assert_eq!(before["pages"], after["pages"]);
    let (mut w, mut h, mut n) = (0, 0, 0);
    // SAFETY: valid session and out-pointers.
    let px = unsafe { loki_render_page(s, 0, 0.5, &mut w, &mut h, &mut n) };
    assert!(!px.is_null());
    // SAFETY: a buffer of w*h*4 bytes just returned.
    let bytes = unsafe { std::slice::from_raw_parts(px, w as usize * h as usize * 4) };
    let painted = bytes.chunks(4).any(|p| p[3] > 0 && (p[0] != p[1] || p[1] != p[2] || p[0] < 250));
    unsafe { loki_buffer_free(px, w as usize * h as usize * 4) };
    assert!(painted, "the rectangle did not reach the render");
    unsafe { loki_session_free(s) };
}
