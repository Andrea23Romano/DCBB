package dcbb.core.content

import dcbb.core.model.Amount
import dcbb.core.model.Bonus
import dcbb.core.model.CardDef
import dcbb.core.model.CardType
import dcbb.core.model.Cond
import dcbb.core.model.ConstantKind
import dcbb.core.model.ConstantSpec
import dcbb.core.model.Cost
import dcbb.core.model.Counter
import dcbb.core.model.DoubleAi
import dcbb.core.model.Effect
import dcbb.core.model.EnemyAction
import dcbb.core.model.EnemyDef
import dcbb.core.model.Encounter
import dcbb.core.model.EnergyColor
import dcbb.core.model.Face
import dcbb.core.model.Faction
import dcbb.core.model.IntentSpec
import dcbb.core.model.Kind
import dcbb.core.model.LatticeAi
import dcbb.core.model.LatticeSub
import dcbb.core.model.MultiSlotAi
import dcbb.core.model.OperativeDef
import dcbb.core.model.PredictiveAi
import dcbb.core.model.Rarity
import dcbb.core.model.RotationAi
import dcbb.core.model.ShiftInRule
import dcbb.core.model.Signature
import dcbb.core.model.StatusType
import dcbb.core.model.Tgt
import dcbb.core.model.VowRule
import dcbb.core.text.RulesText

/**
 * Phase 1 prototype content: every sample card in docs/03, the Misprint tables from docs/08, the era and faction
 * enemies from docs/03 "Enemy Rosters" that the engine supports, and the three MVP operatives.
 * Numbers are prototype starting points; the simulation report is how they get tuned.
 */
object Prototype {

    val content: Content by lazy {
        val authored = cards()
        val tables = misprintTables()
        val misprintFaces = { type: CardType, cost: Int ->
            tables[(if (type == CardType.ATTACK) CardType.ATTACK else CardType.SKILL) to cost.coerceIn(0, 3)]
        }
        Content(
            cards = authored,
            enemies = enemies(),
            operatives = operatives(),
            encounters = encounters(),
            misprintTables = tables,
            decks = decks(),
            variants = authored.map { Variants.inscribe(it, inscribedTweaks[it.id]) } +
                authored.mapNotNull { Variants.misprint(it, misprintFaces) },
        )
    }

    /**
     * Authored Inscribed versions, for cards whose generated upgrade misses ×1.30 of budget or reads badly
     * (docs/08 "Budget Targets"). Each one adjusts the generated upgrade.
     */
    private val inscribedTweaks: Map<String, (CardDef) -> CardDef> = mapOf(
        "conv.optimal_path" to { it.copy(retain = true) },
        "conv.proof_of_meridian" to { it.copy(constant = it.constant?.copy(dusk = listOf(Effect.Foresee(2), Effect.Draw(1)))) },
        "conv.rollback" to { it.copy(eraseAfterPlay = false) },
        "conv.watchdog" to { it.copy(constant = it.constant?.copy(dusk = listOf(block(2), Effect.Foresee(1)))) },
        "errata.paradox_engine" to {
            it.copy(budgetException = "Inscribed: losing one Paradox per Dawn is the natural upgrade, 2% over the window")
        },
        "errata.split_the_moment" to { it.copy(effects = listOf(Effect.ForkCard, Effect.Draw(1), Effect.Shift(1))) },
        "errata.thousand_doors" to {
            it.copy(constant = it.constant?.copy(onShiftIn = ShiftInRule(forkShifted = true, firstShiftDiscount = 2)))
        },
        "milan.codex_sketch" to { it.copy(effects = listOf(Effect.GrantRetain, Effect.ReduceCostThisTurn(1), Effect.Draw(1))) },
        "neutral.hold_the_hour" to { it.copy(effects = listOf(Effect.Delay(1), Effect.Foresee(3))) },
        "order.vow_of_silence" to {
            it.copy(constant = it.constant?.copy(dawn = listOf(Effect.GainEnergy(EnergyColor.FAITH, Amount(1)), Effect.Foresee(1))))
        },
        "saltwaste.mirage_step" to { it.copy(effects = listOf(block(1), Effect.Shift(1), Effect.Foresee(1))) },
    )

    // ---- effect shorthands ------------------------------------------------------------------------------------

    private fun dmg(n: Int, vararg bonuses: Bonus) = Effect.Damage(Amount(n, *bonuses))
    private fun dmgTo(n: Int, target: Tgt, hits: Int = 1) = Effect.Damage(Amount(n), target, hits)
    private fun hits(n: Int, times: Int) = Effect.Damage(Amount(n), Tgt.CHOSEN_ENEMY, times)
    private fun block(n: Int, vararg bonuses: Bonus) = Effect.Block(Amount(n, *bonuses))
    private fun attuned(plus: Int) = Bonus.If(Cond.Attuned, plus)
    private fun whenever(cond: Cond, vararg then: Effect) = Effect.When(cond, then.toList())
    private fun face(type: CardType, vararg effects: Effect) = Face(type, effects.toList())

    private fun card(
        id: String,
        name: String,
        faction: Faction,
        type: CardType,
        rarity: Rarity,
        cost: String,
        effects: List<Effect> = emptyList(),
        constant: ConstantSpec? = null,
        forkFaces: List<Face>? = null,
        misprintFaces: List<Face>? = null,
        retain: Boolean = false,
        erase: Boolean = false,
        requiresParadox: Int = 0,
        exception: String? = null,
    ) = CardDef(
        id = id,
        name = name,
        faction = faction,
        type = type,
        rarity = rarity,
        cost = Cost.parse(cost),
        effects = effects,
        constant = constant,
        forkFaces = forkFaces,
        misprintFaces = misprintFaces,
        retain = retain,
        eraseAfterPlay = erase,
        requiresParadox = requiresParadox,
        budgetException = exception,
    )

    // ---- cards ------------------------------------------------------------------------------------------------

    private fun cards(): List<CardDef> = order() + convergence() + errata() + neutralAndEra()

    private fun order(): List<CardDef> {
        val o = Faction.ORDER
        return listOf(
            card("order.line_strike", "Line Strike", o, CardType.ATTACK, Rarity.STARTER, "1", listOf(dmg(5, attuned(2)))),
            card("order.shield_of_the_line", "Shield of the Line", o, CardType.SKILL, Rarity.STARTER, "1", listOf(block(4, attuned(2)))),
            card(
                "order.vow_of_the_sword", "Vow of the Sword", o, CardType.CONSTANT, Rarity.STARTER, "1F",
                constant = ConstantSpec(ConstantKind.VOW, strikeDamage = 4, vow = VowRule.MAX_ONE_SKILL_PER_TURN),
            ),
            card(
                "order.remembered_blow", "Remembered Blow", o, CardType.ATTACK, Rarity.STARTER, "1F+1",
                listOf(dmg(9, Bonus.If(Cond.Remember(Kind.ATTACK, 3), 5))),
            ),
            card(
                "order.hold_fast", "Hold Fast", o, CardType.SKILL, Rarity.COMMON, "1F",
                listOf(block(4), whenever(Cond.Litany(Kind.SKILL), Effect.GainPlate(1))),
            ),
            card(
                "order.sundial_cut", "Sundial Cut", o, CardType.ATTACK, Rarity.COMMON, "1F",
                listOf(dmg(5), whenever(Cond.Remember(Kind.SKILL, 2), Effect.ApplyStatus(StatusType.WEAK, 1))),
            ),
            card(
                "order.crusaders_charge", "Crusader's Charge", o, CardType.ATTACK, Rarity.COMMON, "2F",
                listOf(dmg(10, Bonus.If(Cond.Litany(Kind.ATTACK), 4))),
            ),
            card(
                "order.litany_of_steel", "Litany of Steel", o, CardType.SKILL, Rarity.UNCOMMON, "1",
                listOf(block(4), whenever(Cond.Litany(Kind.ATTACK), Effect.Draw(1))),
            ),
            card(
                "order.gnomon_blade", "Gnomon Blade", o, CardType.CONSTANT, Rarity.UNCOMMON, "1F+1",
                constant = ConstantSpec(ConstantKind.RELIC, strikeDamage = 3, strikeBlock = 2),
            ),
            card(
                "order.oathkeepers_stand", "Oathkeeper's Stand", o, CardType.SKILL, Rarity.UNCOMMON, "2F",
                listOf(block(9), whenever(Cond.VowKept, Effect.GainPlate(2))),
            ),
            card(
                "order.smite_the_heretic", "Smite the Heretic", o, CardType.ATTACK, Rarity.UNCOMMON, "1F+1",
                listOf(dmg(10, Bonus.If(Cond.TargetFaction(setOf(Faction.ERRATA, Faction.TEAR)), 10))),
            ),
            card(
                "order.recite_the_canon", "Recite the Canon", o, CardType.SKILL, Rarity.UNCOMMON, "1F",
                listOf(Effect.When(Cond.Remember(Kind.ANY, 5), then = listOf(Effect.Recall(2)), otherwise = listOf(Effect.Recall(1)))),
                retain = true,
            ),
            card(
                "order.vow_of_silence", "Vow of Silence", o, CardType.CONSTANT, Rarity.RARE, "1F",
                constant = ConstantSpec(
                    ConstantKind.VOW,
                    dawn = listOf(Effect.GainEnergy(EnergyColor.FAITH, Amount(1))),
                    vow = VowRule.NO_BORROW_OR_DELAY,
                ),
            ),
            card(
                "order.verdict_of_the_line", "Verdict of the Line", o, CardType.ATTACK, Rarity.LEGENDARY, "2F+1",
                listOf(dmg(18, Bonus.Per(Counter.PAST_SIZE, 1, 14))),
                erase = true,
            ),
        )
    }

    private fun convergence(): List<CardDef> {
        val c = Faction.CONVERGENCE
        return listOf(
            card("conv.pulse", "Pulse", c, CardType.ATTACK, Rarity.STARTER, "1", listOf(dmg(5, attuned(2)))),
            card("conv.firewall", "Firewall", c, CardType.SKILL, Rarity.STARTER, "1", listOf(block(4, attuned(2)))),
            card(
                "conv.probability_lance", "Probability Lance", c, CardType.ATTACK, Rarity.STARTER, "1C",
                listOf(dmg(4, Bonus.If(Cond.Calculated, 4))),
            ),
            card(
                "conv.watchdog", "Watchdog.exe", c, CardType.CONSTANT, Rarity.STARTER, "1C",
                constant = ConstantSpec(ConstantKind.SUBROUTINE, dusk = listOf(block(2))),
            ),
            card(
                "conv.recursive_strike", "Recursive Strike", c, CardType.ATTACK, Rarity.COMMON, "1",
                listOf(dmg(3, Bonus.Per(Counter.ITERATIONS, 2, 40))),
            ),
            card(
                "conv.predictive_shield", "Predictive Shield", c, CardType.SKILL, Rarity.COMMON, "1C",
                listOf(block(4), Effect.Foresee(1)),
            ),
            card(
                "conv.scatter_protocol", "Scatter Protocol", c, CardType.ATTACK, Rarity.COMMON, "1C",
                listOf(dmgTo(3, Tgt.ALL_ENEMIES), whenever(Cond.Calculated, Effect.Foresee(2))),
            ),
            card(
                "conv.deterministic_model", "Deterministic Model", c, CardType.SKILL, Rarity.UNCOMMON, "1C+1",
                listOf(
                    Effect.Foresee(3),
                    Effect.Draw(2),
                    whenever(Cond.Calculated, Effect.GainEnergy(EnergyColor.COMPUTE, Amount(1))),
                ),
            ),
            card(
                "conv.lance_exe", "Lance.exe", c, CardType.CONSTANT, Rarity.UNCOMMON, "2C",
                constant = ConstantSpec(ConstantKind.SUBROUTINE, dusk = listOf(dmgTo(6, Tgt.WEAKEST_ENEMY))),
            ),
            card(
                "conv.rollback", "Rollback", c, CardType.SKILL, Rarity.UNCOMMON, "1C",
                listOf(Effect.RestoreLastPhaseLoss(12)),
                erase = true,
            ),
            card(
                "conv.hold_pattern", "Hold Pattern", c, CardType.SKILL, Rarity.UNCOMMON, "1C",
                listOf(Effect.Delay(1), whenever(Cond.Calculated, Effect.Draw(1))),
            ),
            card(
                "conv.optimal_path", "Optimal Path", c, CardType.SKILL, Rarity.RARE, "1C",
                listOf(Effect.Foresee(3, tutor = true)),
            ),
            card(
                "conv.monte_carlo", "Monte Carlo", c, CardType.ATTACK, Rarity.RARE, "1C+1",
                listOf(
                    Effect.When(
                        Cond.Calculated,
                        then = listOf(dmgTo(3, Tgt.WEAKEST_ENEMY, hits = 5)),
                        otherwise = listOf(dmgTo(3, Tgt.RANDOM_ENEMY, hits = 5)),
                    ),
                ),
            ),
            card(
                "conv.proof_of_meridian", "Proof of MERIDIAN", c, CardType.CONSTANT, Rarity.LEGENDARY, "2C+1",
                constant = ConstantSpec(
                    ConstantKind.SUBROUTINE,
                    dawn = listOf(Effect.GainEnergy(EnergyColor.COMPUTE, Amount(0, Bonus.Per(Counter.KNOWN_IN_PRESENT, 1, 3)))),
                    dusk = listOf(Effect.Foresee(1)),
                ),
            ),
        )
    }

    private fun errata(): List<CardDef> {
        val x = Faction.ERRATA
        return listOf(
            card("errata.splinter_cut", "Splinter Cut", x, CardType.ATTACK, Rarity.STARTER, "1", listOf(dmg(5, attuned(2)))),
            card("errata.double_guard", "Double Guard", x, CardType.SKILL, Rarity.STARTER, "1", listOf(block(4, attuned(2)))),
            card(
                "errata.split_the_moment", "Split the Moment", x, CardType.SKILL, Rarity.STARTER, "1X",
                listOf(Effect.ForkCard, Effect.Draw(1)),
            ),
            card(
                "errata.two_places_at_once", "Two Places at Once", x, CardType.ATTACK, Rarity.STARTER, "1",
                forkFaces = listOf(face(CardType.ATTACK, dmg(5)), face(CardType.SKILL, block(4))),
            ),
            card(
                "errata.borrowed_self", "Borrowed Self", x, CardType.SKILL, Rarity.COMMON, "0",
                listOf(Effect.Shift(2), Effect.GainParadox(1)),
            ),
            card(
                "errata.fray_lash", "Fray Lash", x, CardType.ATTACK, Rarity.COMMON, "1X",
                listOf(dmg(3, Bonus.Per(Counter.PARADOX, 1, 6))),
            ),
            card(
                "errata.caret_strike", "Caret Strike", x, CardType.ATTACK, Rarity.COMMON, "1",
                listOf(dmg(4, Bonus.If(Cond.ShiftedThisTurn, 3))),
            ),
            card(
                "errata.crosstalk", "Crosstalk", x, CardType.SKILL, Rarity.UNCOMMON, "1X",
                listOf(Effect.BleedThrough(2), Effect.GainParadox(1)),
            ),
            card(
                "errata.misprinted_edict", "Misprinted Edict", x, CardType.ATTACK, Rarity.UNCOMMON, "1X+1",
                misprintFaces = listOf(
                    face(CardType.ATTACK, dmg(14)),
                    face(CardType.ATTACK, dmgTo(7, Tgt.ALL_ENEMIES)),
                    face(CardType.SKILL, block(10), Effect.Draw(1)),
                ),
            ),
            card(
                "errata.paradox_engine", "Paradox Engine", x, CardType.CONSTANT, Rarity.UNCOMMON, "1X",
                constant = ConstantSpec(
                    ConstantKind.NONE,
                    dawn = listOf(Effect.GainEnergy(EnergyColor.FLUX, Amount(1)), Effect.GainParadox(2)),
                ),
            ),
            card(
                "errata.elsewhen_guard", "Elsewhen Guard", x, CardType.SKILL, Rarity.UNCOMMON, "1X",
                listOf(block(4), Effect.Shift(1)),
            ),
            card(
                "errata.unwrite", "Unwrite", x, CardType.SKILL, Rarity.RARE, "1X",
                listOf(Effect.SpendParadox(2), Effect.RemoveIntent, Effect.Draw(1)),
                requiresParadox = 2,
            ),
            card(
                "errata.double_exposure", "Double Exposure", x, CardType.ATTACK, Rarity.RARE, "1X+1",
                forkFaces = listOf(face(CardType.ATTACK, hits(7, 2)), face(CardType.SKILL, block(11), Effect.Shift(1))),
            ),
            card(
                "errata.thousand_doors", "The Thousand Doors", x, CardType.CONSTANT, Rarity.LEGENDARY, "2X+1",
                constant = ConstantSpec(ConstantKind.NONE, onShiftIn = ShiftInRule(forkShifted = true, firstShiftDiscount = 1)),
            ),
        )
    }

    private fun neutralAndEra(): List<CardDef> {
        val n = Faction.NEUTRAL
        val era = Faction.ERA
        return listOf(
            card("neutral.hold_the_hour", "Hold the Hour", n, CardType.SKILL, Rarity.COMMON, "1", listOf(Effect.Delay(1), Effect.Foresee(1))),
            card(
                "neutral.stitch_in_time", "Stitch in Time", n, CardType.SKILL, Rarity.COMMON, "1",
                listOf(block(3), Effect.Schedule(2, listOf(block(3)))),
            ),
            card("neutral.observe", "Observe", n, CardType.SKILL, Rarity.COMMON, "0", listOf(Effect.Observe, Effect.Foresee(1))),
            card("london.gaslight_ambush", "Gaslight Ambush", era, CardType.ATTACK, Rarity.COMMON, "1", listOf(dmgTo(4, Tgt.ALL_ENEMIES))),
            card(
                "london.punchcard_program", "Punchcard Program", era, CardType.SKILL, Rarity.UNCOMMON, "1",
                listOf(Effect.Foresee(2), Effect.Schedule(2, listOf(Effect.Draw(2)))),
            ),
            card(
                "milan.vitruvian_guard", "Vitruvian Guard", era, CardType.SKILL, Rarity.COMMON, "1",
                listOf(block(4, Bonus.Per(Counter.CONSTANTS, 1, 3))),
            ),
            card(
                "milan.codex_sketch", "Codex Sketch", era, CardType.SKILL, Rarity.UNCOMMON, "1",
                listOf(Effect.GrantRetain, Effect.ReduceCostThisTurn(1)),
            ),
            card("saltwaste.mirage_step", "Mirage Step", era, CardType.SKILL, Rarity.COMMON, "0", listOf(block(1), Effect.Shift(1))),
            card("saltwaste.salt_ward", "Salt Ward", era, CardType.SKILL, Rarity.UNCOMMON, "2", listOf(block(9), Effect.Observe)),
        )
    }

    // ---- Misprint tables (docs/08) ----------------------------------------------------------------------------

    private fun misprintTables(): Map<Pair<CardType, Int>, List<Face>> = mapOf(
        (CardType.ATTACK to 0) to listOf(face(CardType.ATTACK, dmg(3))),
        (CardType.ATTACK to 1) to listOf(
            face(CardType.ATTACK, hits(3, 2)),
            face(CardType.ATTACK, dmg(5), Effect.ApplyStatus(StatusType.EXPOSED, 1)),
            face(CardType.ATTACK, dmg(9), Effect.LoseHp(2)),
            face(CardType.ATTACK, dmgTo(4, Tgt.ALL_ENEMIES)),
        ),
        (CardType.ATTACK to 2) to listOf(
            face(CardType.ATTACK, dmg(12)),
            face(CardType.ATTACK, dmgTo(8, Tgt.ALL_ENEMIES)),
            face(CardType.ATTACK, hits(4, 3)),
        ),
        (CardType.ATTACK to 3) to listOf(
            face(CardType.ATTACK, dmg(18)),
            face(CardType.ATTACK, dmgTo(12, Tgt.ALL_ENEMIES)),
        ),
        (CardType.SKILL to 0) to listOf(
            face(CardType.SKILL, Effect.Draw(1)),
            face(CardType.SKILL, block(1), Effect.Foresee(2)),
        ),
        (CardType.SKILL to 1) to listOf(
            face(CardType.SKILL, block(5)),
            face(CardType.SKILL, block(3), Effect.Draw(1)),
            face(CardType.SKILL, Effect.Foresee(3), Effect.Draw(1)),
            face(CardType.SKILL, Effect.GainPlate(2), block(1)),
        ),
        (CardType.SKILL to 2) to listOf(
            face(CardType.SKILL, block(10)),
            face(CardType.SKILL, block(6), Effect.Draw(2)),
            face(CardType.SKILL, Effect.Draw(3), Effect.Foresee(3)),
        ),
        (CardType.SKILL to 3) to listOf(
            face(CardType.SKILL, block(15)),
            face(CardType.SKILL, block(8), Effect.Draw(3)),
        ),
    )

    // ---- enemies ----------------------------------------------------------------------------------------------

    /** An intent whose Track label is rendered from its actions. Charging intents (countdown 2+) say so. */
    private fun intent(vararg actions: EnemyAction, countdown: Int = 1, fixed: Boolean = false, name: String? = null): IntentSpec {
        val prefix = name ?: if (countdown >= 2) "Charge" else null
        val body = RulesText.intent(actions.toList())
        return IntentSpec("main", if (prefix != null) "$prefix: $body" else body, actions.toList(), countdown, fixed)
    }

    private fun forked(a: List<EnemyAction>, b: List<EnemyAction>): IntentSpec =
        IntentSpec("main", RulesText.intent(a), a, alt = b, altLabel = RulesText.intent(b))

    private fun attack(n: Int, times: Int = 1) = EnemyAction.Attack(n, times)
    private fun guard(n: Int) = EnemyAction.Guard(n)
    private fun debuff(s: StatusType, n: Int) = EnemyAction.Debuff(s, n)
    private fun buff(s: StatusType, n: Int) = EnemyAction.Buff(s, n)

    private fun enemies(): List<EnemyDef> = listOf(
        // Era natives
        EnemyDef(
            "london.gaslight_footpad", "Gaslight Footpad", Faction.ERA, 24,
            RotationAi(listOf(intent(attack(9)), intent(attack(4), debuff(StatusType.WEAK, 1)))),
        ),
        EnemyDef(
            "london.rookery_brawler", "Rookery Brawler", Faction.ERA, 28,
            RotationAi(listOf(intent(attack(9)), intent(attack(18), countdown = 2))),
        ),
        EnemyDef(
            "london.lamplighter", "Lamplighter", Faction.ERA, 22,
            RotationAi(listOf(intent(attack(4), debuff(StatusType.BURN, 2)))),
        ),
        EnemyDef(
            "milan.condottiero", "Condottiero", Faction.ERA, 32,
            RotationAi(listOf(intent(guard(6)), intent(attack(10)), intent(attack(18), countdown = 2))),
        ),
        EnemyDef(
            "milan.plague_doctor", "Plague Doctor", Faction.ERA, 24,
            RotationAi(listOf(intent(debuff(StatusType.BURN, 4)), intent(attack(6), debuff(StatusType.EXPOSED, 1)))),
        ),
        EnemyDef(
            "milan.crossbowman", "Sforza Crossbowman", Faction.ERA, 20,
            RotationAi(listOf(intent(attack(16), countdown = 2), intent(attack(6)))),
        ),
        EnemyDef(
            "saltwaste.salt_wraith", "Salt Wraith", Faction.ERA, 22,
            RotationAi(listOf(intent(EnemyAction.BlankFuture(1), attack(5)), intent(attack(9)))),
        ),
        EnemyDef(
            "saltwaste.sandglass_golem", "Sandglass Golem", Faction.ERA, 38,
            RotationAi(listOf(intent(buff(StatusType.PLATE, 2), attack(4)), intent(attack(12), countdown = 2))),
        ),
        EnemyDef(
            "saltwaste.mirage", "Mirage", Faction.ERA, 20,
            RotationAi(listOf(forked(listOf(attack(12)), listOf(guard(8))))),
        ),
        // The Order
        EnemyDef(
            "order.squire", "Squire of the Line", Faction.ORDER, 24,
            RotationAi(listOf(intent(attack(8)), intent(guard(6)))),
        ),
        EnemyDef(
            "order.sergeant", "Sergeant-at-Arms", Faction.ORDER, 34,
            RotationAi(loop = listOf(intent(attack(10))), opening = listOf(intent(buff(StatusType.PLATE, 2)))),
        ),
        EnemyDef(
            "order.inquisitor", "Inquisitor", Faction.ORDER, 60,
            MultiSlotAi(
                mapOf(
                    "strike" to listOf(intent(attack(7), name = "Strike")),
                    "purge" to listOf(intent(attack(30), countdown = 3, fixed = true, name = "Purge")),
                ),
            ),
            elite = true,
        ),
        // The Convergence
        EnemyDef(
            "conv.brass_proxy", "Brass Proxy", Faction.CONVERGENCE, 26,
            PredictiveAi(ifAttackHeavy = intent(guard(8), attack(6)), otherwise = intent(attack(10))),
        ),
        EnemyDef(
            "conv.calculating_engine", "Calculating Engine", Faction.CONVERGENCE, 30,
            RotationAi(listOf(intent(debuff(StatusType.GLITCH, 2)), intent(attack(7)), intent(guard(7)))),
        ),
        EnemyDef("conv.drone", "Drone", Faction.CONVERGENCE, 10, RotationAi(listOf(intent(attack(5))))),
        EnemyDef(
            "conv.pruner", "Pruner", Faction.CONVERGENCE, 56,
            MultiSlotAi(
                mapOf(
                    "jab" to listOf(intent(attack(7))),
                    "prune" to listOf(
                        intent(EnemyAction.Disrupt(1), EnemyAction.EraseFuture(1), name = "Prune"),
                        intent(attack(14), countdown = 2),
                    ),
                ),
            ),
            elite = true,
        ),
        // The Errata
        EnemyDef(
            "errata.misprint", "Misprint", Faction.ERRATA, 26,
            RotationAi(listOf(forked(listOf(attack(14)), listOf(debuff(StatusType.WEAK, 2))))),
        ),
        EnemyDef("errata.double", "Double", Faction.ERRATA, 36, DoubleAi(fallbackDamage = 8)),
        // The Tear
        EnemyDef(
            "tear.loose_end", "Loose End", Faction.TEAR, 12,
            RotationAi(listOf(intent(EnemyAction.BlankFuture(1), attack(4)))),
        ),
        EnemyDef(
            "tear.ravel", "Ravel", Faction.TEAR, 34,
            RotationAi(listOf(intent(EnemyAction.EraseFuture(1), attack(6)), intent(attack(14)))),
        ),
        EnemyDef(
            "tear.the_rent", "The Rent", Faction.TEAR, 82,
            RotationAi(
                listOf(
                    intent(EnemyAction.SilenceConstants, attack(6)),
                    intent(attack(20), countdown = 2),
                    intent(EnemyAction.BlankFuture(2), attack(8)),
                ),
            ),
            elite = true,
        ),
        // Act boss, London 1843
        EnemyDef(
            "london.lattice_engine", "The Lattice Engine", Faction.CONVERGENCE, 85,
            LatticeAi(
                subs = listOf(
                    LatticeSub("Bulwark.sub", listOf(guard(4))),
                    LatticeSub("Lance.sub", listOf(attack(3))),
                    LatticeSub("Jacquard.sub", listOf(debuff(StatusType.GLITCH, 1))),
                    LatticeSub("Overclock.sub", listOf(buff(StatusType.MIGHT, 1))),
                ),
                render = RulesText::intent,
                installAttack = 5,
                guardedBlock = 6,
                guardedAttack = 5,
                openAttack = 10,
                cascadePer = 5,
            ),
            elite = true,
        ),
    )

    private fun encounters(): List<Encounter> = listOf(
        Encounter("fleet_street", "Fog on Fleet Street", listOf("london.gaslight_footpad", "london.gaslight_footpad")),
        Encounter("rookery", "Rookery Brawl", listOf("london.rookery_brawler", "london.lamplighter")),
        Encounter("proxy_patrol", "Proxy Patrol", listOf("conv.brass_proxy", "conv.calculating_engine")),
        Encounter("misprinted_alley", "Misprinted Alley", listOf("errata.misprint", "errata.double")),
        Encounter("order_patrol", "Order Patrol", listOf("order.squire", "order.sergeant")),
        Encounter("loose_threads", "Loose Threads", listOf("tear.ravel", "tear.loose_end")),
        Encounter("condottieri", "Condottieri", listOf("milan.condottiero", "milan.crossbowman")),
        Encounter("plague_season", "Plague Season", listOf("milan.plague_doctor", "milan.crossbowman")),
        Encounter("salt_mirage", "Salt Mirage", listOf("saltwaste.salt_wraith", "saltwaste.mirage")),
        Encounter("glass_desert", "Glass Desert", listOf("saltwaste.sandglass_golem", "saltwaste.mirage")),
        Encounter("drone_swarm", "Drone Swarm", listOf("conv.drone", "conv.drone", "conv.drone", "conv.drone")),
        Encounter("inquisitor", "The Inquisitor", listOf("order.inquisitor"), elite = true),
        Encounter("pruner", "The Pruner", listOf("conv.pruner", "conv.drone"), elite = true),
        Encounter("the_rent", "The Rent", listOf("tear.the_rent", "tear.loose_end"), elite = true),
        // London 1843, for the run (docs/05): single foes to open an act, a few heavier fights to close it.
        Encounter("lone_footpad", "A Footpad in the Fog", listOf("london.gaslight_footpad")),
        Encounter("rookery_tough", "A Rookery Tough", listOf("london.rookery_brawler")),
        Encounter("fraying_street", "A Fraying Street", listOf("tear.loose_end", "tear.loose_end")),
        Encounter("stray_proxy", "A Stray Proxy", listOf("conv.brass_proxy")),
        Encounter("squire_errant", "A Squire Errant", listOf("order.squire")),
        Encounter("lone_misprint", "A Misprint on Drury Lane", listOf("errata.misprint")),
        Encounter("lamplighters", "The Lamplighters' Round", listOf("london.lamplighter", "london.gaslight_footpad")),
        Encounter("rookery_gang", "The Rookery Gang", listOf("london.rookery_brawler", "london.gaslight_footpad")),
        Encounter("brass_constables", "Brass Constables", listOf("conv.brass_proxy", "conv.brass_proxy")),
        Encounter("line_in_fog", "The Line in the Fog", listOf("order.sergeant", "order.sergeant")),
        Encounter("misprinted_quarter", "The Misprinted Quarter", listOf("errata.misprint", "errata.misprint")),
        Encounter("unravelling", "The Unravelling", listOf("tear.ravel", "tear.loose_end", "tear.loose_end")),
        Encounter("lattice_engine", "The Lattice Engine", listOf("london.lattice_engine"), elite = true),
    )

    // ---- operatives and decks ---------------------------------------------------------------------------------

    private fun operatives(): List<OperativeDef> = listOf(
        OperativeDef("vowknight", "Vowknight", Faction.ORDER, 75, Signature.STRIKE, starter("vowknight")),
        OperativeDef("oracle", "Oracle", Faction.CONVERGENCE, 65, Signature.FORECAST, starter("oracle")),
        OperativeDef("splinter", "Splinter", Faction.ERRATA, 62, Signature.SHIFT, starter("splinter"), otherHandSize = 3),
    )

    private fun starter(op: String) = decks().getValue(op).getValue("starter")

    private fun copies(n: Int, id: String) = List(n) { id }

    private fun decks(): Map<String, Map<String, List<String>>> {
        val vow = copies(4, "order.line_strike") + copies(4, "order.shield_of_the_line") +
            listOf("order.vow_of_the_sword", "order.remembered_blow")
        val oracle = copies(4, "conv.pulse") + copies(4, "conv.firewall") + listOf("conv.probability_lance", "conv.watchdog")
        val splinter = copies(4, "errata.splinter_cut") + copies(4, "errata.double_guard") +
            listOf("errata.split_the_moment", "errata.two_places_at_once")
        return mapOf(
            "vowknight" to mapOf(
                "starter" to vow,
                "mid" to vow + listOf(
                    "order.hold_fast", "order.sundial_cut", "order.crusaders_charge", "order.litany_of_steel",
                    "order.gnomon_blade", "order.oathkeepers_stand", "neutral.hold_the_hour", "neutral.stitch_in_time",
                ),
            ),
            "oracle" to mapOf(
                "starter" to oracle,
                "mid" to oracle + listOf(
                    "conv.recursive_strike", "conv.predictive_shield", "conv.scatter_protocol", "conv.deterministic_model",
                    "conv.lance_exe", "conv.hold_pattern", "neutral.hold_the_hour", "neutral.stitch_in_time",
                ),
            ),
            "splinter" to mapOf(
                "starter" to splinter,
                "mid" to splinter + listOf(
                    "errata.borrowed_self", "errata.fray_lash", "errata.caret_strike", "errata.crosstalk",
                    "errata.elsewhen_guard", "errata.misprinted_edict", "neutral.hold_the_hour", "saltwaste.mirage_step",
                ),
            ),
        )
    }
}
