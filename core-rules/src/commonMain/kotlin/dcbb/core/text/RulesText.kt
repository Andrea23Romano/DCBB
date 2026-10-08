package dcbb.core.text

import dcbb.core.model.Amount
import dcbb.core.model.Bonus
import dcbb.core.model.CardDef
import dcbb.core.model.CardType
import dcbb.core.model.Cond
import dcbb.core.model.ConstantKind
import dcbb.core.model.ConstantSpec
import dcbb.core.model.Counter
import dcbb.core.model.Effect
import dcbb.core.model.EnemyAction
import dcbb.core.model.EnergyColor
import dcbb.core.model.Face
import dcbb.core.model.Kind
import dcbb.core.model.Tgt

/**
 * Renders rules text from effects (docs/08 golden rule: rules text always comes from the engine, never from a writer
 * or a model). English only in Phase 1; Phase 2 moves the templates into localized string tables.
 */
object RulesText {

    fun card(def: CardDef): String {
        val body = when {
            def.forkFaces != null -> def.forkFaces.mapIndexed { i, f -> "${letter(i)}: ${face(f)}" }.joinToString(" ")
            def.misprintFaces != null ->
                "Misprint. " + def.misprintFaces.mapIndexed { i, f -> "${letter(i)}: ${face(f)}" }.joinToString(" ")
            def.constant != null -> constant(def.constant)
            else -> effects(def.effects)
        }
        return buildString {
            append(body)
            if (def.retain) append(" Retain.")
            if (def.eraseAfterPlay) append(" Erase.")
            if (def.unplayable) append(" Unplayable.")
        }.trim()
    }

    fun typeLine(def: CardDef): String = when {
        def.forkFaces != null -> "Fork (${def.forkFaces.joinToString(" / ") { it.type.label }})"
        def.type == CardType.CONSTANT && def.constant != null && def.constant.kind != ConstantKind.NONE ->
            "Constant (${def.constant.kind.label})"
        else -> def.type.label
    }

    fun face(f: Face): String = effects(f.effects)

    private fun letter(i: Int) = ('A' + i).toString()

    fun constant(spec: ConstantSpec): String {
        val vow = spec.vow
        val benefits = buildList {
            if (spec.strikeDamage > 0 && spec.strikeBlock > 0) {
                add("your Strike deals +${spec.strikeDamage} and gives ${spec.strikeBlock} Block")
            } else if (spec.strikeDamage > 0) {
                add("your Strike deals +${spec.strikeDamage}")
            } else if (spec.strikeBlock > 0) {
                add("your Strike gives ${spec.strikeBlock} Block")
            }
            if (spec.dawn.isNotEmpty()) add((if (vow != null) "at Dawn, " else "Dawn: ") + clauses(spec.dawn))
            if (spec.dusk.isNotEmpty()) add((if (vow != null) "at Dusk, " else "Dusk: ") + clauses(spec.dusk))
            spec.onShiftIn?.let { rule ->
                if (rule.forkShifted) add("whenever you Shift a card into your Present, Fork it")
                if (rule.firstShiftDiscount > 0) {
                    add("the first card you Shift in each turn costs ${rule.firstShiftDiscount} less")
                }
            }
        }
        if (vow == null) return benefits.joinToString(". ") { cap(it) } + "."
        return "Vow: ${vow.text}. While kept: ${benefits.joinToString(". ")}."
    }

    /** Full sentences: "Deal 5. Attuned: +2." */
    fun effects(effects: List<Effect>): String {
        val s = clauses(effects)
        return if (s.isEmpty()) "" else cap(s) + "."
    }

    /** Clauses joined into sentences, without the final period and with the first letter as rendered. */
    fun clauses(effects: List<Effect>): String {
        val parts = mutableListOf<String>()
        var i = 0
        while (i < effects.size) {
            val e = effects[i]
            val next = effects.getOrNull(i + 1)
            if (e == Effect.GrantRetain && next is Effect.ReduceCostThisTurn) {
                parts += "choose a card in your Present: it gains Retain and costs ${next.n} less this turn"
                i += 2
                continue
            }
            parts += clause(e)
            i++
        }
        return parts.mapIndexed { i, part -> if (i == 0) part else cap(part) }.joinToString(". ")
    }

    fun clause(e: Effect): String = when (e) {
        is Effect.Damage -> damage(e)
        is Effect.Block -> "gain ${e.amount.base} Block" + bonuses(e.amount, " Block")
        is Effect.GainPlate -> "gain Plate ${e.n}"
        is Effect.Draw -> "draw ${e.n}"
        is Effect.GainEnergy -> energy(e)
        is Effect.Foresee -> "Foresee ${e.n}" + if (e.tutor) ". Put one of them into your Present" else ""
        is Effect.Recall -> "Recall ${e.n}"
        is Effect.Delay -> "Delay ${e.n}"
        Effect.RemoveIntent -> "remove an enemy intent from the Track (not Fixed)"
        Effect.Observe -> "Observe"
        is Effect.ApplyStatus -> "apply ${e.status.label} ${e.n}" + target(e.target)
        is Effect.RestoreLastPhaseLoss -> "restore the HP you lost during the last enemy phase (max ${e.max})"
        is Effect.Shift -> "Shift ${e.n}"
        Effect.ForkCard -> "Fork a card in your Present"
        is Effect.BleedThrough -> "Bleed-through ${e.n}"
        is Effect.GainParadox -> "gain ${e.n} Paradox"
        is Effect.SpendParadox -> "spend ${e.n} Paradox"
        Effect.GrantRetain -> "choose a card in your Present: it gains Retain"
        is Effect.ReduceCostThisTurn -> "choose a card in your Present: it costs ${e.n} less this turn"
        is Effect.LoseHp -> "lose ${e.n} HP"
        is Effect.When -> if (e.otherwise.isEmpty()) {
            "${cond(e.cond)}: ${clauses(e.then)}"
        } else {
            "${clauses(e.otherwise)}. ${cond(e.cond)}: ${clauses(e.then)} instead"
        }

        is Effect.Schedule -> "Schedule ${e.countdown}: ${clauses(e.effects)}"
    }

    private fun damage(e: Effect.Damage): String {
        val hits = when (e.hits) {
            1 -> ""
            2 -> " twice"
            else -> " ${e.hits} times"
        }
        val doubles = e.amount.bonuses.singleOrNull()
            ?.let { it as? Bonus.If }
            ?.takeIf { it.plus == e.amount.base && it.cond is Cond.TargetFaction }
        if (doubles != null) {
            return "deal ${e.amount.base}$hits${target(e.target)}. Double damage ${cond(doubles.cond).replaceFirstChar { it.lowercase() }}"
        }
        return "deal ${e.amount.base}${target(e.target)}$hits" + bonuses(e.amount, "")
    }

    private fun energy(e: Effect.GainEnergy): String {
        val color = colorName(e.color)
        val per = e.amount.bonuses.singleOrNull() as? Bonus.Per
        if (e.amount.base == 0 && per != null) {
            return "gain ${per.each} $color per ${per.counter.label} (max ${per.max})"
        }
        return "gain ${e.amount.base} $color" + bonuses(e.amount, " $color")
    }

    private fun bonuses(a: Amount, unit: String): String = a.bonuses.joinToString("") { b ->
        when (b) {
            is Bonus.If -> ". ${cond(b.cond)}: +${b.plus}$unit"
            is Bonus.Per -> if (b.counter == Counter.ITERATIONS) {
                ". Iterate +${b.each}"
            } else {
                ", +${b.each} per ${b.counter.label} (max +${b.max})"
            }
        }
    }

    private fun target(t: Tgt): String = when (t) {
        Tgt.CHOSEN_ENEMY -> ""
        Tgt.RANDOM_ENEMY -> " to a random enemy"
        Tgt.WEAKEST_ENEMY -> " to the weakest enemy"
        Tgt.ALL_ENEMIES -> " to all enemies"
    }

    fun cond(c: Cond): String = when (c) {
        Cond.Attuned -> "Attuned"
        is Cond.Remember -> if (c.kind == Kind.ANY) "Remember ${c.n}" else "Remember ${c.n} ${c.kind.label}"
        is Cond.Litany -> "Litany (${if (c.kind == Kind.ANY) "any" else c.kind.label.removeSuffix("s")})"
        Cond.Calculated -> "Calculated"
        Cond.VowKept -> "If you have an unbroken Vow"
        is Cond.TargetFaction -> "Against " + c.factions.map { it.label }.sorted().joinToString(" and ") + " enemies"
        is Cond.HpBelowPct -> "If your HP is below ${c.pct}%"
        is Cond.ConstantsAtLeast -> "If you have ${c.n}+ Constants"
        Cond.ShiftedThisTurn -> "If you Shifted this turn"
        is Cond.ParadoxAtLeast -> "Paradox ${c.n}+"
    }

    fun colorName(c: EnergyColor): String = c.name.lowercase().replaceFirstChar { it.uppercase() }

    /** Intent labels for the Track: "Attack 3 + Weak 1", "Unpick: Blank the top card of your Future". */
    fun intent(actions: List<EnemyAction>): String = actions.joinToString(" + ") { a ->
        when (a) {
            is EnemyAction.Attack -> if (a.hits > 1) "Attack ${a.damage}×${a.hits}" else "Attack ${a.damage}"
            is EnemyAction.AttackPer -> "Attack ${a.per} × ${a.status.label}"
            is EnemyAction.Guard -> "Guard ${a.block}"
            is EnemyAction.Buff -> "${a.status.label} ${a.n}"
            is EnemyAction.Debuff -> "${a.status.label} ${a.n}"
            is EnemyAction.BlankFuture ->
                if (a.n == 1) "Unpick: Blank the top card of your Future" else "Unpick: Blank the top ${a.n} cards of your Future"
            is EnemyAction.EraseFuture ->
                if (a.n == 1) "Unpick: Erase the top card of your Future" else "Unpick: Erase the top ${a.n} cards of your Future"
            is EnemyAction.Disrupt -> "Disrupt ${a.n}"
            EnemyAction.SilenceConstants -> "Snip: your Constants don't trigger next turn"
        }
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
}
