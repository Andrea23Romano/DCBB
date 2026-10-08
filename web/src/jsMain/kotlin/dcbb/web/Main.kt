package dcbb.web

import kotlinx.browser.document
import kotlinx.browser.window

/**
 * Boots the client. Inside a Claude artifact viewer, `window.claude.hot` carries the fight across republishes:
 * the snapshot is the fight's replay code, so a reload rebuilds it action by action.
 */
fun main() {
    val root = document.getElementById("app") ?: return
    val app = App(root)
    val hot = window.asDynamic().claude?.hot
    if (hot != null && hot.snapshot != null) {
        hot.snapshot({
            val o: dynamic = js("({})")
            o.code = app.snapshot()
            o
        })
    }
    val start: (dynamic) -> Unit = { data -> app.boot((data?.code as? String)?.takeIf { it.isNotBlank() }) }
    if (hot != null && hot.ready != null) hot.ready(start) else start(hot?.data)
}
