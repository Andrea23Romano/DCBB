package dcbb.core

import dcbb.core.budget.Budget
import dcbb.core.content.Variants
import dcbb.core.model.CardType
import dcbb.core.text.RulesText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BudgetAndTextTest {

    @Test
    fun `every prototype card is within 10 percent of its budget or has a documented exception`() {
        val off = content.authored.map { Budget.report(it) }.filter { !it.acceptable }
        assertTrue(off.isEmpty(), "Out of budget: " + off.joinToString { "${it.cardId} ${"%.2f".format(it.ratio)}" })
        val exceptions = content.authored.filter { it.budgetException != null }
        assertTrue(exceptions.all { !it.budgetException.isNullOrBlank() })
        assertTrue(exceptions.size <= 3, "Exceptions are for a few reviewed cards, not a habit: ${exceptions.map { it.id }}")
    }

    @Test
    fun `every card has an Inscribed version at 1_30 times its target, within 10 percent`() {
        val off = content.authored.mapNotNull { base ->
            val up = content.card(Variants.inscribedId(base.id))
            val ratio = Budget.points(up) / Variants.inscribedTarget(base)
            if (abs(ratio - 1) <= Budget.TOLERANCE + 1e-9 || up.budgetException != null) null else "${up.id} ${"%.2f".format(ratio)}"
        }
        assertTrue(off.isEmpty(), "Inscribed versions out of budget: $off")
        assertEquals("Line Strike+", content.card("order.line_strike+").name)
        assertEquals("Deal 7. Attuned: +2.", RulesText.card(content.card("order.line_strike+")))
    }

    @Test
    fun `Misprinted versions roll from the Misprint table for their type and cost`() {
        val m = content.card(Variants.misprintedId("order.line_strike"))
        assertEquals(content.misprintFaces(CardType.ATTACK, 1), m.misprintFaces)
        assertTrue(Variants.misprintedId("order.vow_of_the_sword") !in content.cards, "Constants have no Misprint table")
    }

    @Test
    fun `budget points match the values printed in docs 03 and 08`() {
        // Pts column of docs/03 (one decimal there) and the docs/08 worked example.
        val expected = mapOf(
            "order.line_strike" to 12.0,
            "order.vow_of_the_sword" to 11.2,
            "order.remembered_blow" to 24.0,
            "order.hold_fast" to 12.6,
            "order.sundial_cut" to 12.4,
            "order.crusaders_charge" to 24.8,
            "order.litany_of_steel" to 13.2,
            "order.gnomon_blade" to 25.92,
            "order.oathkeepers_stand" to 27.6,
            "order.smite_the_heretic" to 28.0,
            "order.recite_the_canon" to 13.2,
            "order.vow_of_silence" to 16.0,
            "order.verdict_of_the_line" to 49.0,
            "conv.probability_lance" to 12.8,
            "conv.watchdog" to 11.52,
            "conv.recursive_strike" to 12.0,
            "conv.predictive_shield" to 11.6,
            "conv.scatter_protocol" to 11.4,
            "conv.deterministic_model" to 24.0,
            "conv.lance_exe" to 27.36,
            "conv.rollback" to 13.0,
            "conv.hold_pattern" to 13.6,
            "conv.optimal_path" to 14.0,
            "conv.monte_carlo" to 27.3,
            "conv.proof_of_meridian" to 48.0,
            "errata.split_the_moment" to 12.0,
            "errata.two_places_at_once" to 12.0,
            "errata.borrowed_self" to 6.0,
            "errata.fray_lash" to 12.0,
            "errata.caret_strike" to 11.6,
            "errata.crosstalk" to 14.0,
            "errata.misprinted_edict" to 26.33,
            "errata.paradox_engine" to 14.4,
            "errata.elsewhen_guard" to 13.6,
            "errata.unwrite" to 14.0,
            "errata.double_exposure" to 32.4,
            "errata.thousand_doors" to 45.6,
            "neutral.hold_the_hour" to 12.0,
            "neutral.stitch_in_time" to 12.96,
            "neutral.observe" to 6.0,
            "london.gaslight_ambush" to 12.0,
            "london.punchcard_program" to 13.6,
            "milan.vitruvian_guard" to 12.0,
            "milan.codex_sketch" to 12.0,
            "saltwaste.mirage_step" to 6.4,
            "saltwaste.salt_ward" to 25.6,
        )
        for ((id, pts) in expected) {
            val actual = Budget.points(content.card(id))
            assertTrue(abs(actual - pts) < 0.01, "$id: expected $pts, got $actual")
        }
    }

    @Test
    fun `budget targets follow cost and rarity`() {
        assertEquals(6.0, Budget.target(content.card("neutral.observe")))
        assertEquals(24.0, Budget.target(content.card("order.remembered_blow")))
        assertEquals(26.4, Budget.target(content.card("order.gnomon_blade")), 1e-9)
        assertEquals(30.0, Budget.target(content.card("conv.monte_carlo")), 1e-9)
        assertEquals(50.4, Budget.target(content.card("conv.proof_of_meridian")), 1e-9)
    }

    @Test
    fun `misprint table faces stay within 25 percent of their slot's target`() {
        for ((key, faces) in content.misprintTables) {
            val (type, cost) = key
            val target = Budget.baseBudget(cost)
            for (f in faces) {
                val ratio = Budget.face(f) / target
                assertTrue(ratio in 0.75..1.25 + 1e-9, "$type/$cost face ${RulesText.face(f)} is at $ratio")
                assertEquals(if (type == CardType.ATTACK) CardType.ATTACK else CardType.SKILL, f.type)
            }
        }
    }

    @Test
    fun `rules text renders like the card tables in docs 03`() {
        val expected = mapOf(
            "order.line_strike" to "Deal 5. Attuned: +2.",
            "order.shield_of_the_line" to "Gain 4 Block. Attuned: +2 Block.",
            "order.remembered_blow" to "Deal 9. Remember 3 Attacks: +5.",
            "order.hold_fast" to "Gain 4 Block. Litany (Skill): gain Plate 1.",
            "order.sundial_cut" to "Deal 5. Remember 2 Skills: apply Weak 1.",
            "order.crusaders_charge" to "Deal 10. Litany (Attack): +4.",
            "order.gnomon_blade" to "Your Strike deals +3 and gives 2 Block.",
            "order.oathkeepers_stand" to "Gain 9 Block. If you have an unbroken Vow: gain Plate 2.",
            "order.smite_the_heretic" to "Deal 10. Double damage against Errata and Tear enemies.",
            "order.recite_the_canon" to "Recall 1. Remember 5: Recall 2 instead. Retain.",
            "order.vow_of_silence" to "Vow: never Borrow or Delay. While kept: at Dawn, gain 1 Faith.",
            "order.verdict_of_the_line" to "Deal 18, +1 per card in your Past (max +14). Erase.",
            "conv.probability_lance" to "Deal 4. Calculated: +4.",
            "conv.watchdog" to "Dusk: gain 2 Block.",
            "conv.recursive_strike" to "Deal 3. Iterate +2.",
            "conv.scatter_protocol" to "Deal 3 to all enemies. Calculated: Foresee 2.",
            "conv.deterministic_model" to "Foresee 3. Draw 2. Calculated: gain 1 Compute.",
            "conv.lance_exe" to "Dusk: deal 6 to the weakest enemy.",
            "conv.rollback" to "Restore the HP you lost during the last enemy phase (max 12). Erase.",
            "conv.hold_pattern" to "Delay 1. Calculated: draw 1.",
            "conv.optimal_path" to "Foresee 3. Put one of them into your Present.",
            "conv.proof_of_meridian" to "Dawn: gain 1 Compute per Known card in your Present (max 3). Dusk: Foresee 1.",
            "errata.split_the_moment" to "Fork a card in your Present. Draw 1.",
            "errata.two_places_at_once" to "A: Deal 5. B: Gain 4 Block.",
            "errata.borrowed_self" to "Shift 2. Gain 1 Paradox.",
            "errata.fray_lash" to "Deal 3, +1 per Paradox (max +6).",
            "errata.caret_strike" to "Deal 4. If you Shifted this turn: +3.",
            "errata.crosstalk" to "Bleed-through 2. Gain 1 Paradox.",
            "errata.elsewhen_guard" to "Gain 4 Block. Shift 1.",
            "errata.unwrite" to "Spend 2 Paradox. Remove an enemy intent from the Track (not Fixed). Draw 1.",
            "errata.thousand_doors" to "Whenever you Shift a card into your Present, Fork it. " +
                "The first card you Shift in each turn costs 1 less.",
            "neutral.hold_the_hour" to "Delay 1. Foresee 1.",
            "neutral.stitch_in_time" to "Gain 3 Block. Schedule 2: gain 3 Block.",
            "london.gaslight_ambush" to "Deal 4 to all enemies.",
            "london.punchcard_program" to "Foresee 2. Schedule 2: draw 2.",
            "milan.vitruvian_guard" to "Gain 4 Block, +1 per Constant you have (max +3).",
            "milan.codex_sketch" to "Choose a card in your Present: it gains Retain and costs 1 less this turn.",
            "saltwaste.salt_ward" to "Gain 9 Block. Observe.",
        )
        for ((id, text) in expected) assertEquals(text, RulesText.card(content.card(id)), id)
    }

    @Test
    fun `tooltips name exactly the keywords a card uses`() {
        fun terms(id: String) = dcbb.core.text.Glossary.card(content.card(id)).map { it.term }
        assertEquals(listOf("Attuned"), terms("order.line_strike"))
        assertEquals(listOf("Vow", "Constant", "Strike"), terms("order.vow_of_the_sword"))
        assertEquals(listOf("Delay", "Calculated", "Known"), terms("conv.hold_pattern"))
        assertTrue("Iterate" in terms("conv.recursive_strike"))
        assertTrue("Fork card" in terms("errata.two_places_at_once"))
        assertTrue("Misprinted" in terms("order.line_strike~"))
        assertTrue("Bleed-through" in terms("errata.crosstalk") && "Paradox" in terms("errata.crosstalk"))
        // Every authored card's tooltips come from its own effects; none is empty for a keyworded card.
        for (def in content.authored) {
            val text = RulesText.typeLine(def) + " " + RulesText.card(def)
            for (entry in dcbb.core.text.Glossary.card(def)) {
                val stem = entry.term.substringBefore(" ").substringBefore("-").removeSuffix("ed").lowercase().take(5)
                val inText = stem in text.lowercase() || entry.term in setOf("Known", "Constant", "Block", "Dawn / Dusk", "Strike", "Fork card", "Misprint", "Shift")
                assertTrue(inText, "${def.id}: tooltip ${entry.term} but the text says: $text")
            }
        }
    }

    @Test
    fun `rules text respects the 140 character cap`() {
        for (def in content.cards.values) {
            val text = RulesText.typeLine(def) + " " + RulesText.card(def)
            assertTrue(text.length <= 140, "${def.id} renders ${text.length} characters: $text")
        }
    }
}
