package dcbb.web

import dcbb.core.engine.Action
import dcbb.core.engine.EndTurn
import dcbb.core.engine.Engine
import dcbb.core.engine.Payment
import dcbb.core.engine.PayStyle
import dcbb.core.engine.PlayCard
import dcbb.core.engine.ResolveForesee
import dcbb.core.engine.TrackNeed
import dcbb.core.engine.UseSignature
import dcbb.core.model.CardType
import dcbb.core.model.Cost
import dcbb.core.model.EnergyColor
import dcbb.core.model.Faction
import dcbb.core.model.Signature
import dcbb.core.model.StatusType
import dcbb.core.state.CardInst
import dcbb.core.state.CombatState
import dcbb.core.state.Phase
import dcbb.core.state.TrackItem
import dcbb.core.state.TrackKind
import dcbb.core.text.Glossary
import dcbb.core.text.RulesText

/** HTML for every screen, rendered from the app's state. All text from the game is escaped. */
class Views(private val app: App) {
    private val content get() = app.content
    private val engine get() = app.engine

    // ---- setup screen ---------------------------------------------------------------------------------------

    fun setup(): String = buildString {
        append(masthead("Play Act I of The First Hour, or test single fights in the lab. Everything runs on the same rules engine as the simulator."))
        append(noticeBox())
        append("""<main class="setup">""")

        append("""<section class="block"><h2 class="label">Operative</h2><div class="ops">""")
        for (id in content.operatives.keys.sorted()) {
            val op = content.operative(id)
            val on = app.setup.op == id
            append(
                """<button type="button" class="op${if (on) " is-on" else ""}" data-act="op" data-id="$id" aria-pressed="$on" style="--fc: var(--${fv(op.faction)})">""" +
                    Art.operative(op) +
                    """<span class="op-name">${esc(op.name)}</span>""" +
                    """<span class="op-meta">${op.faction.label} · ${op.maxHp} HP</span>""" +
                    """<span class="op-sig">${esc(signatureLine(op.signature, 0))}</span>""" +
                    """<span class="op-blurb">${esc(BLURBS[id] ?: "")}</span></button>""",
            )
        }
        append("</div></section>")

        val era = app.runEngine.rc.eras.first()
        append(
            """<section class="block runstart"><h2 class="label">The First Hour · Act ${era.act}: ${esc(era.name)}</h2>""" +
                """<p>${era.steps} steps on the Weft, then the act boss. HP, Paradox and Debt carry between fights; """ +
                """rewards, the Antiquarian, Still Points, events and a Divergence shape your deck.</p>""" +
                """<div class="start"><div class="seed"><label for="seed" class="label">Seed</label>""" +
                """<input id="seed" type="number" inputmode="numeric" value="${app.setup.seed}">""" +
                """<button type="button" data-act="reroll">New seed</button></div><div class="btns">""" +
                (if (app.savedRun() != null) """<button type="button" data-act="run-resume">Resume your run</button>""" else "") +
                """<button type="button" class="primary big" data-act="run-start">Start a run</button></div></div></section>""",
        )

        append("""<h2 class="lab-head">Combat lab <span>one fight, any deck and encounter</span></h2>""")
        val decks = content.decks.getValue(app.setup.op)
        append("""<section class="block"><h2 class="label">Deck</h2><div class="seg" role="group" aria-label="Deck">""")
        for ((name, list) in decks) {
            val on = app.setup.deck == name
            append("""<button type="button" class="${if (on) "is-on" else ""}" data-act="deck" data-id="$name" aria-pressed="$on">${cap(name)} · ${list.size} cards</button>""")
        }
        append("""</div><button type="button" class="linkish" data-act="drawer" data-id="DECK">${if (app.drawer == Drawer.DECK) "Hide" else "Show"} the cards</button>""")
        if (app.drawer == Drawer.DECK) {
            append("""<ul class="decklist">""")
            val counts = content.deck(app.setup.op, app.setup.deck).groupingBy { it }.eachCount()
            for ((id, count) in counts) {
                val def = content.card(id)
                append(
                    """<li style="--fc: var(--${fv(def.faction)})"><span class="dl-count">${count}×</span>""" +
                        """<span class="dl-name">${esc(def.name)}</span><span class="dl-cost">${costPips(def.cost)}</span>""" +
                        """<span class="dl-text">${esc(RulesText.card(def))}</span></li>""",
                )
            }
            append("</ul>")
        }
        append("</section>")

        append("""<section class="block"><h2 class="label">Encounter</h2><div class="encs">""")
        for (enc in content.encounters.filter { !it.runOnly }) {
            val on = app.setup.enc == enc.id
            val enemies = enc.enemies.groupingBy { it }.eachCount().entries.joinToString(", ") { (id, c) ->
                val e = content.enemy(id)
                "${esc(e.name)} ${e.maxHp}${if (c > 1) " ×$c" else ""}"
            }
            append(
                """<button type="button" class="enc${if (on) " is-on" else ""}${if (enc.elite) " is-elite" else ""}" data-act="enc" data-id="${enc.id}" aria-pressed="$on">""" +
                    """<span class="enc-name">${esc(enc.name)}</span>""" +
                    """<span class="enc-where">${place(enc.enemies.first())}${if (enc.elite) " · Elite" else ""}</span>""" +
                    """<span class="enc-foes">$enemies</span></button>""",
            )
        }
        append("</div></section>")

        append(
            """<section class="block start"><p class="muted">Uses the seed above.</p>""" +
                """<button type="button" class="primary" data-act="start">Start fight</button></section>""",
        )

        append(
            """<details class="block"><summary>Replay a run or fight log</summary>""" +
                """<p class="muted">Paste a log copied from this page (or just its replay code) to rebuild that run or fight exactly.</p>""" +
                """<textarea id="replay-code" rows="4" spellcheck="false"></textarea>""" +
                """<button type="button" data-act="paste-code">Replay</button></details>""",
        )
        append("</main>")
    }

    // ---- combat screen ----------------------------------------------------------------------------------------

    fun combat(): String {
        val r = app.run
        val s = app.session
        val st = app.fightState ?: return ""
        val opDef = content.operative(st.player.operativeId)
        return buildString {
            if (r != null) {
                val fight = r.state.screen as dcbb.core.run.Screen.Fight
                append(masthead("${opDef.name} · ${fight.moment.title} · step ${minOf(r.state.step, app.runEngine.era(r.state).steps)} · round ${st.round}", run = true))
                append(app.runViews.runBar(r.state))
                append(tools(r.undo.isNotEmpty(), st.phase.over, inRun = true))
            } else if (s != null) {
                append(masthead("${opDef.name} · ${s.setup.deck} deck · ${content.encounter(s.setup.enc).name} · seed ${s.setup.seed} · round ${st.round}"))
                append(tools(s.undo.isNotEmpty(), st.phase.over, inRun = false))
            }
            append(noticeBox())
            append("""<main class="board">""")
            append(enemies(st))
            append(track(st))
            append(you(st))
            append(present(st))
            if (opDef.otherHandSize > 0) append(otherHand(st))
            append(drawer(st))
            append(logSection(if (r != null) r.fightLog() else s?.log.orEmpty()))
            if (r != null) append(app.runViews.runDrawer(r.state))
            append("</main>")
            app.pick?.let { append(sheet(st, it)) }
            if (st.pending != null) app.foresee?.let { append(foreseeModal(st, it)) }
            if (st.phase.over && app.showResult) {
                append(if (r != null) runResultModal(st) else resultModal(st, s?.actions?.size ?: 0))
            }
            app.copyText?.let { append(copyModal(it)) }
        }
    }

    internal fun masthead(line: String, run: Boolean = false) =
        """<header class="masthead"><h1 class="brand">Anachronist <span>${if (run) "The First Hour" else "Combat Lab"}</span></h1><p class="where">${esc(line)}</p></header>"""

    private fun tools(canUndo: Boolean, over: Boolean, inRun: Boolean) = buildString {
        append("""<nav class="tools" aria-label="Fight tools">""")
        append("""<button type="button" data-act="undo"${if (canUndo) "" else " disabled"}>Undo</button>""")
        append("""<button type="button" data-act="hint"${if (over) " disabled" else ""}>Hint</button>""")
        append("""<button type="button" data-act="bot-turn"${if (over) " disabled" else ""}>Bot plays turn</button>""")
        if (inRun) {
            if (over) append("""<button type="button" class="primary" data-act="run-done">Continue</button>""")
            if (!over) append(app.runViews.autoButtons(fight = true))
            append("""<button type="button" data-act="copy">Copy run code</button>""")
            append("""<button type="button" data-act="to-setup">Leave run</button>""")
        } else {
            append("""<button type="button" data-act="restart">Restart</button>""")
            append("""<button type="button" data-act="new-seed">New seed</button>""")
            append("""<button type="button" data-act="copy">Copy log</button>""")
            append("""<button type="button" data-act="to-setup">Setup</button>""")
        }
        append("</nav>")
    }

    internal fun noticeBox(): String {
        val hint = app.hint
        if (hint != null) {
            return """<div class="notice is-hint" role="status"><span>The planner bot would: <b>${esc(describe(hint))}</b></span>""" +
                """<button type="button" data-act="do-hint">Do it</button><button type="button" data-act="dismiss">Dismiss</button></div>"""
        }
        val notice = app.notice ?: return ""
        if (app.pick != null) return "" // shown inside the sheet instead
        return """<div class="notice" role="status"><span>${esc(notice)}</span><button type="button" data-act="dismiss">Dismiss</button></div>"""
    }

    private fun enemies(st: CombatState): String = buildString {
        val wantsEnemy = pickNeedsEnemy(st)
        append("""<section class="enemies" aria-label="Enemies">""")
        for (e in st.enemies) {
            val def = content.enemy(e.defId)
            val target = wantsEnemy && e.alive
            val chosen = app.pick?.target == e.uid
            val cls = buildList {
                add("enemy")
                if (!e.alive) add("is-dead")
                if (target) add("is-target")
                if (chosen) add("is-chosen")
            }.joinToString(" ")
            val tag = if (target) "button type=\"button\" data-act=\"enemy\" data-id=\"${e.uid}\"" else "article"
            val close = if (target) "button" else "article"
            append("""<$tag class="$cls" style="--fc: var(--${fv(def.faction)})" aria-label="${esc(def.name)}">""")
            append("""<span class="enemy-top">${Art.enemy(def, content.operative(st.player.operativeId))}<span class="enemy-head"><span class="enemy-name">${esc(def.name)}</span><span class="tag">${if (e.script != null) "Echo" else def.faction.label}${if (def.elite) " · Elite" else ""}</span></span></span>""")
            if (!e.alive) {
                append("""<span class="nums">Defeated</span>""")
            } else {
                append(meter(e.hp, e.maxHp, "HP"))
                append("""<span class="nums"><span>HP ${e.hp}/${e.maxHp}</span>${if (e.block > 0) "<span>Block ${e.block}</span>" else ""}${statuses(e.statuses)}</span>""")
                val intents = st.track.filter { it.kind == TrackKind.INTENT && it.enemyUid == e.uid }.sortedBy { it.countdown }
                if (intents.isNotEmpty()) {
                    append("""<span class="intents">""")
                    for (i in intents) append("""<span class="intent" title="${esc(intentTip(i))}">${dial(i.countdown)}<span>${intentText(st, i)}</span></span>""")
                    append("</span>")
                }
            }
            append("</$close>")
        }
        append("</section>")
    }

    private fun track(st: CombatState): String = buildString {
        val need = pickTrackNeed(st)
        val candidates = if (need == TrackNeed.NONE) emptySet() else engine.trackCandidates(st, need).map { it.id }.toSet()
        append("""<section class="track" aria-label="The Track"><h2 class="label">The Track <span>what lands, and when</span></h2><div class="lanes">""")
        val lanes = listOf(1 to "Next enemy phase", 2 to "In 2", 3 to "In 3", 4 to "Later")
        for ((c, title) in lanes) {
            val items = st.track.filter { if (c == 4) it.countdown >= 4 else it.countdown == c }
                .sortedWith(compareBy({ if (it.kind == TrackKind.SCHEDULED) 0 else 1 }, { it.order }))
            append("""<div class="lane"><h3>${dial(c)} $title</h3>""")
            if (items.isEmpty()) append("""<p class="empty">Nothing</p>""")
            for (item in items) {
                val who = if (item.kind == TrackKind.SCHEDULED) "You" else item.enemyUid?.let { st.enemy(it) }?.let { content.enemy(it.defId).name } ?: "?"
                val selectable = item.id in candidates
                val chosen = app.pick?.track == item.id
                val cls = "chip ${if (item.kind == TrackKind.SCHEDULED) "is-yours" else "is-foe"}${if (selectable) " is-target" else ""}${if (chosen) " is-chosen" else ""}"
                val body = """<span class="who">${esc(who)}</span><span>${intentText(st, item)}</span>"""
                val tip = if (item.kind == TrackKind.INTENT) intentTip(item) else tipText(Glossary.face(dcbb.core.model.Face(CardType.SKILL, item.effects)) + Glossary.SCHEDULE)
                val title = if (tip.isEmpty()) "" else """ title="${esc(tip)}""""
                if (selectable) {
                    append("""<button type="button" class="$cls" data-act="track" data-id="${item.id}"$title>$body</button>""")
                } else {
                    append("""<div class="$cls"$title>$body</div>""")
                }
            }
            append("</div>")
        }
        append("</div></section>")
    }

    private fun you(st: CombatState): String = buildString {
        val p = st.player
        val op = content.operative(p.operativeId)
        append("""<section class="you" aria-label="You" style="--fc: var(--${fv(op.faction)})">""")
        append("""<div class="you-head"><h2 class="you-name">${esc(op.name)}</h2><span class="nums"><span>HP ${p.hp}/${p.maxHp}</span><span>Block ${p.block}</span>${statuses(p.statuses)}</span></div>""")
        append(meter(p.hp, p.maxHp, "Your HP"))

        val pool = EnergyColor.entries.filter { p.energyOf(it) > 0 }.joinToString("") { c -> pip(c).repeat(p.energyOf(c)) }
        val debtWhen = if (st.phase.over) "carried into your next fight" else "repaid at your next Dawn"
        val debt = if (p.debtTotal > 0) """<span class="debt">Debt ${p.debtTotal}: $debtWhen</span>""" else ""
        val borrowLeft = Engine.BORROW_LIMIT - p.borrowedThisTurn
        val interest = if (p.borrowedThisTurn == 0) " (first Borrow +${Engine.BORROW_INTEREST} interest)" else ""
        append("""<div class="row energy"><span class="label">Energy</span><span class="pool" aria-label="${p.energyTotal} energy">${pool.ifEmpty { "<span class=\"muted\">none</span>" }}</span>$debt<span class="muted">Borrow left $borrowLeft$interest</span></div>""")

        val seg = (1..10).joinToString("") { i -> """<i class="${if (i <= p.paradox) "on" else ""}${if (i == 10) " last" else ""}"></i>""" }
        append("""<div class="row"><span class="label">Paradox</span><span class="pdx" role="img" aria-label="Paradox ${p.paradox} of 10">$seg</span><span class="nums">${p.paradox}/10${if (p.paradox >= 7) " · Unravel at 10" else ""}</span></div>""")

        val constants = if (p.constants.isEmpty()) {
            """<span class="muted">none</span>"""
        } else {
            p.constants.joinToString("") { c ->
                val def = content.card(c.defId)
                """<span class="const" style="--fc: var(--${fv(def.faction)})" title="${esc(RulesText.card(def))}"><b>${esc(def.name)}</b><small>${esc(RulesText.card(def))}</small></span>"""
            }
        }
        append("""<div class="row constants"><span class="label">Constants ${p.constants.size}/3${if (p.constantsSilenced) " · snipped this turn" else ""}</span>$constants</div>""")

        val known = p.future.count { it.known }
        append(
            """<div class="row zones">""" +
                zoneButton(Drawer.FUTURE, "Future ${p.future.size}${if (known > 0) " ($known Known)" else ""}") +
                zoneButton(Drawer.PAST, "Past ${p.past.size}") +
                zoneButton(Drawer.ERASED, "Erased ${p.erased.size}") + "</div>",
        )

        val sigOn = app.pick != null && app.pick?.uid == null
        val strikeBonus = p.constants.sumOf { engine.constantSpec(it)?.strikeDamage ?: 0 }
        append(
            """<div class="row act">""" +
                """<button type="button" class="sig${if (sigOn) " is-on" else ""}" data-act="sig"${if (p.signatureUsed || st.phase.over) " disabled" else ""}>""" +
                """${esc(signatureLine(op.signature, strikeBonus))}${if (p.signatureUsed) " · used" else ""}</button>""" +
                """<button type="button" class="primary" data-act="end"${if (st.phase.over || st.pending != null) " disabled" else ""}>End turn</button></div>""",
        )
        append("</section>")
    }

    private fun zoneButton(d: Drawer, text: String) =
        """<button type="button" class="linkish${if (app.drawer == d) " is-on" else ""}" data-act="drawer" data-id="${d.name}" aria-expanded="${app.drawer == d}">$text</button>"""

    private fun present(st: CombatState): String = buildString {
        val p = st.player
        append("""<section class="hand" aria-label="Your Present"><h2 class="label">Present <span>${p.present.size}/10 · tap a card, tap again to play</span></h2><div class="cards">""")
        if (p.present.isEmpty()) append("""<p class="empty">Your Present is empty.</p>""")
        for (c in p.present) append(cardTile(st, c, interactive = true))
        append("</div></section>")
    }

    private fun otherHand(st: CombatState): String = buildString {
        append("""<section class="hand other" aria-label="Your Other Hand"><h2 class="label">Other Hand <span>Shift swaps these with your Present</span></h2><div class="cards">""")
        if (st.player.otherHand.isEmpty()) append("""<p class="empty">Empty</p>""")
        for (c in st.player.otherHand) append(cardTile(st, c, interactive = false))
        append("</div></section>")
    }

    private fun cardTile(st: CombatState, c: CardInst, interactive: Boolean): String {
        val def = content.card(c.defId)
        val cost = engine.effectiveCost(st, c)
        val changed = cost != def.cost
        val tags = buildList {
            if (c.known) add("Known")
            if (c.blank) add("Blank")
            if (c.bleed) add("Bleed-through")
            if (def.retain || c.retainThisTurn) add("Retain")
            if (c.face != null) add("Misprinted")
            if (c.granted != null) add("Forked")
            if (c.iterations > 0) add("Played ×${c.iterations}")
        }
        val chosen = interactive && app.pick?.uid == c.uid
        val cls = "card${if (chosen) " is-on" else ""}${if (c.blank) " is-blank" else ""}"
        val inner = """<span class="card-top"><span class="card-cost${if (changed) " is-changed" else ""}">${costPips(cost)}</span>""" +
            """<span class="card-type">${esc(typeOf(c))}</span></span>""" +
            """<span class="card-name">${esc(def.name)}</span>""" +
            """<span class="card-text">${esc(cardText(c))}</span>""" +
            if (tags.isEmpty()) "" else """<span class="card-tags">${tags.joinToString(" · ")}</span>"""
        val style = """style="--fc: var(--${fv(def.faction)})""""
        val tip = """title="${esc(tipText(instGlossary(c)))}""""
        return if (interactive) {
            """<button type="button" class="$cls" data-act="card" data-id="${c.uid}" $style $tip aria-pressed="$chosen">${Art.card(def)}$inner</button>"""
        } else {
            """<div class="$cls" $style $tip>${Art.card(def)}$inner</div>"""
        }
    }

    private fun drawer(st: CombatState): String {
        val p = st.player
        val (title, rows) = when (app.drawer) {
            Drawer.PAST -> "Past (most recent last)" to p.past.map { cardRow(it) }
            Drawer.ERASED -> "Erased" to p.erased.map { cardRow(it) }
            Drawer.FUTURE -> "Future (top first): only Known cards are shown" to p.future.mapIndexed { i, c ->
                if (c.known) "<li><span class=\"dl-count\">#${i + 1}</span>${cardRowInner(c)}</li>" else "<li class=\"muted\"><span class=\"dl-count\">#${i + 1}</span>Unknown</li>"
            }
            else -> return ""
        }
        return """<section class="drawer" aria-label="${esc(title)}"><h2 class="label">${esc(title)}</h2>""" +
            if (rows.isEmpty()) """<p class="empty">Nothing here.</p></section>""" else """<ul class="decklist">${rows.joinToString("")}</ul></section>"""
    }

    private fun cardRow(c: CardInst) = "<li>${cardRowInner(c)}</li>"

    private fun cardRowInner(c: CardInst): String {
        val def = content.card(c.defId)
        return """<span class="dl-name" style="--fc: var(--${fv(def.faction)})">${esc(def.name)}</span><span class="dl-text">${esc(cardText(c))}</span>"""
    }

    private fun logSection(log: List<String>): String {
        val full = app.drawer == Drawer.LOG
        val lines = if (full) log else log.takeLast(14)
        return """<section class="log" aria-label="Combat log"><h2 class="label">Log</h2><ol class="log-lines">""" +
            lines.joinToString("") { line ->
                val round = line.startsWith("— Round")
                """<li${if (round) " class=\"round\"" else ""}>${esc(line)}</li>"""
            } + "</ol>" +
            """<button type="button" class="linkish" data-act="drawer" data-id="LOG">${if (full) "Show recent lines" else "Show the full log (${log.size} lines)"}</button></section>"""
    }

    // ---- the play sheet ---------------------------------------------------------------------------------------

    private fun sheet(st: CombatState, pick: Pick): String = buildString {
        val p = st.player
        val op = content.operative(p.operativeId)
        val inst = pick.uid?.let { uid -> p.present.firstOrNull { it.uid == uid } }
        if (pick.uid != null && inst == null) return ""
        val title: String
        val text: String
        val faces = inst?.let { engine.faces(it) }
        val face = faces?.getOrNull(pick.face)
        if (inst != null && face != null) {
            title = content.card(inst.defId).name
            text = if (faces.size > 1) RulesText.face(face) else cardText(inst)
        } else {
            title = op.signature.label
            text = signatureLine(op.signature, p.constants.sumOf { engine.constantSpec(it)?.strikeDamage ?: 0 })
        }

        append("""<div class="sheet" role="dialog" aria-label="${esc(title)}"><div class="sheet-inner">""")
        append("""<div class="sheet-head"><div><b class="sheet-title">${esc(title)}</b>""")
        if (inst != null) append(""" <span class="card-cost">${costPips(engine.effectiveCost(st, inst))}</span>""")
        append("""</div><button type="button" class="ghost" data-act="cancel">Cancel</button></div>""")
        append("""<p class="sheet-text">${esc(text)}</p>""")
        val gloss = if (inst != null && face != null) {
            (if (faces.size > 1 || inst.face != null || inst.granted != null) Glossary.face(face) else Glossary.card(content.card(inst.defId))) +
                instStates(inst)
        } else {
            Glossary.signature(op.signature)
        }
        append(glossary(gloss.distinct()))

        if (faces != null && faces.size > 1) {
            append(choiceRow("Face", faces.indices.joinToString("") { i ->
                choice("face", i, pick.face == i, "${'A' + i}: ${faces[i].type.label}")
            }))
        }

        val needs = face?.let { engine.needs(it) }
        val alive = st.livingEnemies
        val needsEnemy = if (inst == null) op.signature == Signature.STRIKE else needs?.enemy == true
        if (needsEnemy && alive.size > 1) {
            append(choiceRow("Target", alive.joinToString("") { e ->
                choice("target", e.uid, pick.target == e.uid, "${content.enemy(e.defId).name} ${e.hp}/${e.maxHp}")
            }))
        }
        if (needs != null && needs.track != TrackNeed.NONE) {
            val cands = engine.trackCandidates(st, needs.track)
            when {
                cands.size > 1 -> append(choiceRow(if (needs.track == TrackNeed.DELAYABLE) "Delay which" else "Which intent", cands.joinToString("") { t ->
                    choice("track", t.id, pick.track == t.id, "${trackWho(st, t)}: ${t.label} (${t.countdown})")
                }))
                cands.isEmpty() -> append("""<p class="muted">No legal Track target: that part of the card does nothing.</p>""")
            }
        }
        if (needs != null && needs.card) {
            val others = p.present.filter { it.uid != inst.uid }
            if (others.size > 1) {
                append(choiceRow("Which card", others.joinToString("") { c -> choice("card-target", c.uid, pick.card == c.uid, content.card(c.defId).name) }))
            }
        }
        if (needs != null && needs.recall > 0 && p.past.isNotEmpty()) {
            append(choiceRow("Recall up to ${needs.recall} (default: the most recent)", p.past.asReversed().joinToString("") { c ->
                choice("recall", c.uid, c.uid in pick.recall, content.card(c.defId).name)
            }))
        }
        val shiftCount = if (inst == null) (if (op.signature == Signature.SHIFT) 1 else 0) else needs?.shift ?: 0
        if (shiftCount > 0) append(shiftRows(st, pick, shiftCount, inst))
        if (face != null && face.type == CardType.CONSTANT && p.constants.size >= Engine.CONSTANT_SLOTS) {
            append(choiceRow("Replace which Constant (default: the oldest)", p.constants.joinToString("") { c ->
                choice("replace", c.uid, pick.replace == c.uid, content.card(c.defId).name)
            }))
        }
        if (inst != null) append(payRow(st, inst, pick))

        app.notice?.let { append("""<p class="sheet-error" role="alert">${esc(it)}</p>""") }
        val verb = if (inst == null) "Use ${op.signature.label}" else "Play ${content.card(inst.defId).name}"
        append("""<div class="sheet-foot"><button type="button" class="primary" data-act="play">${esc(verb)}</button></div>""")
        append("</div></div>")
    }

    private fun shiftRows(st: CombatState, pick: Pick, count: Int, playing: CardInst?): String = buildString {
        val p = st.player
        val hasOther = content.operative(p.operativeId).otherHandSize > 0
        val outs = p.present.filter { it.uid != playing?.uid && pick.shifts.none { s -> s.presentUid == it.uid } }
        val chosen = pick.shifts.joinToString(", ") { s ->
            "${cardName(st, s.presentUid)} ⇄ ${s.otherUid?.let { cardName(st, it) } ?: "top of your Future"}"
        }
        val label = "Shift ${pick.shifts.size}/$count" + if (chosen.isNotEmpty()) ": $chosen" else ""
        val full = pick.shifts.size >= count
        val body = buildString {
            if (!full) {
                if (hasOther) {
                    val waiting = pick.shiftOut
                    if (waiting == null) {
                        append("""<span class="muted">Swap out:</span>""")
                        outs.forEach { append(choice("shift-out", it.uid, false, content.card(it.defId).name)) }
                    } else {
                        append("""<span class="muted">Swap ${esc(cardName(st, waiting))} for:</span>""")
                        p.otherHand.filter { o -> pick.shifts.none { it.otherUid == o.uid } }
                            .forEach { append(choice("shift-in", it.uid, false, content.card(it.defId).name)) }
                    }
                } else {
                    append("""<span class="muted">Swap with the top of your Future:</span>""")
                    outs.forEach { append(choice("shift-top", it.uid, false, content.card(it.defId).name)) }
                }
            }
            if (pick.shifts.isNotEmpty() || pick.shiftOut != null) append(choice("shift-clear", 0, false, "Clear"))
        }
        append(choiceRow(label, body))
    }

    private fun payRow(st: CombatState, inst: CardInst, pick: Pick): String {
        val cost = engine.effectiveCost(st, inst)
        if (cost.total == 0) return """<p class="muted">Free to play.</p>"""
        val now = engine.payment(st, inst, pick.pay, allowBorrow = false)
        val withBorrow = engine.payment(st, inst, pick.pay, allowBorrow = true)
        // Built from trusted pieces only: energy pips and fixed wording.
        val preview = when {
            now != null -> "Pays ${spend(now)}${if (now.attuned) " · Attuned" else ""}"
            withBorrow != null && pick.borrow -> "Pays ${spend(withBorrow)}, borrowing ${withBorrow.borrowed}" +
                (if (st.player.borrowedThisTurn == 0) " (+${Engine.BORROW_INTEREST} interest)" else "") +
                (if (withBorrow.attuned) " · Attuned" else "")
            withBorrow != null -> "Not enough energy. Turn on Borrow to play it."
            else -> "Not enough energy, even with Borrow."
        }
        val styles = choice("pay", 0, pick.pay == PayStyle.AUTO, "Attuned first", "A") +
            choice("pay", 0, pick.pay == PayStyle.NEUTRAL_FIRST, "Neutral first", "N") +
            choice("borrow", 0, pick.borrow, if (pick.borrow) "Borrow: on" else "Borrow: off")
        return """<div class="choice-row"><span class="label">Pay</span><span class="pay-preview">$preview</span><div class="choices">$styles</div></div>"""
    }

    private fun spend(pay: Payment): String {
        val all = (pay.spend.keys + pay.borrow.keys).sortedBy { it.ordinal }
        return all.joinToString("") { c -> pip(c).repeat((pay.spend[c] ?: 0) + (pay.borrow[c] ?: 0)) }.ifEmpty { "nothing" }
    }

    internal fun choiceRow(label: String, body: String) =
        """<div class="choice-row"><span class="label">${esc(label)}</span><div class="choices">$body</div></div>"""

    internal fun choice(act: String, id: Int, on: Boolean, text: String, idText: String? = null) =
        """<button type="button" class="pill${if (on) " is-on" else ""}" data-act="$act" data-id="${idText ?: id}" aria-pressed="$on">${esc(text)}</button>"""

    // ---- modals -------------------------------------------------------------------------------------------------

    private fun foreseeModal(st: CombatState, f: ForeseeDraft): String = buildString {
        append("""<div class="modal" role="dialog" aria-label="Foresee"><div class="modal-box">""")
        append("""<h2 class="modal-title">Foresee</h2>""")
        append("""<p class="muted">The top of your Future, now Known. The first card is on top. """)
        append(if (f.tutor) "Choose one to put into your Present.</p>" else "Send any to the bottom.</p>")
        append("""<ol class="fs-list">""")
        for (uid in f.order) {
            val c = st.player.future.firstOrNull { it.uid == uid } ?: continue
            val def = content.card(c.defId)
            val bottom = uid in f.bottom
            val take = f.take == uid
            append("""<li class="${if (bottom) "is-bottom" else ""}${if (take) " is-take" else ""}" style="--fc: var(--${fv(def.faction)})">""")
            append("""<span class="fs-card"><b>${esc(def.name)}</b><span class="muted">${esc(cardText(c))}</span></span><span class="fs-btns">""")
            append("""<button type="button" class="pill" data-act="fs-up" data-id="$uid" aria-label="Move ${esc(def.name)} up">↑</button>""")
            append("""<button type="button" class="pill" data-act="fs-down" data-id="$uid" aria-label="Move ${esc(def.name)} down">↓</button>""")
            if (!take) append("""<button type="button" class="pill${if (bottom) " is-on" else ""}" data-act="fs-bottom" data-id="$uid">${if (bottom) "Bottom" else "To bottom"}</button>""")
            if (f.tutor) append("""<button type="button" class="pill${if (take) " is-on" else ""}" data-act="fs-take" data-id="$uid">${if (take) "Taking" else "Take"}</button>""")
            append("</span></li>")
        }
        append("</ol>")
        app.notice?.let { append("""<p class="sheet-error" role="alert">${esc(it)}</p>""") }
        append("""<button type="button" class="primary" data-act="fs-ok">Done</button></div></div>""")
    }

    private fun resultModal(st: CombatState, actions: Int): String {
        val p = st.player
        val (title, cls) = when (st.phase) {
            Phase.WON -> "Victory" to "is-won"
            Phase.LOST -> "Defeat" to "is-lost"
            else -> "Stalemate" to "is-draw"
        }
        val line = when (st.phase) {
            Phase.WON -> "Round ${st.round} · ${p.hp}/${p.maxHp} HP left · ${p.maxHp - p.hp} HP lost"
            Phase.LOST -> "You fell in round ${st.round}."
            else -> "The fight hit the ${st.roundCap}-round cap."
        }
        return """<div class="modal" role="dialog" aria-label="$title"><div class="modal-box result $cls">""" +
            """<h2 class="modal-title">$title</h2><p>${esc(line)} · $actions actions</p><div class="btns">""" +
            """<button type="button" class="primary" data-act="restart">Fight again</button>""" +
            """<button type="button" data-act="new-seed">New seed</button>""" +
            """<button type="button" data-act="to-setup">Change setup</button>""" +
            """<button type="button" data-act="copy">Copy log</button>""" +
            """<button type="button" class="ghost" data-act="result-close">See the board</button></div></div></div>"""
    }

    private fun runResultModal(st: CombatState): String {
        val p = st.player
        val (title, cls) = when (st.phase) {
            Phase.WON -> "Victory" to "is-won"
            Phase.LOST -> "Defeat" to "is-lost"
            else -> "Stalemate" to "is-draw"
        }
        val line = when (st.phase) {
            Phase.WON -> "Round ${st.round} · ${p.hp}/${p.maxHp} HP left. Your HP, Paradox and any Debt carry on."
            Phase.LOST -> if (app.run?.state?.anchor != null) {
                "You fell in round ${st.round}. Your Anchor still holds: continue to Rewind, or let the run end."
            } else {
                "You fell in round ${st.round}. The run ends here."
            }
            else -> "The fight hit the ${st.roundCap}-round cap."
        }
        return """<div class="modal" role="dialog" aria-label="$title"><div class="modal-box result $cls">""" +
            """<h2 class="modal-title">$title</h2><p>${esc(line)}</p><div class="btns">""" +
            """<button type="button" class="primary" data-act="run-done">Continue</button>""" +
            """<button type="button" data-act="undo">Undo</button>""" +
            """<button type="button" class="ghost" data-act="result-close">See the board</button></div></div></div>"""
    }

    private fun copyModal(text: String) =
        """<div class="modal" role="dialog" aria-label="Copy the log"><div class="modal-box">""" +
            """<h2 class="modal-title">Copy the log</h2><p class="muted">Your browser blocked automatic copying. Select the text and copy it.</p>""" +
            """<textarea id="copy-text" rows="10" readonly spellcheck="false">${esc(text)}</textarea>""" +
            """<button type="button" class="primary" data-act="copy-close">Done</button></div></div>"""

    // ---- helpers ------------------------------------------------------------------------------------------------

    fun describe(a: Action): String {
        val st = app.fightState ?: return ""
        return when (a) {
            EndTurn -> "End your turn"
            is ResolveForesee -> "Keep the Foresee order" + if (a.bottom.isNotEmpty()) " and send ${a.bottom.size} to the bottom" else ""
            is UseSignature -> buildString {
                append("Use ${content.operative(st.player.operativeId).signature.label}")
                a.target?.let { append(" on ${enemyName(st, it)}") }
                a.shiftPair?.let { append(": swap ${cardName(st, it.presentUid)} for ${it.otherUid?.let { o -> cardName(st, o) } ?: "the top of your Future"}") }
            }
            is PlayCard -> buildString {
                append("Play ${cardName(st, a.cardUid)}")
                if (a.face > 0) append(" (face ${'A' + a.face})")
                a.target?.let { append(" on ${enemyName(st, it)}") }
                a.trackTarget?.let { id -> st.trackItem(id)?.let { append(" on ${trackWho(st, it)}: ${it.label}") } }
                a.cardTarget?.let { append(" on ${cardName(st, it)}") }
                if (a.shiftPairs.isNotEmpty()) append(", swapping " + a.shiftPairs.joinToString(", ") { cardName(st, it.presentUid) })
                if (a.pay == PayStyle.NEUTRAL_FIRST) append(", paying Neutral first")
                if (a.allowBorrow) append(", borrowing if needed")
            }
        }
    }

    private fun pickNeedsEnemy(st: CombatState): Boolean {
        val pick = app.pick ?: return false
        if (pick.uid == null) return content.operative(st.player.operativeId).signature == Signature.STRIKE
        val inst = st.player.present.firstOrNull { it.uid == pick.uid } ?: return false
        val face = engine.faces(inst).getOrNull(pick.face) ?: return false
        return engine.needs(face).enemy
    }

    private fun pickTrackNeed(st: CombatState): TrackNeed {
        val pick = app.pick ?: return TrackNeed.NONE
        val inst = pick.uid?.let { uid -> st.player.present.firstOrNull { it.uid == uid } } ?: return TrackNeed.NONE
        val face = engine.faces(inst).getOrNull(pick.face) ?: return TrackNeed.NONE
        return engine.needs(face).track
    }

    private fun intentText(st: CombatState, item: TrackItem): String = buildString {
        if (item.kind == TrackKind.SCHEDULED) {
            append(esc(item.label))
            return@buildString
        }
        val collapsed = item.collapsed
        when {
            item.forked && collapsed == null ->
                append("A: ${esc(item.label)}${dmg(st, item, 0)} <em>or</em> B: ${esc(item.altLabel ?: "")}${dmg(st, item, 1)}")
            item.forked -> append("Observed: ${esc(if (collapsed == 0) item.label else item.altLabel ?: "")}${dmg(st, item, collapsed ?: 0)}")
            else -> append("${esc(item.label)}${dmg(st, item, 0)}")
        }
        if (item.pressure > 0) append(""" <span class="pressure">Pressure ${item.pressure} · +${25 * item.pressure}%</span>""")
        if (item.fixed) append(""" <span class="fixed">Fixed</span>""")
    }

    private fun dmg(st: CombatState, item: TrackItem, outcome: Int): String {
        val d = engine.intentDamage(st, item, outcome)
        return if (d > 0) """ <b class="dmg">→ $d</b>""" else ""
    }

    private fun trackWho(st: CombatState, t: TrackItem) =
        if (t.kind == TrackKind.SCHEDULED) "You" else t.enemyUid?.let { st.enemy(it) }?.let { content.enemy(it.defId).name } ?: "?"

    private fun enemyName(st: CombatState, uid: Int): String = st.enemy(uid)?.let { content.enemy(it.defId).name } ?: "?"

    private fun cardName(st: CombatState, uid: Int): String =
        st.player.allCards.firstOrNull { it.uid == uid }?.let { content.card(it.defId).name } ?: "?"

    /** The card's type; its faction shows in its color. */
    private fun typeOf(c: CardInst): String = c.face?.type?.label ?: RulesText.typeLine(content.card(c.defId))

    private fun cardText(c: CardInst): String {
        val def = content.card(c.defId)
        val face = c.face
        val granted = c.granted
        return when {
            face != null -> RulesText.face(face)
            granted != null -> "A: ${RulesText.card(def)} B: ${RulesText.face(granted)}"
            else -> RulesText.card(def)
        }
    }

    internal fun statuses(map: Map<StatusType, Int>) =
        map.entries.sortedBy { it.key.ordinal }.joinToString("") { (k, v) ->
            """<span class="status s-${k.name.lowercase()}" title="${esc(Glossary.status(k).text)}">${k.label} $v</span>"""
        }

    /** Keyword explanations as a definition list (the selected card's tooltips). */
    internal fun glossary(entries: List<Glossary.Entry>): String {
        if (entries.isEmpty()) return ""
        return """<dl class="gloss">""" + entries.joinToString("") { """<div><dt>${esc(it.term)}</dt><dd>${esc(it.text)}</dd></div>""" } + "</dl>"
    }

    internal fun tipText(entries: List<Glossary.Entry>) = entries.joinToString("\n") { "${it.term}: ${it.text}" }

    /** The card's keywords, plus what has happened to this copy (Known, Blank, Misprinted...). */
    private fun instGlossary(c: CardInst): List<Glossary.Entry> {
        val base = c.face?.let { Glossary.face(it) } ?: Glossary.card(content.card(c.defId))
        return (base + instStates(c)).distinct()
    }

    private fun instStates(c: CardInst): List<Glossary.Entry> = buildList {
        if (c.known) add(Glossary.KNOWN)
        if (c.blank) add(Glossary.BLANK)
        if (c.bleed) add(Glossary.BLEED)
        if (c.face != null) add(Glossary.MISPRINT)
        if (c.granted != null) add(Glossary.FORK)
        if (c.retainThisTurn) add(Glossary.RETAIN)
    }

    private fun intentTip(item: TrackItem): String =
        tipText(Glossary.intent(item.actions + item.alt.orEmpty(), item.fixed, item.forked, item.pressure > 0))

    internal fun meter(value: Int, max: Int, label: String): String {
        val pct = if (max <= 0) 0 else (100 * value.coerceIn(0, max) / max)
        return """<span class="meter" role="img" aria-label="$label $value of $max"><span style="width:$pct%"></span></span>"""
    }

    private fun dial(n: Int) = """<span class="dial" aria-label="countdown $n">${if (n > 9) "9+" else n}</span>"""

    internal fun pip(c: EnergyColor) = """<span class="pip p-${c.name.lowercase()}" title="${RulesText.colorName(c)}">${c.symbol}</span>"""

    internal fun costPips(cost: Cost): String {
        if (cost.total == 0) return """<span class="pip p-free">0</span>"""
        return pip(EnergyColor.FAITH).repeat(cost.faith) + pip(EnergyColor.COMPUTE).repeat(cost.compute) +
            pip(EnergyColor.FLUX).repeat(cost.flux) + if (cost.generic > 0) """<span class="pip p-generic">${cost.generic}</span>""" else ""
    }

    internal fun signatureLine(sig: Signature, strikeBonus: Int) = when (sig) {
        Signature.STRIKE -> "Strike · deal ${Engine.STRIKE_DAMAGE + strikeBonus}"
        Signature.FORECAST -> "Forecast · Foresee 2"
        Signature.SHIFT -> "Shift · swap 1 card"
    }

    private fun place(enemyId: String) = when (enemyId.substringBefore('.')) {
        "london" -> "London 1843"
        "milan" -> "Milan 1495"
        "saltwaste" -> "Salt Waste 1191"
        "tear" -> "The Tear"
        else -> "Faction squad"
    }

    internal fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    companion object {
        private val BLURBS = mapOf(
            "vowknight" to "Takes Vows that power up a free Strike. The most forgiving operative: high HP and plenty of Block.",
            "oracle" to "Foresees the Future so Calculated cards hit harder, and installs Subroutines that work every turn.",
            "splinter" to "Keeps an Other Hand of 3 cards and Shifts between them. Forks cards and spends Paradox.",
        )

        fun fv(f: Faction) = when (f) {
            Faction.ORDER -> "faith"
            Faction.CONVERGENCE -> "compute"
            Faction.ERRATA -> "flux"
            Faction.NEUTRAL -> "neutral"
            Faction.ERA -> "era"
            Faction.TEAR -> "tear"
        }

        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}
