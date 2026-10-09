package dcbb.core.text

import dcbb.core.model.Bonus
import dcbb.core.model.CardDef
import dcbb.core.model.Cond
import dcbb.core.model.ConstantKind
import dcbb.core.model.ConstantSpec
import dcbb.core.model.Counter
import dcbb.core.model.Effect
import dcbb.core.model.EnemyAction
import dcbb.core.model.Face
import dcbb.core.model.Signature
import dcbb.core.model.StatusType

/**
 * Short explanations of the keywords a card, status or intent uses, for tooltips (docs/04 "Keywords", the glossary
 * in docs/README). Like rules text, they come from the effects, so a tooltip can't name a keyword the card lacks.
 */
object Glossary {

    data class Entry(val term: String, val text: String)

    private fun e(term: String, text: String) = Entry(term, text)

    val ATTUNED = e("Attuned", "A bonus when you pay the card's whole cost in its faction's color.")
    val REMEMBER = e("Remember", "A bonus when your Past holds at least that many cards of the kind named.")
    val LITANY = e("Litany", "A bonus when the card you played just before this one, this turn, was of the kind named.")
    val CALCULATED = e("Calculated", "A bonus when the card is Known: you saw it with Foresee before you drew it.")
    val KNOWN = e("Known", "You saw this card with Foresee before you drew it. Calculated cards are stronger while Known.")
    val FORESEE = e("Foresee", "Look at the top cards of your Future. They become Known. Keep, reorder, or send them to the bottom.")
    val RECALL = e("Recall", "Return cards from your Past to your Present.")
    val DELAY = e("Delay", "Push a Track item later. An enemy intent gains Pressure: +25% potency per Delay. Fixed intents can't be Delayed.")
    val OBSERVE = e("Observe", "Collapse a Forked intent into one of its outcomes now.")
    val SCHEDULE = e("Schedule", "Put the effect on the Track. It resolves after that many rounds, before the enemies act.")
    val SHIFT = e("Shift", "Swap cards between your Present and your Other Hand (with no Other Hand, with the top of your Future).")
    val FORK = e("Fork", "Give a card in your Present a second face for this combat, a Misprint for its type and cost. Choose a face when you play it.")
    val FORK_CARD = e("Fork card", "This card has two faces. Choose A or B when you play it.")
    val MISPRINT = e("Misprint", "Rolls one of its faces when you draw it.")
    val MISPRINTED = e("Misprinted", "The Weave rewrote this card: it plays as a random face for its type and cost.")
    val BLEED = e("Bleed-through", "Adds random cards from another faction to your Present. They cost 1 less this turn, any energy pays them, and they are Erased after play.")
    val PARADOX = e("Paradox", "Strain on the timeline, 0 to 10. At 10 it Unravels: your Present is Misprinted, a Loose End joins the fight, and Paradox drops to 5.")
    val ITERATE = e("Iterate", "Grows each time you have played it this combat.")
    val RETAIN = e("Retain", "Stays in your Present at Dusk instead of going to your Past.")
    val ERASE = e("Erase", "After you play it, it is removed for the rest of the combat.")
    val CONSTANT = e("Constant", "Stays in play in one of your 3 Constant slots. Installing a fourth replaces one.")
    val VOW = e("Vow", "A Constant with a restriction. While you keep it, you get its benefit. Break it and it is Erased, and you lose 4 HP (Penance).")
    val RELIC = e("Relic", "A Constant that improves your Signature Action.")
    val SUBROUTINE = e("Subroutine", "A Constant that acts on its own at Dawn or Dusk.")
    val DAWN_DUSK = e("Dawn / Dusk", "The start and the end of your turn.")
    val BLOCK = e("Block", "Absorbs damage. Yours lasts until your next Dawn.")
    val BLANK = e("Blank", "Its thread was cut: it can't be played until your Past is reshuffled.")
    val STRIKE = e("Strike", "The Vowknight's Signature Action, once per turn: deal 4 to an enemy. Vows and Relics make it stronger.")
    val FORECAST = e("Forecast", "The Oracle's Signature Action, once per turn: Foresee 2.")
    val SHIFT_SIG = e("Shift (Signature)", "The Splinter's Signature Action, once per turn: swap a card in your Present with one in your Other Hand.")

    fun status(s: StatusType): Entry = when (s) {
        StatusType.MIGHT -> e("Might", "+1 damage on every hit, per point.")
        StatusType.PLATE -> e("Plate", "Gain that much Block at the end of each turn. Each hit that gets through Block removes 1.")
        StatusType.WEAK -> e("Weak", "Deals 25% less damage. Drops by 1 each round.")
        StatusType.EXPOSED -> e("Exposed", "Takes 50% more damage. Drops by 1 each round.")
        StatusType.BURN -> e("Burn", "Lose that much HP at the end of the turn, then Burn drops by 1.")
        StatusType.SLOW -> e("Slow", "Its next intent takes 1 round longer.")
        StatusType.GLITCH -> e("Glitch", "Your next cards cost 1 more each, one per point.")
        StatusType.SUBROUTINES -> e("Subroutines", "Installed on itself. Each adds its effect to every later intent, and the Cascade hits harder for each.")
    }

    fun signature(sig: Signature): List<Entry> = when (sig) {
        Signature.STRIKE -> listOf(STRIKE)
        Signature.FORECAST -> listOf(FORECAST, FORESEE, KNOWN)
        Signature.SHIFT -> listOf(SHIFT_SIG)
    }

    /** Everything a card's definition uses, in reading order, without repeats. */
    fun card(def: CardDef): List<Entry> {
        val out = LinkedHashSet<Entry>()
        def.forkFaces?.let { faces ->
            out += FORK_CARD
            faces.forEach { effects(it.effects, out) }
        }
        def.misprintFaces?.let { faces ->
            out += if (def.id.endsWith("~")) MISPRINTED else MISPRINT
            faces.forEach { effects(it.effects, out) }
        }
        if (def.forkFaces == null && def.misprintFaces == null) effects(def.effects, out)
        def.constant?.let { constant(it, out) }
        if (def.retain) out += RETAIN
        if (def.eraseAfterPlay) out += ERASE
        if (def.requiresParadox > 0) out += PARADOX
        return out.toList()
    }

    fun face(face: Face): List<Entry> = LinkedHashSet<Entry>().also { effects(face.effects, it) }.toList()

    /** What an enemy intent's label refers to. */
    fun intent(actions: List<EnemyAction>, fixed: Boolean, forked: Boolean, pressured: Boolean): List<Entry> {
        val out = LinkedHashSet<Entry>()
        if (fixed) out += e("Fixed", "Can't be Delayed or removed.")
        if (forked) out += e("Forked", "Two possible outcomes. It resolves as one of them at random, unless you Observe it first.")
        if (pressured) out += e("Pressure", "+25% potency for each time it was Delayed.")
        for (a in actions) {
            when (a) {
                is EnemyAction.BlankFuture -> out += e("Unpick", "The top of your Future goes Blank (unplayable until reshuffled).")
                is EnemyAction.EraseFuture -> out += e("Unpick", "Cards on top of your Future are Erased for the rest of the combat.")
                is EnemyAction.Disrupt -> out += e("Disrupt", "Your Scheduled effects are pushed later.")
                EnemyAction.SilenceConstants -> out += e("Snip", "Your Constants don't trigger next turn.")
                is EnemyAction.Buff -> out += status(a.status)
                is EnemyAction.Debuff -> out += status(a.status)
                is EnemyAction.AttackPer -> out += status(a.status)
                else -> Unit
            }
        }
        return out.toList()
    }

    private fun constant(c: ConstantSpec, out: MutableSet<Entry>) {
        out += when (c.kind) {
            ConstantKind.VOW -> VOW
            ConstantKind.RELIC -> RELIC
            ConstantKind.SUBROUTINE -> SUBROUTINE
            ConstantKind.NONE -> CONSTANT
        }
        if (c.kind != ConstantKind.NONE) out += CONSTANT
        if (c.dawn.isNotEmpty() || c.dusk.isNotEmpty()) out += DAWN_DUSK
        if (c.strikeDamage > 0 || c.strikeBlock > 0) out += STRIKE
        effects(c.dawn, out)
        effects(c.dusk, out)
        c.onShiftIn?.let {
            out += SHIFT
            if (it.forkShifted) out += FORK
        }
    }

    private fun effects(list: List<Effect>, out: MutableSet<Entry>) {
        for (e in list) {
            when (e) {
                is Effect.Damage -> bonuses(e.amount.bonuses, out)
                is Effect.Block -> {
                    out += BLOCK
                    bonuses(e.amount.bonuses, out)
                }
                is Effect.GainPlate -> out += status(StatusType.PLATE)
                is Effect.GainEnergy -> bonuses(e.amount.bonuses, out)
                is Effect.Foresee -> {
                    out += FORESEE
                    out += KNOWN
                }
                is Effect.Recall -> out += RECALL
                is Effect.Delay -> out += DELAY
                Effect.Observe -> out += OBSERVE
                is Effect.ApplyStatus -> out += status(e.status)
                is Effect.Shift -> out += SHIFT
                Effect.ForkCard -> out += FORK
                is Effect.BleedThrough -> out += BLEED
                is Effect.GainParadox, is Effect.SpendParadox -> out += PARADOX
                Effect.GrantRetain -> out += RETAIN
                is Effect.When -> {
                    cond(e.cond, out)
                    effects(e.then, out)
                    effects(e.otherwise, out)
                }
                is Effect.Schedule -> {
                    out += SCHEDULE
                    effects(e.effects, out)
                }
                else -> Unit
            }
        }
    }

    private fun bonuses(bs: List<Bonus>, out: MutableSet<Entry>) {
        for (b in bs) {
            when (b) {
                is Bonus.If -> cond(b.cond, out)
                is Bonus.Per -> when (b.counter) {
                    Counter.ITERATIONS -> out += ITERATE
                    Counter.PARADOX -> out += PARADOX
                    Counter.KNOWN_IN_PRESENT -> out += KNOWN
                    Counter.CONSTANTS -> out += CONSTANT
                    Counter.PAST_SIZE -> Unit
                }
            }
        }
    }

    private fun cond(c: Cond, out: MutableSet<Entry>) {
        when (c) {
            Cond.Attuned -> out += ATTUNED
            is Cond.Remember -> out += REMEMBER
            is Cond.Litany -> out += LITANY
            Cond.Calculated -> {
                out += CALCULATED
                out += KNOWN
            }
            Cond.VowKept -> out += VOW
            is Cond.ConstantsAtLeast -> out += CONSTANT
            Cond.ShiftedThisTurn -> out += SHIFT
            is Cond.ParadoxAtLeast -> out += PARADOX
            is Cond.TargetFaction, is Cond.HpBelowPct -> Unit
        }
    }
}
