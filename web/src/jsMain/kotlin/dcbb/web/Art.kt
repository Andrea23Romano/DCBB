package dcbb.web

import dcbb.core.model.CardDef
import dcbb.core.model.CardType
import dcbb.core.model.ConstantKind
import dcbb.core.model.Effect
import dcbb.core.model.EnemyDef
import dcbb.core.model.EnergyColor
import dcbb.core.model.Faction
import dcbb.core.model.OperativeDef
import dcbb.core.model.Rarity
import dcbb.core.model.Tgt
import dcbb.core.run.Moment
import dcbb.core.run.MomentType
import kotlin.math.abs

/**
 * Placeholder illustrations, drawn as SVG until the real art exists. They follow docs/11 (Illuminated Weave) where
 * a placeholder can:
 *
 * - A vellum ground with woven threads.
 * - One medium per faction: manuscript gold on lapis for the Order, cyan engraving on porcelain for the Convergence,
 *   torn collage with a misprint offset for the Errata, and rips into the void for the Tear. Era subjects get the
 *   era palette and no medium.
 * - The frame's top shape by card type (Attack a pointed arch, Skill a rounded arch, Constant a square with pins),
 *   and the thread color by rarity.
 * - One centered subject: an emblem for the card's main effect, or a bust for an enemy.
 *
 * No text or numbers ever appear in the images. Each drawing is seeded by the subject's id, so it is stable.
 */
object Art {
    private val cache = HashMap<String, String>()

    // ---- palettes (docs/11) -------------------------------------------------------------------------------------

    private class Palette(val ground: String, val field: String, val accent: String, val light: String, val ink: String, val second: String)

    private val ORDER = Palette("#EFE3C8", "#2B3F8C", "#C9A24A", "#F0D98A", "#2A1E16", "#C8412B")
    private val CONVERGENCE = Palette("#F4F6F7", "#13233A", "#1F9BB5", "#6FE3F2", "#13233A", "#B0894A")
    private val ERRATA = Palette("#ECE6DA", "#6A3FA0", "#D2357F", "#E3D29B", "#222222", "#B9B4A8")
    private val TEAR = Palette("#E9E2D0", "#0B0A10", "#E9E2D0", "#5B5566", "#2B2830", "#3A3440")
    private val NEUTRAL = Palette("#EFE3C8", "#7A7468", "#A79F8E", "#F3ECDA", "#2A1E16", "#5E5A52")
    private val LONDON = Palette("#E8E1D2", "#3B3836", "#E0A44A", "#F2D49A", "#2A2622", "#8A4B3A")
    private val MILAN = Palette("#EDE3D1", "#9C4A36", "#C9954C", "#F2DDB4", "#3A2418", "#7FA3C0")
    private val SALT = Palette("#F2EEE6", "#121214", "#D9B56E", "#CFD8DE", "#121214", "#8C5A3C")

    private fun palette(faction: Faction, id: String): Palette = when (faction) {
        Faction.ORDER -> ORDER
        Faction.CONVERGENCE -> CONVERGENCE
        Faction.ERRATA -> ERRATA
        Faction.TEAR -> TEAR
        Faction.NEUTRAL -> NEUTRAL
        Faction.ERA -> when {
            id.startsWith("milan.") -> MILAN
            id.startsWith("saltwaste.") -> SALT
            else -> LONDON
        }
    }

    private fun thread(r: Rarity): String = when (r) {
        Rarity.UNCOMMON -> "#B8BEC8"
        Rarity.RARE -> "#C9A24A"
        Rarity.LEGENDARY -> "#D2357F"
        else -> "#6E6E73"
    }

    // ---- seeded variation -----------------------------------------------------------------------------------------

    private fun hash(s: String): Int {
        var h = 0x811C9DC5.toInt()
        for (ch in s) h = (h xor ch.code) * 0x01000193
        return h
    }

    private class Rand(seed: Int) {
        private var x = if (seed == 0) 1 else seed
        fun next(): Int {
            x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
            return x
        }
        fun range(lo: Double, hi: Double): Double = lo + (abs(next() % 10_000) / 10_000.0) * (hi - lo)
        fun int(n: Int): Int = abs(next() % n)
    }

    private fun f(d: Double) = ((d * 10).toInt() / 10.0).toString()

    // ---- emblems: one subject each, drawn in a 40×40 box --------------------------------------------------------

    enum class Emblem {
        BLADE, BLADES, SHIELD, EYE, HOURGLASS, GEAR, SPIRAL, SUNDIAL, SWORD_RIBBON, CARET, DOUBLE, RIP, QUILL, PAGES,
        FLAME, LENS, HEX, HELM, CROSS, DAGGER, FIST, SHEARS, MASK, BOLT, WISP, BLOCKS, WAVES, THREAD, KNOT, LATTICE,
        CHEST, SCALES, CANDLE, ARCH, CLOCK, BANNER, SWORDS, LOOP,
    }

    private fun emblem(e: Emblem, fill: String, ink: String, accent: String): String = when (e) {
        Emblem.BLADE -> """<g transform="rotate(32 20 20)"><path d="M20 2 L23 25 L17 25 Z" fill="$fill" stroke="$ink" stroke-width="1"/><rect x="11" y="25" width="18" height="3" fill="$accent" stroke="$ink" stroke-width=".8"/><rect x="18.5" y="28" width="3" height="7" fill="$ink"/><circle cx="20" cy="37" r="2.2" fill="$accent" stroke="$ink" stroke-width=".8"/></g>"""
        Emblem.BLADES -> (-1..1).joinToString("") { k ->
            """<g transform="rotate(${k * 28} 20 34)"><path d="M20 6 L22 26 L18 26 Z" fill="$fill" stroke="$ink" stroke-width=".8"/><rect x="15" y="26" width="10" height="2" fill="$accent"/></g>"""
        }
        Emblem.SHIELD -> """<path d="M20 3 L34 8 L33 22 Q31 32 20 38 Q9 32 7 22 L6 8 Z" fill="$fill" stroke="$ink" stroke-width="1.2"/><path d="M20 8 V32 M11 17 H29" stroke="$accent" stroke-width="2.4"/>"""
        Emblem.EYE -> """<path d="M3 20 Q20 5 37 20 Q20 35 3 20 Z" fill="$fill" stroke="$ink" stroke-width="1.2"/><circle cx="20" cy="20" r="7" fill="$accent" stroke="$ink" stroke-width="1"/><circle cx="20" cy="20" r="2.6" fill="$ink"/>"""
        Emblem.HOURGLASS -> """<path d="M10 4 H30 V7 Q30 15 22 20 Q30 25 30 33 V36 H10 V33 Q10 25 18 20 Q10 15 10 7 Z" fill="$fill" stroke="$ink" stroke-width="1.2"/><path d="M14 33 Q20 26 26 33 Z M15 10 H25 Q22 15 20 17 Q18 15 15 10 Z" fill="$accent"/>"""
        Emblem.GEAR -> buildString {
            for (k in 0 until 8) append("""<rect x="17.5" y="3" width="5" height="8" rx="1" fill="$fill" stroke="$ink" stroke-width=".8" transform="rotate(${k * 45} 20 20)"/>""")
            append("""<circle cx="20" cy="20" r="11" fill="$fill" stroke="$ink" stroke-width="1"/><circle cx="20" cy="20" r="4" fill="$accent" stroke="$ink" stroke-width="1"/>""")
        }
        Emblem.SPIRAL -> """<path d="M20 20 m0 -2 a2 2 0 1 1 -2 2 a4 4 0 1 1 4 4 a6 6 0 1 1 -6 -6 a8 8 0 1 1 8 8 a10 10 0 1 1 -10 -10 a12 12 0 1 1 12 12" fill="none" stroke="$fill" stroke-width="3" stroke-linecap="round"/><path d="M20 20 m0 -2 a2 2 0 1 1 -2 2 a4 4 0 1 1 4 4 a6 6 0 1 1 -6 -6 a8 8 0 1 1 8 8" fill="none" stroke="$ink" stroke-width=".8"/>"""
        Emblem.SUNDIAL -> """<circle cx="20" cy="22" r="15" fill="$fill" stroke="$ink" stroke-width="1.2"/>${(0 until 12).joinToString("") { k -> """<rect x="19.5" y="8" width="1" height="3" fill="$ink" transform="rotate(${k * 30} 20 22)"/>""" }}<path d="M20 6 L20 22 L31 22 Z" fill="$accent" stroke="$ink" stroke-width="1"/>"""
        Emblem.SWORD_RIBBON -> emblem(Emblem.BLADE, fill, ink, accent) + """<path d="M14 26 Q8 30 11 36 Q14 31 18 30 Z" fill="$accent" stroke="$ink" stroke-width=".7"/>"""
        Emblem.CARET -> """<path d="M6 33 L20 8 L34 33" fill="none" stroke="$ink" stroke-width="6" stroke-linejoin="round"/><path d="M6 33 L20 8 L34 33" fill="none" stroke="$fill" stroke-width="3.5" stroke-linejoin="round"/>"""
        Emblem.DOUBLE -> bust(fill, ink, offset = -4.0) + """<g opacity=".75">${bust(accent, ink, offset = 4.0)}</g>"""
        Emblem.RIP -> """<path d="M14 1 L21 11 L16 18 L25 25 L18 31 L23 39 L28 39 L26 30 L33 23 L25 14 L29 5 L22 1 Z" fill="#0B0A10"/><path d="M16 18 q-6 6 -3 14 M25 25 q5 4 4 11 M21 11 q-8 2 -10 8" stroke="#E9E2D0" stroke-width=".8" fill="none"/>"""
        Emblem.QUILL -> """<path d="M32 4 Q20 8 12 26 L10 34 L14 30 Q28 20 32 4 Z" fill="$fill" stroke="$ink" stroke-width="1"/><path d="M30 7 L12 30" stroke="$ink" stroke-width=".8"/><circle cx="9" cy="35" r="1.6" fill="$accent"/>"""
        Emblem.PAGES -> (0..2).joinToString("") { k -> """<rect x="${9 + k * 4}" y="${8 + k * 3}" width="18" height="24" rx="1.5" fill="$fill" stroke="$ink" stroke-width="1" transform="rotate(${-8 + k * 8} 20 20)"/>""" }
        Emblem.FLAME -> """<path d="M20 3 Q31 15 27 26 Q25 35 20 37 Q13 35 12 27 Q10 19 17 13 Q17 22 20 23 Q18 13 20 3 Z" fill="$fill" stroke="$ink" stroke-width="1"/><path d="M20 22 Q25 27 22 33 Q20 35 18 33 Q16 28 20 22 Z" fill="$accent"/>"""
        Emblem.LENS -> """<ellipse cx="20" cy="20" rx="12" ry="16" fill="$fill" stroke="$ink" stroke-width="1.2"/><circle cx="20" cy="18" r="7" fill="none" stroke="$accent" stroke-width="1.5"/><circle cx="20" cy="18" r="3.5" fill="$accent"/><circle cx="20" cy="18" r="10" fill="none" stroke="$accent" stroke-width=".6"/>"""
        Emblem.HEX -> """<path d="M20 4 L33 12 L33 28 L20 36 L7 28 L7 12 Z" fill="$fill" stroke="$ink" stroke-width="1.2"/><path d="M20 11 L27 15 L27 25 L20 29 L13 25 L13 15 Z" fill="none" stroke="$accent" stroke-width="1.2"/>"""
        Emblem.HELM -> """<path d="M8 33 V17 Q8 4 20 4 Q32 4 32 17 V33 Z" fill="$fill" stroke="$ink" stroke-width="1.2"/><rect x="11" y="16" width="18" height="3" fill="$ink"/><path d="M20 19 V30" stroke="$ink" stroke-width="1.4"/><path d="M8 26 H32" stroke="$accent" stroke-width="1.6"/>"""
        Emblem.CROSS -> """<path d="M17 3 H23 V15 H35 V21 H23 V37 H17 V21 H5 V15 H17 Z" fill="$fill" stroke="$ink" stroke-width="1.2"/><circle cx="20" cy="18" r="4" fill="$accent"/>"""
        Emblem.DAGGER -> """<g transform="rotate(-38 20 20)"><path d="M20 3 L22.5 24 L17.5 24 Z" fill="$fill" stroke="$ink" stroke-width="1"/><rect x="14" y="24" width="12" height="2.4" fill="$accent"/><rect x="18.6" y="26.4" width="2.8" height="9" fill="$ink"/></g>"""
        Emblem.FIST -> """<rect x="9" y="12" width="22" height="18" rx="6" fill="$fill" stroke="$ink" stroke-width="1.2"/>${(0..3).joinToString("") { k -> """<path d="M${13 + k * 5} 12 V19" stroke="$ink" stroke-width=".9"/>""" }}<rect x="13" y="30" width="14" height="7" fill="$accent" stroke="$ink" stroke-width=".8"/>"""
        Emblem.SHEARS -> """<g stroke="$ink" stroke-width="1"><path d="M20 20 L33 4 L35 6 Z" fill="$fill"/><path d="M20 20 L7 4 L5 6 Z" fill="$fill"/><circle cx="13" cy="29" r="5" fill="none" stroke="$accent" stroke-width="2"/><circle cx="27" cy="29" r="5" fill="none" stroke="$accent" stroke-width="2"/></g><path d="M20 20 L14 25 M20 20 L26 25" stroke="$ink" stroke-width="2"/>"""
        Emblem.MASK -> """<ellipse cx="17" cy="18" rx="10" ry="12" fill="$fill" stroke="$ink" stroke-width="1.2"/><path d="M24 18 L38 27 L23 25 Z" fill="$accent" stroke="$ink" stroke-width="1"/><circle cx="13" cy="15" r="2.6" fill="$ink"/><circle cx="20" cy="15" r="2.6" fill="$ink"/>"""
        Emblem.BOLT -> """<path d="M5 33 L33 7" stroke="$ink" stroke-width="2.4"/><path d="M33 7 L26 9 L31 14 Z" fill="$accent" stroke="$ink" stroke-width=".8"/><path d="M5 33 L9 27 M5 33 L11 31" stroke="$fill" stroke-width="2"/>"""
        Emblem.WISP -> """<path d="M20 4 Q31 12 26 22 Q23 30 28 37 Q18 34 15 26 Q12 18 18 12 Q14 22 20 24 Q17 13 20 4 Z" fill="$fill" stroke="$ink" stroke-width="1" opacity=".9"/><circle cx="18" cy="16" r="1.6" fill="$ink"/><circle cx="23" cy="16" r="1.6" fill="$ink"/>"""
        Emblem.BLOCKS -> """<rect x="10" y="4" width="20" height="13" fill="$fill" stroke="$ink" stroke-width="1.2"/><rect x="6" y="17" width="28" height="19" fill="$fill" stroke="$ink" stroke-width="1.2"/><path d="M6 26 H34 M20 17 V36" stroke="$accent" stroke-width="1.2"/><circle cx="16" cy="10" r="1.6" fill="$accent"/><circle cx="24" cy="10" r="1.6" fill="$accent"/>"""
        Emblem.WAVES -> (0..3).joinToString("") { k -> """<path d="M3 ${10 + k * 7} q4.25 -4 8.5 0 t8.5 0 t8.5 0 t8.5 0" fill="none" stroke="${if (k % 2 == 0) fill else accent}" stroke-width="2"/>""" }
        Emblem.THREAD -> """<path d="M4 26 C10 8 18 36 24 16 S34 10 36 28" fill="none" stroke="$fill" stroke-width="3" stroke-linecap="round"/><path d="M4 26 C10 8 18 36 24 16 S34 10 36 28" fill="none" stroke="$ink" stroke-width=".7"/>"""
        Emblem.KNOT -> """<path d="M6 20 C6 6 34 6 34 20 C34 34 10 30 14 18 C18 6 30 14 24 24 C18 34 6 32 6 20 Z" fill="none" stroke="$fill" stroke-width="3"/><path d="M6 20 C6 6 34 6 34 20" fill="none" stroke="$ink" stroke-width=".7"/>"""
        Emblem.LATTICE -> buildString {
            append("""<rect x="4" y="4" width="32" height="32" rx="3" fill="$fill" stroke="$ink" stroke-width="1.2"/>""")
            for (i in 0..2) for (j in 0..2) append("""<circle cx="${11 + i * 9}" cy="${11 + j * 9}" r="3" fill="none" stroke="$accent" stroke-width="1.4"/>""")
            append("""<path d="M11 11 L29 29 M29 11 L11 29" stroke="$accent" stroke-width=".7"/>""")
        }
        Emblem.CHEST -> """<rect x="6" y="16" width="28" height="18" rx="2" fill="$fill" stroke="$ink" stroke-width="1.2"/><path d="M6 16 Q20 4 34 16 Z" fill="$accent" stroke="$ink" stroke-width="1.2"/><rect x="18" y="18" width="4" height="6" fill="$ink"/>"""
        Emblem.SCALES -> """<path d="M20 5 V34 M8 10 H32" stroke="$ink" stroke-width="1.6"/><path d="M3 22 L8 10 L13 22 Z M27 22 L32 10 L37 22 Z" fill="none" stroke="$ink" stroke-width=".8"/><path d="M2 22 Q8 28 14 22 Z M26 22 Q32 28 38 22 Z" fill="$accent" stroke="$ink" stroke-width="1"/><rect x="13" y="33" width="14" height="3" fill="$fill" stroke="$ink" stroke-width=".8"/>"""
        Emblem.CANDLE -> """<rect x="15" y="16" width="10" height="20" fill="$fill" stroke="$ink" stroke-width="1.2"/><path d="M20 4 Q25 10 22 14 Q20 16 18 14 Q15 10 20 4 Z" fill="$accent" stroke="$ink" stroke-width=".8"/><rect x="11" y="36" width="18" height="2.5" fill="$ink"/>"""
        Emblem.ARCH -> """<path d="M8 37 V18 Q8 4 20 4 Q32 4 32 18 V37 Z" fill="$fill" stroke="$ink" stroke-width="1.2"/><path d="M14 37 V21 Q14 12 20 12 Q26 12 26 21 V37 Z" fill="$accent" stroke="$ink" stroke-width=".8"/>"""
        Emblem.CLOCK -> """<circle cx="20" cy="20" r="15" fill="$fill" stroke="$ink" stroke-width="1.2"/><path d="M20 20 V10 M20 20 L27 24" stroke="$ink" stroke-width="1.6"/><path d="M27 6 L22 14 L26 17 L19 27" fill="none" stroke="$accent" stroke-width="1.4"/>"""
        Emblem.BANNER -> """<path d="M9 4 V37" stroke="$ink" stroke-width="2"/><path d="M10 6 H33 L27 14 L33 22 H10 Z" fill="$fill" stroke="$ink" stroke-width="1"/><circle cx="19" cy="14" r="3" fill="$accent"/>"""
        Emblem.SWORDS -> """<g transform="rotate(35 20 20)"><path d="M20 3 L22 26 L18 26 Z" fill="$fill" stroke="$ink" stroke-width=".8"/><rect x="14" y="26" width="12" height="2.4" fill="$accent"/></g><g transform="rotate(-35 20 20)"><path d="M20 3 L22 26 L18 26 Z" fill="$fill" stroke="$ink" stroke-width=".8"/><rect x="14" y="26" width="12" height="2.4" fill="$accent"/></g>"""
        Emblem.LOOP -> """<path d="M30 12 A12 12 0 1 0 32 24" fill="none" stroke="$fill" stroke-width="3.5"/><path d="M33 6 L31 15 L24 11 Z" fill="$fill"/>"""
    }

    /** A head-and-shoulders silhouette, the default subject for a figure. */
    private fun bust(fill: String, ink: String, offset: Double = 0.0): String =
        """<g transform="translate(${f(offset)} 0)"><circle cx="20" cy="13" r="8" fill="$fill" stroke="$ink" stroke-width="1"/><path d="M5 39 Q6 24 20 23 Q34 24 35 39 Z" fill="$fill" stroke="$ink" stroke-width="1"/></g>"""

    // ---- grounds and media ----------------------------------------------------------------------------------------

    /** Vellum with a few woven threads: the shared ground of every image. */
    private fun ground(w: Int, h: Int, p: Palette, r: Rand): String = buildString {
        append("""<rect width="$w" height="$h" fill="${p.ground}"/>""")
        var y = 3.0
        while (y < h) {
            append("""<path d="M0 ${f(y)} H$w" stroke="${p.ink}" stroke-opacity=".06" stroke-width=".6"/>""")
            y += r.range(4.0, 7.0)
        }
        var x = 2.0
        while (x < w) {
            append("""<path d="M${f(x)} 0 V$h" stroke="${p.ink}" stroke-opacity=".045" stroke-width=".6"/>""")
            x += r.range(5.0, 9.0)
        }
    }

    /** The faction's medium behind the subject, inside the niche. */
    private fun medium(faction: Faction, w: Int, h: Int, p: Palette, r: Rand): String = buildString {
        when (faction) {
            Faction.ORDER -> {
                // A lapis field with a gold diaper lattice, like a manuscript background.
                append("""<rect width="$w" height="$h" fill="${p.field}"/>""")
                val step = r.range(9.0, 13.0)
                var d = -h.toDouble()
                while (d < w + h) {
                    append("""<path d="M${f(d)} 0 L${f(d + h)} $h M${f(d + h)} 0 L${f(d)} $h" stroke="${p.accent}" stroke-opacity=".55" stroke-width=".7"/>""")
                    d += step
                }
            }
            Faction.CONVERGENCE -> {
                // Porcelain with cyan engraving: concentric circles and fine hatching.
                append("""<rect width="$w" height="$h" fill="${p.ground}"/>""")
                val cx = w / 2.0 + r.range(-10.0, 10.0)
                val cy = h / 2.0
                for (k in 1..7) append("""<circle cx="${f(cx)}" cy="${f(cy)}" r="${k * 7}" fill="none" stroke="${p.accent}" stroke-opacity="${f(0.55 - k * 0.05)}" stroke-width=".7"/>""")
                var x = 0.0
                while (x < w) {
                    append("""<path d="M${f(x)} $h L${f(x + 14)} 0" stroke="${p.accent}" stroke-opacity=".12" stroke-width=".6"/>""")
                    x += 4.0
                }
            }
            Faction.ERRATA -> {
                // Torn paper fragments taped together.
                append("""<rect width="$w" height="$h" fill="${p.ground}"/>""")
                val colors = listOf(p.field, p.second, p.light, "#C9A24A", "#1F9BB5")
                repeat(5) { k ->
                    val x0 = r.range(-10.0, w - 30.0)
                    val y0 = r.range(-10.0, h - 20.0)
                    val ww = r.range(30.0, 70.0)
                    val hh = r.range(20.0, 46.0)
                    val pts = listOf(x0 to y0, x0 + ww to y0 + r.range(-4.0, 4.0), x0 + ww + r.range(-5.0, 5.0) to y0 + hh, x0 to y0 + hh + r.range(-4.0, 4.0))
                    val jag = pts.joinToString(" ") { (a, b) -> "${f(a)},${f(b)}" }
                    append("""<polygon points="$jag" fill="${colors[k % colors.size]}" fill-opacity="${if (k % colors.size >= 3) ".35" else ".8"}" stroke="${p.ink}" stroke-opacity=".25" stroke-width=".6"/>""")
                }
                append("""<rect x="${f(r.range(10.0, w - 40.0))}" y="${f(r.range(4.0, h - 14.0))}" width="26" height="7" fill="${p.light}" fill-opacity=".85" transform="rotate(${f(r.range(-20.0, 20.0))} ${w / 2} ${h / 2})"/>""")
            }
            Faction.TEAR -> {
                // The picture itself torn open: a void with loose threads.
                append("""<rect width="$w" height="$h" fill="${p.ground}"/>""")
                val x = r.range(w * 0.3, w * 0.6)
                val pts = buildList {
                    add(x to -2.0)
                    var yy = 0.0
                    while (yy < h) {
                        yy += r.range(6.0, 12.0)
                        add(x + r.range(-14.0, 18.0) + yy * 0.25 to yy)
                    }
                    var yb = h + 2.0
                    while (yb > 0) {
                        add(x + 22 + r.range(-10.0, 16.0) + yb * 0.25 to yb)
                        yb -= r.range(6.0, 12.0)
                    }
                }
                append("""<polygon points="${pts.joinToString(" ") { (a, b) -> "${f(a)},${f(b)}" }}" fill="${p.field}"/>""")
                repeat(4) {
                    val tx = x + r.range(0.0, 24.0)
                    val ty = r.range(0.0, h.toDouble())
                    append("""<path d="M${f(tx)} ${f(ty)} q${f(r.range(-8.0, 8.0))} 6 ${f(r.range(-4.0, 4.0))} 14" fill="none" stroke="${p.accent}" stroke-width=".7"/>""")
                }
            }
            else -> {
                // Era subjects and neutral cards: the era palette, no faction medium.
                append("""<rect width="$w" height="$h" fill="${p.ground}"/>""")
                append("""<rect y="${f(h * 0.62)}" width="$w" height="${f(h * 0.38)}" fill="${p.field}" fill-opacity=".18"/>""")
                append("""<circle cx="${f(r.range(w * 0.15, w * 0.85))}" cy="${f(h * 0.3)}" r="${f(r.range(10.0, 18.0))}" fill="${p.accent}" fill-opacity=".28"/>""")
            }
        }
    }

    /** The niche the subject stands in; its top shape says the card type (docs/11 "Frames"). */
    private fun niche(type: CardType?, w: Int, h: Int): String {
        val x0 = w * 0.22
        val x1 = w * 0.78
        val top = 6.0
        val shoulder = h * 0.42
        return when (type) {
            CardType.ATTACK -> "M${f(x0)} $h V${f(shoulder)} Q${f(x0)} ${f(top + 6)} ${f(w / 2.0)} ${f(top)} Q${f(x1)} ${f(top + 6)} ${f(x1)} ${f(shoulder)} V$h Z"
            CardType.CONSTANT -> "M${f(x0)} $h V${f(top)} H${f(x1)} V$h Z"
            else -> "M${f(x0)} $h V${f(shoulder)} A${f((x1 - x0) / 2)} ${f(shoulder - top)} 0 0 1 ${f(x1)} ${f(shoulder)} V$h Z"
        }
    }

    private fun svg(w: Int, h: Int, label: String, body: String) =
        """<svg class="art" viewBox="0 0 $w $h" role="img" aria-label="${Views.esc(label)}" preserveAspectRatio="xMidYMid slice">$body</svg>"""

    // ---- subjects ---------------------------------------------------------------------------------------------------

    fun card(def: CardDef): String = cache.getOrPut("card:" + def.id) {
        val w = 160
        val h = 64
        val r = Rand(hash(def.id))
        val p = palette(def.faction, def.id)
        val type = def.forkFaces?.first()?.type ?: def.type
        val emblem = emblemFor(def)
        val body = buildString {
            append(ground(w, h, NEUTRAL, r))
            append("""<g opacity=".95">${medium(def.faction, w, h, p, r)}</g>""")
            val shape = niche(type, w, h)
            append("""<path d="$shape" fill="${p.ground}" fill-opacity="${if (def.faction == Faction.ORDER || def.faction == Faction.TEAR) ".92" else ".7"}" stroke="${p.accent}" stroke-width="1.4"/>""")
            if (type == CardType.CONSTANT) {
                for ((px, py) in listOf(w * 0.22 to 6.0, w * 0.78 to 6.0, w * 0.22 to h - 3.0, w * 0.78 to h - 3.0)) {
                    append("""<circle cx="${f(px)}" cy="${f(py)}" r="2.2" fill="${p.accent}" stroke="${p.ink}" stroke-width=".6"/>""")
                }
            }
            val size = 46.0
            val ex = w / 2.0 - size / 2 + r.range(-3.0, 3.0)
            val ey = h - size - 3.0
            val tilt = r.range(-7.0, 7.0)
            val (fill, accent) = subjectColors(def.faction, p)
            if (def.faction == Faction.ERRATA || def.id.endsWith("~")) {
                // The misprint offset: the subject printed twice, slightly out of register.
                append("""<g transform="translate(${f(ex + 2.6)} ${f(ey - 1.2)}) scale(${f(size / 40)}) rotate(${f(tilt)} 20 20)" opacity=".55">${emblem(emblem, "#D2357F", "#D2357F", "#D2357F")}</g>""")
            }
            append("""<g transform="translate(${f(ex)} ${f(ey)}) scale(${f(size / 40)}) rotate(${f(tilt)} 20 20)">${emblem(emblem, fill, p.ink, accent)}</g>""")
            // The rarity thread around the frame.
            val thread = thread(def.rarity)
            append("""<rect x="1" y="1" width="${w - 2}" height="${h - 2}" fill="none" stroke="$thread" stroke-width="2"${if (def.rarity == Rarity.LEGENDARY) " stroke-dasharray=\"6 3\"" else ""}/>""")
            if (def.id.endsWith("+")) append("""<path d="M${w - 16} 4 h12 v12" fill="none" stroke="#C9A24A" stroke-width="2"/>""")
        }
        svg(w, h, "Placeholder art for ${def.name}", body)
    }

    private fun subjectColors(faction: Faction, p: Palette): Pair<String, String> = when (faction) {
        Faction.ORDER -> "#F0D98A" to "#C8412B"
        Faction.CONVERGENCE -> "#F4F6F7" to "#1F9BB5"
        Faction.ERRATA -> "#B48DF1" to "#E3D29B"
        Faction.TEAR -> "#3A3440" to "#E9E2D0"
        else -> p.light to p.accent
    }

    /** The card's main effect decides its subject. */
    fun emblemFor(def: CardDef): Emblem {
        def.constant?.let { c ->
            return when (c.kind) {
                ConstantKind.VOW -> Emblem.SWORD_RIBBON
                ConstantKind.RELIC -> Emblem.SUNDIAL
                ConstantKind.SUBROUTINE -> Emblem.GEAR
                ConstantKind.NONE -> if (c.onShiftIn != null) Emblem.ARCH else Emblem.SPIRAL
            }
        }
        if (def.misprintFaces != null) return if (def.id.endsWith("~")) Emblem.RIP else Emblem.PAGES
        if (def.forkFaces != null) return Emblem.DOUBLE
        val effects = flatten(def.effects)
        return when {
            effects.any { it is Effect.Damage && it.target == Tgt.ALL_ENEMIES } -> Emblem.BLADES
            effects.any { it is Effect.Damage } -> Emblem.BLADE
            effects.any { it is Effect.RemoveIntent } -> Emblem.CARET
            effects.any { it is Effect.BleedThrough } -> Emblem.DOUBLE
            effects.any { it is Effect.ForkCard || it is Effect.Shift } -> Emblem.CARET
            effects.any { it is Effect.Delay || it is Effect.Schedule || it is Effect.RestoreLastPhaseLoss } -> Emblem.HOURGLASS
            effects.any { it is Effect.Block || it is Effect.GainPlate } -> Emblem.SHIELD
            effects.any { it is Effect.Recall } -> Emblem.LOOP
            effects.any { it is Effect.Foresee || it is Effect.Observe } -> Emblem.EYE
            effects.any { it is Effect.GrantRetain || it is Effect.ReduceCostThisTurn } -> Emblem.QUILL
            effects.any { it is Effect.Draw } -> Emblem.PAGES
            effects.any { it is Effect.GainEnergy } -> Emblem.SUNDIAL
            else -> Emblem.SPIRAL
        }
    }

    private fun flatten(effects: List<Effect>): List<Effect> = effects.flatMap { e ->
        when (e) {
            is Effect.When -> listOf(e) + flatten(e.then) + flatten(e.otherwise)
            is Effect.Schedule -> listOf(e) + flatten(e.effects)
            else -> listOf(e)
        }
    }

    fun enemy(def: EnemyDef, operative: OperativeDef? = null): String = cache.getOrPut("enemy:" + def.id + ":" + operative?.id) {
        val w = 96
        val h = 72
        val r = Rand(hash(def.id))
        val faction = if (def.id == "echo.you" && operative != null) operative.faction else def.faction
        val p = palette(faction, def.id)
        val body = buildString {
            append(ground(w, h, NEUTRAL, r))
            append(medium(faction, w, h, p, r))
            val (fill, accent) = subjectColors(faction, p)
            val e = enemyEmblem(def)
            val size = 54.0
            val ex = w / 2.0 - size / 2
            val ey = h - size - 2.0
            if (def.id == "echo.you") {
                append("""<g transform="translate(${f(ex + 3)} ${f(ey - 1)}) scale(${f(size / 40)})" opacity=".45">${bust("#D2357F", "#D2357F")}</g>""")
            }
            append("""<g transform="translate(${f(ex)} ${f(ey)}) scale(${f(size / 40)})">${if (e == null) bust(fill, p.ink) else emblem(e, fill, p.ink, accent)}</g>""")
            if (def.elite) append("""<path d="M3 3 h10 M3 3 v10 M${w - 3} 3 h-10 M${w - 3} 3 v10" stroke="#C9A24A" stroke-width="2"/>""")
        }
        svg(w, h, "Placeholder portrait of ${def.name}", body)
    }

    private fun enemyEmblem(def: EnemyDef): Emblem? {
        val id = def.id
        return when {
            "footpad" in id -> Emblem.DAGGER
            "brawler" in id -> Emblem.FIST
            "lamplighter" in id -> Emblem.FLAME
            "condottiero" in id || "squire" in id || "sergeant" in id -> Emblem.HELM
            "plague" in id -> Emblem.MASK
            "crossbow" in id -> Emblem.BOLT
            "wraith" in id -> Emblem.WISP
            "golem" in id -> Emblem.BLOCKS
            "mirage" in id -> Emblem.WAVES
            "inquisitor" in id -> Emblem.CROSS
            "proxy" in id -> Emblem.LENS
            "calculating" in id -> Emblem.GEAR
            "drone" in id -> Emblem.HEX
            "pruner" in id -> Emblem.SHEARS
            "misprint" in id || "double" in id || id == "echo.you" -> Emblem.DOUBLE
            "loose_end" in id -> Emblem.THREAD
            "ravel" in id -> Emblem.KNOT
            "the_rent" in id -> Emblem.RIP
            "lattice" in id -> Emblem.LATTICE
            else -> null
        }
    }

    fun moment(m: Moment, operative: OperativeDef): String = cache.getOrPut("moment:" + m.type + ":" + m.faction + ":" + m.returning + ":" + (m.echo != null) + ":" + m.tear + ":" + m.title) {
        val w = 160
        val h = 52
        val r = Rand(hash(m.title))
        val faction = when {
            m.tear -> Faction.TEAR
            m.echo != null -> operative.faction
            m.faction != null -> m.faction!!
            else -> Faction.ERA
        }
        val p = palette(faction, "london.")
        val e = when (m.type) {
            MomentType.COMBAT -> Emblem.SWORDS
            MomentType.ELITE -> if (m.echo != null) Emblem.DOUBLE else Emblem.BANNER
            MomentType.EVENT -> Emblem.QUILL
            MomentType.ANOMALY -> Emblem.CLOCK
            MomentType.ANTIQUARIAN -> Emblem.SCALES
            MomentType.STILL_POINT -> Emblem.CANDLE
            MomentType.SHRINE -> Emblem.ARCH
            MomentType.CACHE -> Emblem.CHEST
            MomentType.BOSS -> Emblem.LATTICE
        }
        val body = buildString {
            append(ground(w, h, NEUTRAL, r))
            append("""<g opacity=".9">${medium(faction, w, h, p, r)}</g>""")
            val (fill, accent) = subjectColors(faction, p)
            val size = 40.0
            append("""<g transform="translate(${f(w / 2.0 - size / 2)} ${f(h - size - 6)}) scale(${f(size / 40)})">${emblem(e, fill, p.ink, accent)}</g>""")
            if (m.returning != null) {
                // A returning Moment: a loop of thread around the frame.
                append("""<path d="M4 ${h - 4} Q4 4 ${w / 2} 4 Q${w - 4} 4 ${w - 4} ${h - 4}" fill="none" stroke="#B48DF1" stroke-width="2" stroke-dasharray="5 3"/>""")
            }
        }
        svg(w, h, "Placeholder art for ${m.type.label}", body)
    }

    fun operative(op: OperativeDef): String = cache.getOrPut("op:" + op.id) {
        val w = 120
        val h = 80
        val r = Rand(hash(op.id))
        val p = palette(op.faction, op.id)
        val body = buildString {
            append(ground(w, h, NEUTRAL, r))
            append(medium(op.faction, w, h, p, r))
            val (fill, accent) = subjectColors(op.faction, p)
            val size = 62.0
            val ex = w / 2.0 - size / 2
            val ey = h - size
            val subject = when (op.faction) {
                Faction.ORDER -> bust(fill, p.ink) + """<g transform="translate(24 10) scale(.4)">${emblem(Emblem.HELM, "#B8BEC8", p.ink, accent)}</g>""" +
                    """<path d="M17 28 V38 M12 32 H22" stroke="$accent" stroke-width="2.2"/>"""
                Faction.CONVERGENCE -> """<g transform="translate(7 -2) scale(.65)">${emblem(Emblem.LENS, fill, p.ink, accent)}</g>""" +
                    """<path d="M5 39 Q6 26 20 25 Q34 26 35 39 Z" fill="$fill" stroke="${p.ink}" stroke-width="1"/><path d="M13 30 L16 28 L19 30 L16 32 Z M21 30 L24 28 L27 30 L24 32 Z" fill="$accent"/>"""
                Faction.ERRATA -> """<g opacity=".7">${bust("#D2357F", "#D2357F", offset = 4.0)}</g>""" + bust(fill, p.ink) +
                    """<path d="M16 34 L20 28 L24 34" fill="none" stroke="${p.ink}" stroke-width="1.6"/>"""
                else -> bust(fill, p.ink)
            }
            append("""<g transform="translate(${f(ex)} ${f(ey)}) scale(${f(size / 40)})">$subject</g>""")
            val col = when (op.color) {
                EnergyColor.FAITH -> "#C9A24A"
                EnergyColor.COMPUTE -> "#1F9BB5"
                else -> "#6A3FA0"
            }
            append("""<rect x="1" y="1" width="${w - 2}" height="${h - 2}" fill="none" stroke="$col" stroke-width="2"/>""")
        }
        svg(w, h, "Placeholder portrait of the ${op.name}", body)
    }
}
