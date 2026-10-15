package dcbb.web

import dcbb.core.content.Variants
import dcbb.core.model.CardDef
import dcbb.core.model.Faction
import dcbb.core.run.EventKind
import dcbb.core.run.Moment
import dcbb.core.run.MomentType
import dcbb.core.run.Purpose
import dcbb.core.run.RunEngine
import dcbb.core.run.RunState
import dcbb.core.run.Screen
import dcbb.core.run.ShopKind
import dcbb.core.text.Glossary
import dcbb.core.text.RulesText
import dcbb.web.Views.Companion.esc
import dcbb.web.Views.Companion.fv

/** HTML for the run screens between fights (fights reuse the combat board). All game text is escaped. */
class RunViews(private val app: App, private val views: Views) {
    private val re: RunEngine get() = app.runEngine
    private val content get() = app.content

    fun screen(r: RunSession): String {
        val s = r.state
        val era = re.era(s)
        val stepLine = if (s.step > era.steps) (if (era.nexus.isNotEmpty()) "the Nexus" else "the boss") else "step ${s.step} of ${era.steps}"
        return buildString {
            append(views.topbar("Act ${views.roman(s.act)} · ${era.name} · $stepLine · seed ${s.seed}", runTools(r)))
            append(runBar(s))
            append(rewindConfirm(s))
            append(views.noticeBox())
            append("""<main class="run">""")
            append(
                when (val sc = s.screen) {
                    is Screen.Weft -> weft(s, sc)
                    is Screen.LevelUp -> levelUp(s, sc)
                    is Screen.Reward -> reward(s, sc)
                    is Screen.Shop -> shop(s, sc)
                    is Screen.Rest -> rest(s, sc)
                    is Screen.Event -> event(sc)
                    is Screen.PickCard -> pickCard(s, sc)
                    is Screen.Note -> note(sc)
                    is Screen.Over -> over(sc)
                    is Screen.Fallen -> fallen(s, sc)
                    is Screen.Fight -> ""
                },
            )
            append(runDrawer(s))
            append("</main>")
            app.copyText?.let { append(copyModal(it)) }
        }
    }

    // ---- the run bar ------------------------------------------------------------------------------------------

    /** Pinned to the top between fights: HP, Hours, Omens, Paradox, then the run's Artifacts, Anchor and deck. */
    fun runBar(s: RunState): String = buildString {
        val op = content.operative(s.operativeId)
        append("""<section class="runbar" aria-label="Run" style="--fc: var(--${fv(op.faction)})">""")
        append("""<div class="rb-row"><b class="rb-op">${esc(op.name)}</b>""")
        append("""<span class="rb-hp">${views.meter(s.hp, s.maxHp, "Your HP")}<span class="nums"><span><b>${s.hp}</b>/${s.maxHp} HP</span></span></span>""")
        append("""<span class="nums"><span>Hours <b>${s.hours}</b></span></span><span class="nums"><span>Omens <b>${s.omens}</b></span></span>""")
        val toNext = re.xpToNext(s)
        append("""<span class="nums" title="${esc(LEVEL_TIP)}"><span>Level <b>${s.level}</b>${if (toNext != null) " · $toNext XP to next" else ""}</span></span>""")
        val cap = re.unravelAt(s)
        append(
            """<span class="rb-pdx" title="${esc(Glossary.PARADOX.text)}">${views.pdxBar(s.paradox, cap)}""" +
                """<span class="nums"><span>Paradox <b>${s.paradox}</b>${if (cap != 10) "/$cap" else ""}${paradoxState(s.paradox, cap)}</span></span></span>""",
        )
        if (s.debt.values.sum() > 0 && !s.over) append("""<span class="debt" title="Repaid at your next fight's first Dawn">Debt ${s.debt.values.sum()}</span>""")
        append("</div>")
        append("""<div class="rb-row rb-sub">${runChips(s)}</div>""")
        append("</section>")
    }

    /** The run in a line, for the fight screen: what you carry, without repeating what the fight shows. */
    fun fightLine(s: RunState): String =
        """<div class="fightline"><span class="nums">Level ${s.level}</span><span class="nums">Hours ${s.hours}</span><span class="nums">Omens ${s.omens}</span>""" +
            carrying(s) + links(s) + "</div>"

    /** One button for everything you carry, which opens the list (the fight screen, and run screens on a phone). */
    private fun carrying(s: RunState): String {
        val n = s.artifacts.size + s.ripples.size + s.imprints.size
        val anchor = s.anchor?.let { "Anchor: step ${it.step}" } ?: "Anchor spent"
        val label = if (n > 0) "Carrying $n · $anchor" else anchor
        return """<button type="button" class="linkish rb-carry${if (app.drawer == Drawer.RUN_INFO) " is-on" else ""}" data-act="drawer" data-id="RUN_INFO">${esc(label)}</button>"""
    }

    private fun links(s: RunState): String =
        """<button type="button" class="linkish${if (app.drawer == Drawer.RUN_DECK) " is-on" else ""}" data-act="drawer" data-id="RUN_DECK">Deck ${s.deck.size}</button>""" +
            """<button type="button" class="linkish${if (app.drawer == Drawer.RUN_LOG) " is-on" else ""}" data-act="drawer" data-id="RUN_LOG">Run log</button>"""

    /** Artifacts, Ripples and Imprints by name (tap one for the details), the Anchor, the Branch Pool, the deck and the run log. */
    private fun runChips(s: RunState): String = """<span class="rb-chips">${chips(s)}</span>""" + carrying(s) + links(s)

    private fun chips(s: RunState): String = buildString {
        val info = app.drawer == Drawer.RUN_INFO
        for (a in s.artifacts) {
            val def = re.rc.artifact(a)
            append("""<button type="button" class="chiplet" data-act="drawer" data-id="RUN_INFO" title="${esc(def.text)}" aria-expanded="$info">${esc(def.name)}</button>""")
        }
        for (rp in s.ripples) {
            val def = re.rc.ripple(rp)
            append("""<button type="button" class="chiplet is-ripple" data-act="drawer" data-id="RUN_INFO" title="${esc(def.text)}" aria-expanded="$info">Ripple: ${esc(def.name)}</button>""")
        }
        for (im in s.imprints) {
            val def = re.rc.imprint(im)
            append("""<button type="button" class="chiplet is-imprint" data-act="drawer" data-id="RUN_INFO" title="${esc(def.text)}" aria-expanded="$info">${esc(def.name)}</button>""")
        }
        val anchor = s.anchor
        val anchorText = if (anchor != null) "Anchor: step ${anchor.step}" else "Anchor spent"
        append("""<button type="button" class="chiplet is-anchor" data-act="drawer" data-id="RUN_INFO" title="${esc(ANCHOR_TIP)}">${esc(anchorText)}</button>""")
        if (s.branchPool.isNotEmpty()) {
            append("""<button type="button" class="chiplet" data-act="drawer" data-id="RUN_INFO" title="${esc(POOL_TIP)}">Branch Pool ${s.branchPool.size}</button>""")
        }
        if (s.echoesWaiting.isNotEmpty()) append("""<span class="chiplet is-ripple">An Echo waits in the next act</span>""")
    }

    private fun paradoxState(p: Int, cap: Int) = when {
        p >= cap -> " · Unravel"
        p >= RunEngine.UNSTABLE -> " · Unstable"
        p >= RunEngine.FRAYED -> " · Frayed"
        else -> ""
    }

    private fun runTools(r: RunSession) = buildString {
        append("""<nav class="tools" aria-label="Run tools">""")
        append("""<button type="button" data-act="undo"${if (r.undo.isEmpty()) " disabled" else ""}>Undo</button>""")
        if (!r.state.over) {
            if (app.auto != Auto.NONE) {
                append("""<button type="button" class="primary" data-act="auto-stop">Stop the bot</button>""")
            } else {
                append(
                    views.menu(
                        "Bot",
                        """<button type="button" data-act="run-bot">Bot makes this choice</button><button type="button" data-act="auto-run">Bot plays on through the run</button>""",
                    ),
                )
            }
            if (re.canRewind(r.state)) append("""<button type="button" data-act="run-rewind" title="${esc(ANCHOR_TIP)}">Rewind</button>""")
        }
        append(
            views.menu(
                "More",
                """<button type="button" data-act="copy">Copy run code</button><button type="button" data-act="to-setup">Leave run</button>""",
                end = true,
            ),
        )
        append("</nav>")
    }

    fun runDrawer(s: RunState): String {
        val r = app.run ?: return ""
        return when (app.drawer) {
            Drawer.RUN_DECK -> {
                val counts = s.deck.groupingBy { it }.eachCount()
                """<section class="drawer" aria-label="Your deck"><h2 class="label">Your deck <span>${s.deck.size} cards</span></h2><ul class="decklist">""" +
                    counts.entries.sortedBy { content.card(it.key).name }.joinToString("") { (id, n) ->
                        val def = content.card(id)
                        """<li style="--fc: var(--${fv(def.faction)})"><span class="dl-count">$n×</span><span class="dl-name">${esc(def.name)}</span>""" +
                            """<span class="dl-cost">${views.costPips(def.cost)}</span><span class="dl-text">${esc(RulesText.card(def))}</span></li>"""
                    } + "</ul></section>"
            }
            Drawer.RUN_LOG -> """<section class="drawer" aria-label="Run log"><h2 class="label">Run log</h2><ol class="log-lines">""" +
                r.log.joinToString("") { "<li>${esc(it)}</li>" } + "</ol></section>"
            Drawer.RUN_INFO -> runInfo(s)
            else -> ""
        }
    }

    /** Everything the run carries, spelled out: Artifacts, Ripples, standing, the Anchor and the Branch Pool. */
    private fun runInfo(s: RunState): String = buildString {
        append("""<section class="drawer" aria-label="What you carry"><h2 class="label">What you carry</h2><ul class="decklist">""")
        for (a in s.artifacts) {
            val def = re.rc.artifact(a)
            append("""<li><span class="dl-count">Artifact</span><span class="dl-name">${esc(def.name)}</span><span class="dl-text">${esc(def.text)}</span></li>""")
        }
        for (rp in s.ripples) {
            val def = re.rc.ripple(rp)
            append("""<li><span class="dl-count">Ripple</span><span class="dl-name">${esc(def.name)}</span><span class="dl-text">${esc(def.text)}</span></li>""")
        }
        for (im in s.imprints) {
            val def = re.rc.imprint(im)
            append("""<li><span class="dl-count">Imprint</span><span class="dl-name">${esc(def.name)}</span><span class="dl-text">${esc(def.text)}</span></li>""")
        }
        val toNext = re.xpToNext(s)
        append(
            """<li><span class="dl-count">Level</span><span class="dl-name">Level ${s.level} · ${s.xp} XP</span>""" +
                """<span class="dl-text">${esc(LEVEL_TIP)}${if (toNext != null) " $toNext XP to the next level." else ""}</span></li>""",
        )
        val standing = listOf(Faction.ORDER, Faction.CONVERGENCE, Faction.ERRATA).joinToString(" · ") { f ->
            val v = s.standingWith(f)
            "${f.label} ${if (v > 0) "+$v" else if (v < 0) "−${-v}" else "0"}"
        }
        append("""<li><span class="dl-count">Standing</span><span class="dl-name">${esc(standing)}</span><span class="dl-text">How the factions regard you. Shrines and some events ask for it.</span></li>""")
        val anchor = s.anchor
        append(
            """<li><span class="dl-count">Anchor</span><span class="dl-name">${if (anchor != null) "Act ${views.roman(anchor.act)}, step ${anchor.step}" else "Spent"}</span>""" +
                """<span class="dl-text">${esc(ANCHOR_TIP)}</span></li>""",
        )
        if (s.branchPool.isNotEmpty()) {
            append(
                """<li><span class="dl-count">Branch Pool</span><span class="dl-name">${esc(s.branchPool.joinToString(", ") { it.title })}</span>""" +
                    """<span class="dl-text">${esc(POOL_TIP)}</span></li>""",
            )
        }
        append("</ul></section>")
    }

    // ---- the Weft ---------------------------------------------------------------------------------------------

    private fun weft(s: RunState, w: Screen.Weft): String = buildString {
        val single = w.options.size == 1
        val title = when {
            w.options.singleOrNull()?.type == MomentType.BOSS -> if (re.era(s).nexus.isNotEmpty()) "The Nexus" else "The act's last knot"
            w.options.singleOrNull()?.ambush == true -> "The Tear finds you"
            else -> "Step ${s.step}: choose one Moment"
        }
        append("""<section class="block"><h2 class="run-title">${esc(title)}</h2>""")
        if (s.anchorOffer) {
            append(
                """<div class="notice is-anchor"><span>The Shrine lets you move your Anchor here, for free. Rewinding would bring you back to this step.</span>""" +
                    """<button type="button" class="primary" data-act="run-anchor">Move your Anchor here</button></div>""",
            )
        }
        if (!single) append("""<p class="muted">Each step deals three Moments from the Era Deck. An Omen reveals what they hold, and one Moment of the next step.</p>""")
        append("""<div class="moments">""")
        for ((i, m) in w.options.withIndex()) append(moment(s, m, i))
        append("</div>")
        val hidden = w.options.any { it.id !in s.revealed }
        if (hidden || (s.step < re.era(s).steps)) {
            append("""<div class="btns"><button type="button" data-act="run-omen"${if (s.omens <= 0) " disabled" else ""}>Read an Omen (${s.omens} left)</button></div>""")
        }
        val peeked = if (s.step < re.era(s).steps) s.plan[s.step].filter { it.id in s.revealed } else emptyList()
        if (peeked.isNotEmpty()) {
            append("""<h3 class="label">Foreseen in the next step</h3><div class="moments is-peek">""")
            for (m in peeked) append(moment(s, m, null))
            append("</div>")
        }
        append("</section>")
    }

    private fun moment(s: RunState, m: Moment, index: Int?): String {
        val revealed = m.id in s.revealed
        val color = if (m.tear) "tear" else m.faction?.let { fv(it) } ?: "neutral"
        val threat = if (m.type.fight) """<span class="threat" aria-label="Threat ${m.threat} of 3">${"●".repeat(m.threat)}${"○".repeat(3 - m.threat)}</span>""" else ""
        val kind = buildList {
            add(m.type.label)
            if (m.tear) add("Tear")
            if (m.ambush && m.returning == null) add("Ambush")
            if (m.echo != null) add("Echo")
        }.joinToString(" · ")
        val details = if (revealed) details(m) else if (hasHidden(m)) """<span class="m-hidden">An Omen reveals more</span>""" else ""
        val back = m.returning?.let { ret -> """<span class="m-return" title="${esc(POOL_TIP)}">Returning · ${esc(ret.label)}: ${esc(ret.hint)}</span>""" } ?: ""
        val body = Art.moment(m, content.operative(s.operativeId)) +
            """<span class="m-top"><span class="m-type">${esc(kind)}</span>$threat</span>""" +
            """<span class="m-title">${esc(m.title)}</span>$back<span class="m-hint">${esc(hint(m))}</span>$details"""
        val cls = "moment t-${m.type.name.lowercase()}${if (m.returning != null) " is-returning" else ""}"
        return if (index != null) {
            """<button type="button" class="$cls" data-act="run-choose" data-id="$index" style="--fc: var(--$color)">$body</button>"""
        } else {
            """<div class="$cls" style="--fc: var(--$color)">$body</div>"""
        }
    }

    private fun hasHidden(m: Moment) = m.type.fight || m.type == MomentType.EVENT || m.type == MomentType.ANOMALY

    private fun hint(m: Moment): String = when (m.type) {
        MomentType.COMBAT -> if (m.ambush) "They strike first · Hours · a card" else "15–25 Hours · a card"
        MomentType.ELITE -> if (m.echo != null) {
            "Your abandoned self, with the ${m.echo!!.deck.size}-card deck you had · Lost Weight: a card from it, or an Artifact"
        } else {
            "40–55 Hours · a card · an Artifact"
        }
        MomentType.EVENT -> "A choice"
        MomentType.ANOMALY -> "Time weirdness"
        MomentType.ANTIQUARIAN -> "Buy cards and Artifacts · Erase · Inscribe"
        MomentType.STILL_POINT -> "Heal, Inscribe or Steady"
        MomentType.SHRINE -> if (m.faction == content.operative(app.run!!.state.operativeId).faction) "Your faction's altar: a boon" else "Another faction's altar"
        MomentType.CACHE -> "A free reward"
        MomentType.BOSS -> if (re.era(app.run!!.state).nexus.isNotEmpty()) {
            "The champion of the faction you stand lowest with, come for the Gnomon. Win the run."
        } else {
            "Win to clear the act: a boss Artifact, a rare card, a full heal"
        }
    }

    private fun details(m: Moment): String = when {
        m.echo != null -> """<span class="m-detail">Echo of You ${m.echo!!.hp} HP</span>"""
        m.type.fight && m.ref != null -> {
            val foes = content.encounter(m.ref ?: "").enemies.groupingBy { it }.eachCount().entries.joinToString(", ") { (id, c) ->
                val e = content.enemy(id)
                "${e.name} ${e.maxHp}${if (c > 1) " ×$c" else ""}"
            }
            """<span class="m-detail">${esc(foes)}</span>"""
        }
        (m.type == MomentType.EVENT || m.type == MomentType.ANOMALY) && m.ref != null -> {
            val ev = re.rc.event(m.ref ?: "")
            """<span class="m-detail">${esc(ev.title)} · ${esc(ev.kind.label)}</span>"""
        }
        else -> ""
    }

    // ---- level-ups ------------------------------------------------------------------------------------------

    private fun levelUp(s: RunState, l: Screen.LevelUp): String = buildString {
        append("""<section class="block"><h2 class="run-title">Level ${l.level}</h2>""")
        append("""<p class="muted">+${RunEngine.LEVEL_HP} max HP. Choose an Imprint: it lasts for the rest of the run.</p><div class="shoplist">""")
        for ((i, id) in l.options.withIndex()) {
            val def = re.rc.imprint(id)
            val who = def.operative?.let { content.operative(it).name } ?: "Universal"
            val req = def.requires?.let { " · needs ${re.describe(it)}" } ?: ""
            append(
                """<button type="button" class="ware imprint" data-act="run-choose" data-id="$i"><span class="ware-name">${esc(def.name)}</span>""" +
                    """<span class="ware-text">${esc(def.text)}</span><span class="m-return">${esc(who + req)}</span></button>""",
            )
        }
        append("</div></section>")
    }

    // ---- rewards ----------------------------------------------------------------------------------------------

    private fun reward(s: RunState, r: Screen.Reward): String = buildString {
        append("""<section class="block"><h2 class="run-title">${esc(r.title)}</h2>""")
        append(lines(r.lines))
        if (r.artifacts.isNotEmpty()) {
            val head = when {
                r.lostWeight -> "Or reclaim an Artifact"
                r.boss -> "Choose a boss Artifact first: each has a drawback"
                else -> "Choose an Artifact first"
            }
            append("""<h3 class="label">${esc(head)}</h3><div class="shoplist">""")
            for ((i, a) in r.artifacts.withIndex()) {
                val def = re.rc.artifact(a)
                append(
                    """<button type="button" class="ware" data-act="run-artifact" data-id="$i"><span class="ware-name">${esc(def.name)}</span>""" +
                        """<span class="ware-text">${esc(def.text)}</span></button>""",
                )
            }
            append("</div>")
        }
        val choose = when {
            r.lostWeight -> "Reclaim a card from your Echo's deck"
            r.boss -> "Choose a rare card"
            else -> "Choose a card"
        }
        append("""<h3 class="label">${esc(choose)} <span>or skip it for ${RunEngine.SKIP_HOURS} Hours</span></h3><div class="cards">""")
        val locked = r.artifacts.isNotEmpty() && !r.lostWeight
        for ((i, id) in r.cards.withIndex()) append(defTile(content.card(id), "run-choose", i, disabled = locked))
        append("</div>")
        append(legend(r.cards))
        append("""<div class="btns">""")
        if (!r.glimpsed && !r.lostWeight && !r.boss) append("""<button type="button" data-act="run-glimpse"${if (locked) " disabled" else ""}>Glimpse: see the other branch's 3 cards (+${RunEngine.GLIMPSE_PARADOX} Paradox)</button>""")
        append("""<button type="button" data-act="run-skip"${if (locked) " disabled" else ""}>Skip (+${RunEngine.SKIP_HOURS} Hours)</button></div></section>""")
    }

    /** A card as a tile, from its definition (run screens show deck cards, not combat instances). */
    private fun defTile(def: CardDef, act: String?, id: Int, extra: String = "", disabled: Boolean = false): String {
        val inner = """<span class="card-top"><span class="card-cost">${views.costPips(def.cost)}</span>""" +
            """<span class="card-type">${esc(RulesText.typeLine(def))} · ${def.rarity.name.lowercase()}</span></span>""" +
            """<span class="card-name">${esc(def.name)}</span><span class="card-text">${esc(RulesText.card(def))}</span>$extra"""
        val style = """style="--fc: var(--${fv(def.faction)})" title="${esc(views.tipText(Glossary.card(def)))}""""
        return if (act != null) {
            """<button type="button" class="card" data-act="$act" data-id="$id" $style${if (disabled) " disabled" else ""}>${Art.card(def)}$inner</button>"""
        } else {
            """<div class="card" $style>${Art.card(def)}$inner</div>"""
        }
    }

    /** The keywords on the cards shown, explained once below them. */
    private fun legend(ids: List<String>): String {
        val entries = ids.flatMap { Glossary.card(content.card(it)) }.distinct()
        if (entries.isEmpty()) return ""
        return """<details class="legend"><summary>Keywords on these cards</summary>${views.glossary(entries)}</details>"""
    }

    // ---- the Antiquarian --------------------------------------------------------------------------------------

    private fun shop(s: RunState, sh: Screen.Shop): String = buildString {
        append("""<section class="block"><h2 class="run-title">The Antiquarian</h2><p class="muted">You have <b>${s.hours} Hours</b>. Each item and service once per visit.</p>""")
        append("""<div class="cards">""")
        for ((i, item) in sh.stock.withIndex()) {
            if (item.kind != ShopKind.CARD) continue
            val price = """<span class="price${if (item.sold) " is-sold" else ""}">${if (item.sold) "Bought" else "${item.price} Hours"}</span>"""
            append(defTile(content.card(item.ref!!), "run-choose", i, price, disabled = item.sold || item.price > s.hours))
        }
        append("</div>")
        append(legend(sh.stock.filter { it.kind == ShopKind.CARD }.mapNotNull { it.ref }))
        append("<div class=\"shoplist\">")
        for ((i, item) in sh.stock.withIndex()) {
            val (name, text) = when (item.kind) {
                ShopKind.CARD -> continue
                ShopKind.ARTIFACT -> re.rc.artifact(item.ref!!).let { "Artifact: ${it.name}" to it.text }
                ShopKind.ERASE -> "Erase from history" to "Remove a card from your deck. Costs 25 more each time."
                ShopKind.INSCRIBE -> "Inscribe" to "Upgrade a card (about +30%)."
                ShopKind.OMEN -> "An Omen" to "Reveal Moments on the Weft."
            }
            val off = item.sold || item.price > s.hours
            append(
                """<button type="button" class="ware" data-act="run-choose" data-id="$i"${if (off) " disabled" else ""}>""" +
                    """<span class="ware-name">${esc(name)}</span><span class="ware-text">${esc(text)}</span>""" +
                    """<span class="price${if (item.sold) " is-sold" else ""}">${if (item.sold) "Bought" else "${item.price} Hours"}</span></button>""",
            )
        }
        append("""</div><div class="btns"><button type="button" class="primary" data-act="run-done">Leave the shop</button></div></section>""")
    }

    // ---- Still Points -----------------------------------------------------------------------------------------

    private fun rest(s: RunState, r: Screen.Rest): String = buildString {
        val heal = minOf(s.maxHp * RunEngine.REST_HEAL_PCT / 100, s.maxHp - s.hp)
        val both = dcbb.core.run.Perk.REST_HEAL_AND_INSCRIBE in re.perks(s)
        append("""<section class="block"><h2 class="run-title">A Still Point</h2>""")
        append("""<p class="muted">Time holds still here. Choose one${if (both) " (Bell of the Still Hour: you may both heal and Inscribe)" else ""}.</p><div class="shoplist">""")
        append(ware(0, "Heal", "Restore ${RunEngine.REST_HEAL_PCT}% of your max HP: +$heal HP.", r.healed || (r.inscribed && !both)))
        append(ware(1, "Inscribe", "Upgrade a card in your deck (about +30%).", r.inscribed || (r.healed && !both) || s.deck.none { re.canInscribe(it) }))
        append(ware(2, "Steady", "−${RunEngine.STEADY} Paradox (you have ${s.paradox}).", r.healed || r.inscribed))
        append("</div>")
        if (r.healed || r.inscribed) append("""<div class="btns"><button type="button" class="primary" data-act="run-done">Move on</button></div>""")
        append("</section>")
    }

    private fun ware(i: Int, name: String, text: String, off: Boolean) =
        """<button type="button" class="ware" data-act="run-choose" data-id="$i"${if (off) " disabled" else ""}>""" +
            """<span class="ware-name">${esc(name)}</span><span class="ware-text">${esc(text)}</span></button>"""

    // ---- events -----------------------------------------------------------------------------------------------

    private fun event(ev: Screen.Event): String = buildString {
        val def = re.rc.event(ev.eventId)
        val color = def.faction?.let { fv(it) } ?: if (def.kind == EventKind.ANOMALY) "tear" else "era"
        append("""<section class="block event" style="--fc: var(--$color)"><p class="label">${esc(def.kind.label)}</p><h2 class="run-title">${esc(def.title)}</h2>""")
        append("""<p class="scene">${esc(def.text)}</p><div class="options">""")
        for ((i, o) in ev.options.withIndex()) {
            val fx = o.effects.mapNotNull { re.describe(it) }
            append("""<button type="button" class="option" data-act="run-choose" data-id="$i"${if (o.blocked != null) " disabled" else ""}>""")
            append("""<span class="o-text">${esc(o.text)}</span>""")
            if (fx.isNotEmpty()) append("""<span class="o-fx">${esc(fx.joinToString(" · "))}</span>""")
            o.ripple?.let { rid ->
                val rp = re.rc.ripple(rid)
                append("""<span class="o-ripple">Ripple: <b>${esc(rp.name)}</b>. ${esc(rp.text)}</span>""")
            }
            o.blocked?.let { append("""<span class="o-blocked">${esc(it)}</span>""") }
            append("</button>")
        }
        append("</div></section>")
    }

    // ---- choosing a deck card ---------------------------------------------------------------------------------

    private fun pickCard(s: RunState, pc: Screen.PickCard): String = buildString {
        val verb = pc.purpose.verb
        append("""<section class="block"><h2 class="run-title">$verb a card${if (pc.remaining > 1) " (${pc.remaining} left)" else ""}</h2>""")
        append(
            """<p class="muted">${
                if (pc.purpose == Purpose.ERASE) "It leaves your deck for the rest of the run." else "The upgraded version replaces it for the rest of the run."
            }</p><div class="cards">""",
        )
        for ((i, id) in s.deck.withIndex()) {
            val def = content.card(id)
            if (pc.purpose == Purpose.INSCRIBE) {
                val can = re.canInscribe(id)
                val extra = if (can) """<span class="card-tags">→ ${esc(RulesText.card(content.card(Variants.inscribedId(id))))}</span>""" else ""
                append(defTile(def, "run-choose", i, extra, disabled = !can))
            } else {
                append(defTile(def, "run-choose", i))
            }
        }
        append("</div>")
        append(legend(s.deck.distinct()))
        append("""<div class="btns"><button type="button" data-act="run-done">${if (pc.back != null) "Cancel" else "Skip"}</button></div></section>""")
    }

    // ---- notes and the end ------------------------------------------------------------------------------------

    private fun note(n: Screen.Note): String =
        """<section class="block"><h2 class="run-title">${esc(n.title)}</h2>${lines(n.lines)}""" +
            """<div class="btns"><button type="button" class="primary" data-act="run-done">Continue</button></div></section>"""

    private fun over(o: Screen.Over): String =
        """<section class="block result ${if (o.won) "is-won" else "is-lost"}"><h2 class="run-title modal-title">${esc(o.title)}</h2>${lines(o.lines)}""" +
            """<div class="btns"><button type="button" class="primary" data-act="run-new">New run</button>""" +
            """<button type="button" data-act="copy">Copy run code</button><button type="button" data-act="to-setup">Setup</button></div></section>"""

    private fun fallen(s: RunState, f: Screen.Fallen): String {
        val step = s.anchor?.step ?: 0
        return """<section class="block result is-lost"><h2 class="run-title modal-title">${esc(f.title)}</h2>${lines(f.lines)}""" +
            """<p>Your Anchor still holds. Rewind to step $step: you keep what your Omens showed you, Paradox rises by ${RunEngine.REWIND_DEATH_PARADOX}, """ +
            """and the self you leave behind becomes an Echo you will meet later in the act.</p>""" +
            """<div class="btns"><button type="button" class="primary" data-act="run-rewind-yes">Rewind to step $step (+${RunEngine.REWIND_DEATH_PARADOX} Paradox)</button>""" +
            """<button type="button" data-act="run-done">Let the run end</button></div></section>"""
    }

    /** Rewinding outside combat asks once, in the page (the viewer can't show confirm dialogs). */
    private fun rewindConfirm(s: RunState): String {
        if (!app.confirmRewind || !re.canRewind(s) || s.screen is Screen.Fallen) return ""
        val step = s.anchor?.step ?: 0
        return """<div class="notice is-anchor" role="alertdialog"><span>Rewind to your Anchor at step $step? You lose everything since, except what your Omens showed you. """ +
            """Paradox +${RunEngine.REWIND_PARADOX}, your Anchor is spent, and the self you leave behind becomes an Echo.</span>""" +
            """<button type="button" class="primary" data-act="run-rewind-yes">Rewind</button><button type="button" data-act="run-rewind-no">Cancel</button></div>"""
    }

    private fun lines(ls: List<String>) = if (ls.isEmpty()) "" else """<ul class="lines">${ls.joinToString("") { "<li>${esc(it)}</li>" }}</ul>"""

    private fun copyModal(text: String) =
        """<div class="modal" role="dialog" aria-label="Copy the run log"><div class="modal-box">""" +
            """<h2 class="modal-title">Copy the run log</h2><p class="muted">Your browser blocked automatic copying. Select the text and copy it.</p>""" +
            """<textarea id="copy-text" rows="10" readonly spellcheck="false">${esc(text)}</textarea>""" +
            """<button type="button" class="primary" data-act="copy-close">Done</button></div></div>"""

    private companion object {
        const val ANCHOR_TIP = "Your Anchor holds the run as it was at the start of the act, or where you last Steadied or accepted a Shrine's offer. " +
            "Rewind back to it outside combat (+3 Paradox) or when you fall (+5). It is spent once used, and each act brings a new one."
        const val POOL_TIP = "The Moments you don't choose wait in the Branch Pool. Each may return later, changed: fights as Ambushes, " +
            "Elites Empowered, events as their Consequences, shops Looted, Shrines Desecrated."
        const val LEVEL_TIP = "Fights give XP: 10, an Elite 25, a boss 50. Each level brings +3 max HP and a choice of Imprints."
    }
}
