package dcbb.core.content

import dcbb.core.model.Amount
import dcbb.core.model.CombatMods
import dcbb.core.model.Effect
import dcbb.core.model.EnemyBoost
import dcbb.core.model.EnergyColor
import dcbb.core.model.Faction
import dcbb.core.model.Rarity
import dcbb.core.model.StatusType
import dcbb.core.model.Tgt
import dcbb.core.run.ArtifactDef
import dcbb.core.run.CardPool
import dcbb.core.run.ChoiceDef
import dcbb.core.run.DeckTrait
import dcbb.core.run.EraDef
import dcbb.core.run.EraFight
import dcbb.core.run.EventDef
import dcbb.core.run.EventKind
import dcbb.core.run.ImprintDef
import dcbb.core.run.ImprintReq
import dcbb.core.run.MomentType
import dcbb.core.run.Perk
import dcbb.core.run.Purpose
import dcbb.core.run.Requirement
import dcbb.core.run.RippleDef
import dcbb.core.run.RunContent
import dcbb.core.run.RunEffect

/**
 * The First Hour (docs/02 "The MVP Mission"): London 1843, Milan 1495 and the Salt Waste in 1191, back along one
 * thread to the knot where time travel began. The run-layer content: each era's Era Deck, its events and
 * Divergence, Artifacts and boss Artifacts, Ripples, and Imprints. Event prose is the authored fallback text
 * (docs/05 "How events are built").
 */
object FirstHour {

    val run: RunContent by lazy {
        RunContent(
            content = Prototype.content,
            eras = listOf(london(), milan(), saltWaste()),
            events = events() + milanEvents() + saltWasteEvents() + shrines(),
            artifacts = artifacts() + laterArtifacts() + bossArtifacts(),
            ripples = ripples() + milanRipples() + saltWasteRipples(),
            shrines = mapOf(
                Faction.ORDER to "shrine.order_chapel",
                Faction.CONVERGENCE to "shrine.convergence_terminal",
                Faction.ERRATA to "shrine.errata_seam",
            ),
            imprints = imprints(),
        )
    }

    /** An 8-step act's Era Deck (docs/05 "Era Deck composition"). */
    private val ACT_DECK = linkedMapOf(
        MomentType.COMBAT to 10,
        MomentType.ELITE to 2,
        MomentType.EVENT to 5,
        MomentType.ANOMALY to 1,
        MomentType.ANTIQUARIAN to 2,
        MomentType.STILL_POINT to 2,
        MomentType.SHRINE to 1,
        MomentType.CACHE to 1,
    )

    private fun london() = EraDef(
        id = "london_1843",
        name = "London, 1843",
        act = 1,
        steps = 8,
        combats = listOf(
            EraFight("lone_footpad", 1),
            EraFight("rookery_tough", 1),
            EraFight("fraying_street", 1),
            EraFight("stray_proxy", 1),
            EraFight("squire_errant", 1),
            EraFight("lone_misprint", 1),
            EraFight("fleet_street", 2),
            EraFight("rookery", 2),
            EraFight("lamplighters", 2),
            EraFight("loose_threads", 2),
            EraFight("proxy_patrol", 2),
            EraFight("drone_swarm", 2),
            EraFight("misprinted_alley", 2),
            EraFight("order_patrol", 2),
            EraFight("rookery_gang", 3),
            EraFight("brass_constables", 3),
            EraFight("line_in_fog", 3),
            EraFight("misprinted_quarter", 3),
            EraFight("unravelling", 3),
        ),
        elites = listOf("inquisitor", "pruner", "the_rent"),
        tearFights = listOf("fraying_street", "loose_threads"),
        events = listOf(
            "london.the_copyist", "london.penny_dreadful", "london.babbage_workshop", "london.mudlark", "london.broad_street",
        ),
        divergence = "london.the_notes",
        anomalies = listOf("anomaly.fading_street", "anomaly.the_loop"),
        boss = "lattice_engine",
        cardPrefix = "london.",
        composition = ACT_DECK,
        titles = mapOf(
            MomentType.ANTIQUARIAN to listOf("A Curiosity Shop on Holywell Street", "The Pawnbroker of Seven Dials", "A Bookseller off Paternoster Row"),
            MomentType.STILL_POINT to listOf("A Quiet Pew at St Bride's", "A Coaching Inn at Dusk", "The Reading Room after Hours"),
            MomentType.CACHE to listOf("A Loose Floorboard", "An Unclaimed Parcel", "A Strongbox in the Thames Mud"),
        ),
        intro = "Fog, gaslight and the print shops of Fleet Street. Ada Lovelace's notes on the Analytical Engine are " +
            "MERIDIAN's earliest anchor, and Proxies in constables' coats are watching them.",
    )

    /**
     * Act II: Milan, 1495. Leonardo's mechanical knight stands open-chested in the Corte Vecchia; what you put in its
     * chest decides the Automa Cavaliere you fight at the end of the act.
     */
    private fun milan() = EraDef(
        id = "milan_1495",
        name = "Milan, 1495",
        act = 2,
        steps = 8,
        combats = listOf(
            EraFight("lone_condottiero", 1),
            EraFight("pikemen_at_the_gate", 1),
            EraFight("lone_penitent", 1),
            EraFight("engine_on_the_navigli", 1),
            EraFight("double_in_the_crowd", 1),
            EraFight("ravel_in_the_cloister", 1),
            EraFight("condottieri", 2),
            EraFight("plague_season", 2),
            EraFight("pike_square", 2),
            EraFight("the_lion_wakes", 2),
            EraFight("penitents_procession", 2),
            EraFight("proxy_patrol", 2),
            EraFight("misprinted_alley", 2),
            EraFight("loose_threads", 2),
            EraFight("sforza_guard", 3),
            EraFight("free_company", 3),
            EraFight("lion_and_pikes", 3),
            EraFight("line_in_milan", 3),
            EraFight("brass_court", 3),
            EraFight("margins_of_milan", 3),
            EraFight("fraying_cloister", 3),
        ),
        elites = listOf("capitano", "seam_runner", "inquisitor", "pruner", "the_rent"),
        tearFights = listOf("ravel_in_the_cloister", "loose_threads"),
        events = listOf("milan.leonardos_notebook", "milan.plague_cart", "milan.dukes_banquet", "milan.refectory_scaffold", "milan.letter_of_credit"),
        divergence = "milan.automatons_heart",
        anomalies = listOf("anomaly.the_loop", "anomaly.painted_window"),
        boss = "automa_hollow",
        bossByFlag = mapOf(
            "milan.heart_meridian" to "automa_meridian",
            "milan.heart_order" to "automa_order",
            "milan.heart_errata" to "automa_errata",
        ),
        cardPrefix = "milan.",
        composition = ACT_DECK,
        titles = mapOf(
            MomentType.ANTIQUARIAN to listOf("A Goldsmith's Stall on the Ponte", "A Banker's Back Room", "A Bookshop by the Broletto"),
            MomentType.STILL_POINT to listOf("A Cloister Garden", "The Navigli at Dusk", "A Chapel of Santa Maria delle Grazie"),
            MomentType.CACHE to listOf("A Crate in the Corte Vecchia", "A Sealed Codex", "A Saddlebag in the Stables"),
        ),
        scaling = CombatMods(hpPct = 125, enemyBoosts = listOf(EnemyBoost(StatusType.MIGHT, 1))),
        intro = "You follow the thread three and a half centuries back, to Milan in 1495. Leonardo's mechanical knight is " +
            "being built in the Corte Vecchia. MERIDIAN and the Order both want what will sit in its chest.",
    )

    /** Act III: the Salt Waste, 1191. The Nexus: the Gnomon is found, and a faction's champion comes for it. */
    private fun saltWaste() = EraDef(
        id = "saltwaste_1191",
        name = "The Salt Waste, 1191",
        act = 3,
        steps = 7,
        combats = listOf(
            EraFight("lone_crusader", 1),
            EraFight("horse_archer", 1),
            EraFight("lone_golem", 1),
            EraFight("glassborn", 1),
            EraFight("salt_wraiths", 1),
            EraFight("salt_mirage", 2),
            EraFight("glass_desert", 2),
            EraFight("crusader_and_archer", 2),
            EraFight("horse_archers", 2),
            EraFight("glass_and_salt", 2),
            EraFight("order_patrol", 2),
            EraFight("proxy_patrol", 2),
            EraFight("misprinted_alley", 2),
            EraFight("loose_threads", 2),
            EraFight("two_crusaders", 3),
            EraFight("the_shattered_dunes", 3),
            EraFight("archers_and_mirage", 3),
            EraFight("line_in_milan", 3),
            EraFight("brass_court", 3),
            EraFight("margins_of_milan", 3),
            EraFight("fraying_cloister", 3),
        ),
        elites = listOf("glass_sentinel", "seam_runner", "inquisitor", "pruner", "the_rent"),
        tearFights = listOf("salt_wraiths", "loose_threads"),
        events = listOf("saltwaste.aurels_vigil", "saltwaste.salt_caravan", "saltwaste.well", "saltwaste.glass_field", "saltwaste.truce"),
        divergence = "saltwaste.shadow_of_the_gnomon",
        anomalies = listOf("anomaly.sundial", "anomaly.salt_mirror"),
        boss = "thousandfold",
        nexus = mapOf(Faction.ORDER to "aurelian", Faction.CONVERGENCE to "meridian_ascendant", Faction.ERRATA to "thousandfold"),
        cardPrefix = "saltwaste.",
        composition = linkedMapOf(
            MomentType.COMBAT to 9,
            MomentType.ELITE to 2,
            MomentType.EVENT to 4,
            MomentType.ANOMALY to 1,
            MomentType.ANTIQUARIAN to 2,
            MomentType.STILL_POINT to 2,
            MomentType.SHRINE to 1,
        ),
        titles = mapOf(
            MomentType.ANTIQUARIAN to listOf("A Trader under an Awning", "A Caravan's Last Camel", "A Pedlar at the Wells"),
            MomentType.STILL_POINT to listOf("An Oasis at Night", "A Ruined Caravanserai", "The Lee of a Dune"),
            MomentType.CACHE to listOf("A Buried Chest", "A Dead Rider's Pack"),
        ),
        divergenceSteps = 3..5,
        scaling = CombatMods(hpPct = 150, enemyBoosts = listOf(EnemyBoost(StatusType.MIGHT, 1))),
        intro = "Three more centuries back, the thread ends in the Salt Waste in 1191, during the Third Crusade. A company of " +
            "knights is crossing the salt, and young Brother Aurel is a day from the needle of black glass. Whoever holds the " +
            "Gnomon holds the First Hour.",
    )

    // ---- events -----------------------------------------------------------------------------------------------

    private fun choice(id: String, text: String, vararg effects: RunEffect, ripples: List<String> = emptyList(), requires: Requirement = Requirement.NONE) =
        ChoiceDef(id, text, effects.toList(), ripples, requires)

    private fun hours(n: Int) = RunEffect.Hours(n)
    private fun paradox(n: Int) = RunEffect.Paradox(n)
    private fun standing(f: Faction, n: Int) = RunEffect.Standing(f, n)
    private fun flag(id: String) = RunEffect.Flag(id)

    private fun events(): List<EventDef> = listOf(
        EventDef(
            "london.the_copyist", "The Copyist", "A Candle in a Cellar", EventKind.DILEMMA,
            "Candle stubs, ink on her cuffs, and the sound of boots on wet cobbles. A scribe is copying a page the Order " +
                "has forbidden. An Order patrol is two streets away.",
            listOf(
                choice(
                    "hide", "Pull her into the cellar.",
                    standing(Faction.ORDER, -1), standing(Faction.ERRATA, 1),
                    RunEffect.GainCard(CardPool(Faction.ERRATA, Rarity.COMMON)), flag("scribe_hidden"),
                ),
                choice("report", "Take the page. The Line will judge it.", standing(Faction.ORDER, 1), hours(40), flag("scribe_reported")),
                choice("read", "Read it first.", paradox(2), RunEffect.Omens(1), flag("read_forbidden_page")),
            ),
            consequence = "london.copyist_in_a_cell",
        ),
        EventDef(
            "london.the_notes", "The Notes", "Ink and Brass", EventKind.DIVERGENCE,
            "Ada Lovelace's notes on the Analytical Engine lie on a desk in a locked print shop: the translation, and the " +
                "long appendix that will outlive the machine. A stranger in an oilskin coat says he is from 2049 and asks " +
                "for them. The Order would pay well. The fire is lit.",
            listOf(
                choice("burn", "Burn the notes.", flag("london.notes_burned"), ripples = listOf("engine_never_built", "order_takes_credit")),
                choice("copy", "Copy them for the Order.", flag("london.notes_to_order"), ripples = listOf("canon_of_engines", "scriptorium")),
                choice("stranger", "Give them to the stranger from 2049.", flag("london.notes_to_stranger"), ripples = listOf("bootstrapped", "signal_loop")),
                choice("untouched", "Let history run.", flag("london.notes_untouched"), ripples = listOf("as_written", "tear_notices")),
            ),
            consequence = "london.notes_at_auction",
        ),
        EventDef(
            "london.penny_dreadful", "A Penny Dreadful", "A Boy Crying the News", EventKind.ENCOUNTER,
            "A printer's boy is selling a serial about a knight who steps out of time and into Whitechapel. The woodcut " +
                "on the cover has your face, and this week's number ends at the door you are about to open.",
            listOf(
                choice("buy", "Buy every number in print.", hours(-25), RunEffect.Omens(1)),
                choice("author", "Find out who writes it.", paradox(1), RunEffect.GainCard(CardPool(rarity = Rarity.UNCOMMON)), flag("london.author_sought")),
                choice("walk", "Walk on."),
            ),
            consequence = "london.penny_dreadful_ending",
        ),
        EventDef(
            "london.babbage_workshop", "Babbage's Workshop", "Brass behind a Shutter", EventKind.ENCOUNTER,
            "A workshop off Dorset Street, full of brass that was never assembled. One column of gears turns by itself, " +
                "counting something. Nobody else is here.",
            listOf(
                choice("pocket", "Pocket the turning gear.", RunEffect.GainArtifact(), paradox(2)),
                choice("sell", "Strip the brass and sell it.", hours(60), standing(Faction.CONVERGENCE, -1)),
                choice("leave", "Leave it counting.", flag("london.engine_left_counting")),
            ),
            consequence = "london.workshop_cold",
        ),
        EventDef(
            "london.mudlark", "The Mudlark", "Low Tide at Blackfriars", EventKind.ENCOUNTER,
            "At low tide a mudlark offers you something she pulled out of the Thames: a coin minted in a year that " +
                "hasn't happened yet. She also knows every patrol on the river.",
            listOf(
                choice("coin", "Buy the coin.", hours(-40), RunEffect.GainCard(CardPool(rarity = Rarity.RARE)), paradox(1)),
                choice("patrols", "Pay her for the patrols.", hours(-15), RunEffect.Omens(1)),
                choice("leave", "Leave her to the tide."),
            ),
            consequence = "london.mudlark_robbed",
        ),
        EventDef(
            "london.broad_street", "The Night Ward", "Lanterns at a Ward Door", EventKind.ENCOUNTER,
            "A fever ward near Golden Square is short of hands tonight. The surgeon doesn't ask who you are. The " +
                "dispensary door is open.",
            listOf(
                choice("work", "Work the night shift.", RunEffect.LoseHp(6), RunEffect.Pick(Purpose.INSCRIBE), standing(Faction.ORDER, 1)),
                choice("laudanum", "Take laudanum from the dispensary.", RunEffect.HealPct(20), paradox(1)),
                choice("leave", "Keep walking."),
            ),
            consequence = "london.ward_overflowing",
        ),
        // Consequences: what an event became while you were elsewhere (docs/05 "Returning Moments").
        EventDef(
            "london.copyist_in_a_cell", "The Copyist, in a Cell", "A Name in the Charge Book", EventKind.ENCOUNTER,
            "The scribe from the cellar is in a cell at Bow Street now, her page held as evidence. A sergeant of the " +
                "Line sits by the door with the keys on his belt. She recognizes you before he does.",
            listOf(
                choice("free", "Get her out.", RunEffect.LoseHp(5), standing(Faction.ORDER, -1), standing(Faction.ERRATA, 1), flag("scribe_freed")),
                choice("bribe", "Pay the sergeant to look away.", hours(-30), standing(Faction.ERRATA, 1), flag("scribe_freed")),
                choice("testify", "Tell the sergeant what you saw.", standing(Faction.ORDER, 1), hours(25), flag("scribe_testified")),
            ),
        ),
        EventDef(
            "london.notes_at_auction", "The Notes, at Auction", "A Gavel on King Street", EventKind.DIVERGENCE,
            "The notes you walked past have surfaced in an auction room on King Street. The stranger in the oilskin " +
                "coat is bidding against an Order envoy and a man from the British Museum. History is for sale.",
            listOf(
                choice("burn", "Outbid them all, then burn the notes.", hours(-40), flag("london.notes_burned"), ripples = listOf("engine_never_built", "order_takes_credit")),
                choice("copy", "Bid for the Order.", hours(-20), flag("london.notes_to_order"), ripples = listOf("canon_of_engines", "scriptorium")),
                choice("stranger", "Let the stranger win.", flag("london.notes_to_stranger"), ripples = listOf("bootstrapped", "signal_loop")),
                choice("untouched", "Let the Museum have them.", flag("london.notes_untouched"), ripples = listOf("as_written", "tear_notices")),
            ),
        ),
        EventDef(
            "london.penny_dreadful_ending", "The Last Number", "The Final Number Is Out", EventKind.ENCOUNTER,
            "The serial's last number is out. The knight dies in it, at a door you recognize, on a night with this " +
                "same fog. The printer's boy has three copies left.",
            listOf(
                choice("read", "Read the ending.", RunEffect.Omens(1), paradox(1)),
                choice("burn", "Buy the print run and burn it.", hours(-30), paradox(-1)),
                choice("walk", "Let the ending stand."),
            ),
        ),
        EventDef(
            "london.workshop_cold", "A Cold Workshop", "A Shutter Left Open", EventKind.ENCOUNTER,
            "Someone has been to Babbage's workshop since you. The counting column is gone. Brass shavings trail out " +
                "of the door, and a concentric-circle sigil is scratched into the bench.",
            listOf(
                choice("follow", "Follow the shavings.", RunEffect.Fight("stray_proxy"), standing(Faction.CONVERGENCE, -1)),
                choice("sell", "Sweep up the shavings for a jeweller.", hours(35)),
                choice("leave", "Leave it cold."),
            ),
        ),
        EventDef(
            "london.mudlark_robbed", "The Mudlark, Robbed", "A Bruise at Low Tide", EventKind.ENCOUNTER,
            "The mudlark again, thinner, with a bruise on her cheek. Someone took the coin from her, and she knows " +
                "which lodging house they went into.",
            listOf(
                choice("give", "Give her money for a room.", hours(-20), RunEffect.Omens(1), standing(Faction.ERRATA, 1)),
                choice("collect", "Go and get the coin back.", RunEffect.Fight("fleet_street")),
                choice("leave", "Walk on."),
            ),
        ),
        EventDef(
            "london.ward_overflowing", "The Ward, Overflowing", "Cots in the Street", EventKind.ENCOUNTER,
            "The fever ward has spilled into the street: cots under the lamps, a priest and a surgeon arguing over " +
                "the same patient. The surgeon recognizes you and holds out an apron.",
            listOf(
                choice("help", "Take the apron.", RunEffect.LoseHp(8), standing(Faction.ORDER, 1), RunEffect.GainCard(CardPool(rarity = Rarity.UNCOMMON))),
                choice("supplies", "Take what supplies are left.", RunEffect.HealPct(25), paradox(1)),
                choice("leave", "Keep walking."),
            ),
        ),
        // Anomalies (docs/05 "Anomaly")
        EventDef(
            "anomaly.fading_street", "Fading Street", "A Street Going Thin", EventKind.ANOMALY,
            "A whole street is thinning out of existence: the gas lamps first, then the doors, then the people who " +
                "lived behind them. Someone could hold it in place for a while. It would cost them.",
            listOf(
                choice("help", "Hold it in place.", RunEffect.LoseHp(8), standing(Faction.ERRATA, 1), flag("london.street_held")),
                choice("fade", "Let it Fade, and take what falls out.", hours(40), paradox(1)),
            ),
        ),
        EventDef(
            "anomaly.the_loop", "The Loop", "A Clock Running Back", EventKind.ANOMALY,
            "Every clock in the square runs backward to the moment your last fight began. You could live it again, " +
                "and this time keep twice what you earn.",
            listOf(
                choice("relive", "Relive it.", paradox(1), RunEffect.Fight(null, hoursMult = 2)),
                choice("step_out", "Step out of the loop."),
            ),
        ),
    )

    private fun shrines(): List<EventDef> = listOf(
        EventDef(
            "shrine.order_chapel", "Order Chapel", "Order Chapel", EventKind.SHRINE,
            "A side chapel the Order keeps in every century: one candle, one copy of the Canon, one kneeler worn smooth.",
            listOf(
                choice("erase", "Erase a card from history.", RunEffect.Pick(Purpose.ERASE), requires = Requirement.OWN_FACTION),
                choice("relic", "Take up a Relic.", RunEffect.GainCard(CardPool(Faction.ORDER, relicOnly = true)), requires = Requirement.OWN_FACTION),
                choice("heal", "Pray.", RunEffect.HealPct(20), requires = Requirement.OWN_FACTION),
                choice("desecrate", "Desecrate it.", hours(50), paradox(1), standing(Faction.ORDER, -1), requires = Requirement.OTHER_FACTION),
                choice("leave", "Leave it be."),
            ),
            faction = Faction.ORDER,
        ),
        EventDef(
            "shrine.convergence_terminal", "Convergence Terminal", "Convergence Terminal", EventKind.SHRINE,
            "A brass cabinet that shouldn't exist for another two centuries hums behind a false wall. Its screen asks " +
                "for your credentials, then decides it already knows them.",
            listOf(
                choice("inscribe", "Inscribe two cards.", RunEffect.Pick(Purpose.INSCRIBE, 2), requires = Requirement.OWN_FACTION),
                choice("omens", "Download a forecast.", RunEffect.Omens(2), requires = Requirement.OWN_FACTION),
                choice("desecrate", "Desecrate it.", hours(50), paradox(1), standing(Faction.CONVERGENCE, -1), requires = Requirement.OTHER_FACTION),
                choice("leave", "Leave it be."),
            ),
            faction = Faction.CONVERGENCE,
        ),
        EventDef(
            "shrine.errata_seam", "Errata Seam", "Errata Seam", EventKind.SHRINE,
            "A seam in the brickwork where the mortar is the wrong century. Through it you can hear someone arguing " +
                "with themselves, in your voice.",
            listOf(
                choice("rare", "Reach through.", paradox(2), RunEffect.GainCard(CardPool(Faction.ERRATA, Rarity.RARE)), requires = Requirement.OWN_FACTION),
                choice("steady", "Stitch yourself tighter.", paradox(-2), requires = Requirement.OWN_FACTION),
                choice("desecrate", "Desecrate it.", hours(50), paradox(1), standing(Faction.ERRATA, -1), requires = Requirement.OTHER_FACTION),
                choice("leave", "Leave it be."),
            ),
            faction = Faction.ERRATA,
        ),
    )

    // ---- Artifacts (docs/05 "Artifacts": the ones the engine supports so far) -------------------------------------

    private fun artifacts(): List<ArtifactDef> = listOf(
        ArtifactDef(
            "antikythera_gear", "Antikythera Gear", "Start of each combat: Foresee 3.", 160,
            mods = CombatMods(startEffects = listOf(Effect.Foresee(3))),
        ),
        ArtifactDef("baghdad_cell", "Baghdad Cell", "Your Reservoir cap is 8.", 140, mods = CombatMods(reservoirCap = 8)),
        ArtifactDef(
            "clockwork_sparrow", "Clockwork Sparrow",
            "The first time each combat you Delay an enemy intent, it gains no Pressure.", 150,
            mods = CombatMods(freeFirstDelay = true),
        ),
        ArtifactDef(
            "piri_reis_fragment", "Piri Reis Fragment",
            "+1 Omen per act. Each Omen also reveals a second Moment of the next step.", 130,
            perks = setOf(Perk.OMEN_EXTRA_PEEK), onGain = listOf(RunEffect.Omens(1)),
        ),
        ArtifactDef(
            "gnomon_splinter", "Gnomon Splinter", "Fixed intents deal 20% less damage to you.", 180,
            mods = CombatMods(fixedDamagePct = 80),
        ),
        ArtifactDef(
            "lovelaces_notes", "Lovelace's Notes", "Start of each combat: install a random Subroutine from your deck for free.", 170,
            mods = CombatMods(installSubroutine = true),
        ),
        ArtifactDef(
            "bell_of_the_still_hour", "Bell of the Still Hour", "At Still Points you may both heal and Inscribe.", 120,
            perks = setOf(Perk.REST_HEAL_AND_INSCRIBE),
        ),
        ArtifactDef(
            "byzantine_fire_jar", "Byzantine Fire Jar", "Start of each combat: apply Burn 3 to all enemies.", 150,
            mods = CombatMods(startEffects = listOf(Effect.ApplyStatus(StatusType.BURN, 3, Tgt.ALL_ENEMIES))),
        ),
        ArtifactDef(
            "misprinted_psalter", "Misprinted Psalter",
            "Start each combat with +1 Paradox. Your Attacks deal +1 damage per 3 Paradox.", 140,
            mods = CombatMods(startEffects = listOf(Effect.GainParadox(1)), attackPerParadox = 3),
        ),
    )

    // ---- Ripples of The Notes (docs/05 "Divergence events and Ripples") -------------------------------------------

    private fun ripples(): List<RippleDef> = listOf(
        RippleDef(
            "engine_never_built", "The Engine Never Built", "Convergence enemies have 10% less HP this run.",
            mods = CombatMods(enemyHpPct = mapOf(Faction.CONVERGENCE to 90)),
        ),
        RippleDef(
            "order_takes_credit", "The Order Takes Credit", "Order enemies start each fight with Plate 1.",
            mods = CombatMods(enemyBoosts = listOf(EnemyBoost(StatusType.PLATE, 1, faction = Faction.ORDER))),
        ),
        RippleDef(
            "canon_of_engines", "Canon of Engines", "Relic cards cost 25% less at the Antiquarian.",
            perks = setOf(Perk.RELIC_DISCOUNT),
        ),
        RippleDef("scriptorium", "Scriptorium", "Inscribe costs 20 Hours less at the Antiquarian.", perks = setOf(Perk.INSCRIBE_DISCOUNT)),
        RippleDef(
            "bootstrapped", "Bootstrapped",
            "Convergence cards appear twice as often in card rewards, but Brass Proxies start each fight with Might 1.",
            mods = CombatMods(enemyBoosts = listOf(EnemyBoost(StatusType.MIGHT, 1, enemyId = "conv.brass_proxy"))),
            perks = setOf(Perk.CONVERGENCE_REWARDS),
        ),
        RippleDef(
            "signal_loop", "Signal Loop", "+1 Omen now and at the start of each act, and +1 Paradox.",
            now = listOf(RunEffect.Omens(1), RunEffect.Paradox(1)), perks = setOf(Perk.OMENS_PER_ACT),
        ),
        RippleDef("as_written", "As Written", "Order Standing +1.", now = listOf(RunEffect.Standing(Faction.ORDER, 1))),
        RippleDef(
            "tear_notices", "The Tear Notices", "+1 Paradox, and your next Cache holds more.",
            now = listOf(RunEffect.Paradox(1)),
        ),
    )

    // ---- Milan, 1495 (Act II) ------------------------------------------------------------------------------------

    private fun milanEvents(): List<EventDef> = listOf(
        EventDef(
            "milan.automatons_heart", "The Automaton's Heart", "An Open Chest of Brass", EventKind.DIVERGENCE,
            "Leonardo's mechanical knight stands open-chested in a workshop of the Corte Vecchia, pulleys where its " +
                "heart should be. Three hearts could fill it: a brass escapement stamped with concentric circles, a " +
                "reliquary of the Order, or a page of Errata misprints folded small. The master is at dinner.",
            listOf(
                choice("meridian", "Fit MERIDIAN's escapement.", flag("milan.heart_meridian"), ripples = listOf("clockwork_lineage", "perpetual_motion")),
                choice("order", "Seal the Order's relic inside.", flag("milan.heart_order"), ripples = listOf("relic_knight", "canonized")),
                choice("errata", "Fold the misprint into its chest.", flag("milan.heart_errata"), ripples = listOf("margins_in_the_machine", "unwritten_knight")),
                choice("hollow", "Leave the chest empty.", flag("milan.heart_hollow"), ripples = listOf("hollow_knight", "history_holds")),
            ),
            consequence = "milan.knight_finished",
        ),
        EventDef(
            "milan.knight_finished", "The Knight, Finished by Others", "Footprints in the Workshop", EventKind.DIVERGENCE,
            "Someone has been in Leonardo's workshop since you walked past. The knight's chest is still open, but the " +
                "tools are laid out for a fitting, and three people are arguing over which heart it gets. You can still " +
                "decide, at a price.",
            listOf(
                choice("meridian", "Pay the stranger to fit the escapement.", hours(-30), flag("milan.heart_meridian"), ripples = listOf("clockwork_lineage", "perpetual_motion")),
                choice("order", "Back the Order's envoy.", RunEffect.LoseHp(5), flag("milan.heart_order"), ripples = listOf("relic_knight", "canonized")),
                choice("errata", "Slip the misprint in while they argue.", paradox(1), flag("milan.heart_errata"), ripples = listOf("margins_in_the_machine", "unwritten_knight")),
                choice("hollow", "Bar the door and leave it empty.", flag("milan.heart_hollow"), ripples = listOf("hollow_knight", "history_holds")),
            ),
        ),
        EventDef(
            "milan.leonardos_notebook", "Leonardo's Notebook", "Mirror Writing on a Bench", EventKind.ENCOUNTER,
            "A notebook lies open on a bench: horses, water, a wing, and lines of writing that only make sense in a " +
                "mirror. Its owner has stepped out to argue with a foundryman.",
            listOf(
                choice("read", "Read it in a mirror.", RunEffect.Omens(1), paradox(1), flag("milan.notebook_read")),
                choice("copy", "Copy the page on the wing.", RunEffect.GainCard(CardPool(rarity = Rarity.UNCOMMON)), flag("milan.notebook_copied")),
                choice("wait", "Close it and wait for him.", RunEffect.HealPct(15), flag("milan.leonardo_met")),
            ),
            consequence = "milan.notebook_sold",
        ),
        EventDef(
            "milan.notebook_sold", "The Notebook, Sold", "A Codex on a Goldsmith's Stall", EventKind.ENCOUNTER,
            "The notebook you left on the bench is on a goldsmith's stall now, between a reliquary and a pair of " +
                "spurs. The goldsmith has no idea what it is. A man in a brass-buttoned coat is circling the stall.",
            listOf(
                choice("buy", "Buy it before he does.", hours(-45), RunEffect.GainCard(CardPool(rarity = Rarity.RARE))),
                choice("follow", "Follow the man in the brass buttons.", RunEffect.Fight("engine_on_the_navigli"), standing(Faction.CONVERGENCE, -1)),
                choice("leave", "Let it go."),
            ),
        ),
        EventDef(
            "milan.plague_cart", "The Plague Cart", "A Cart at the Lazzaretto Gate", EventKind.DILEMMA,
            "A cart of the sick is stuck at the gate of the Lazzaretto. The guards won't touch it. A plague doctor in " +
                "a beaked mask asks for a hand, and a priest asks you to stand well back.",
            listOf(
                choice("help", "Help push it through.", RunEffect.LoseHp(5), standing(Faction.ORDER, 1), RunEffect.GainCard(CardPool(rarity = Rarity.UNCOMMON))),
                choice("burn", "Burn the bedding before it spreads.", hours(30), standing(Faction.CONVERGENCE, 1)),
                choice("back", "Stand back, as the priest asks."),
            ),
        ),
        EventDef(
            "milan.dukes_banquet", "The Duke's Banquet", "Music from the Castello", EventKind.ENCOUNTER,
            "Ludovico Sforza is feasting the French ambassador at the Castello, and Leonardo's stage machinery has turned " +
                "the hall into the heavens. Nobody checks who you are if you carry a tray.",
            listOf(
                choice("silver", "Pocket the silver.", hours(60), standing(Faction.ORDER, -1), flag("milan.silver_taken")),
                choice("masque", "Stay for the masque.", RunEffect.HealPct(15)),
                choice("listen", "Listen to the ambassador.", RunEffect.Omens(1), flag("milan.french_plans")),
            ),
            consequence = "milan.after_the_banquet",
        ),
        EventDef(
            "milan.after_the_banquet", "After the Banquet", "Torches in the Courtyard", EventKind.ENCOUNTER,
            "The banquet is over and the Castello's guard is searching the courtyard for someone who was not on the " +
                "list. They have a description. It is close enough.",
            listOf(
                choice("fight", "Stand your ground.", RunEffect.Fight("sforza_guard")),
                choice("bribe", "Pay the sergeant.", hours(-30)),
                choice("run", "Run for the moat.", RunEffect.LoseHp(6)),
            ),
        ),
        EventDef(
            "milan.refectory_scaffold", "The Refectory Scaffold", "A Ladder in Santa Maria delle Grazie", EventKind.ENCOUNTER,
            "In the refectory of Santa Maria delle Grazie a painter is up a scaffold, staring at a wall where a supper " +
                "is half-finished. The ladder under him is rotten. He hasn't noticed.",
            listOf(
                choice("steady", "Steady the ladder.", RunEffect.Pick(Purpose.INSCRIBE)),
                choice("touch", "Touch the wet plaster.", paradox(1), RunEffect.GainCard(CardPool(rarity = Rarity.RARE))),
                choice("leave", "Let him work."),
            ),
        ),
        EventDef(
            "milan.letter_of_credit", "A Letter of Credit", "A Banker's Seal", EventKind.ENCOUNTER,
            "A Medici agent offers you a letter of credit drawn on a bank that won't open for another century. It is " +
                "good today. The interest is paid in something other than florins.",
            listOf(
                choice("sign", "Sign it.", hours(80), paradox(2)),
                choice("small", "Take a small advance.", hours(40), paradox(1)),
                choice("refuse", "Refuse."),
            ),
        ),
        EventDef(
            "anomaly.painted_window", "The Painted Window", "A Fresco That Moves", EventKind.ANOMALY,
            "A fresco on a chapel wall shows tomorrow: the same street, the same light, and you in it, doing something " +
                "you haven't decided to do yet.",
            listOf(
                choice("study", "Study it.", RunEffect.Omens(2), paradox(1)),
                choice("away", "Look away.", paradox(-1)),
            ),
        ),
    )

    private fun milanRipples(): List<RippleDef> = listOf(
        RippleDef(
            "clockwork_lineage", "Clockwork Lineage",
            "Convergence cards appear twice as often in card rewards, and Convergence enemies start each fight with Might 1.",
            mods = CombatMods(enemyBoosts = listOf(EnemyBoost(StatusType.MIGHT, 1, faction = Faction.CONVERGENCE))),
            perks = setOf(Perk.CONVERGENCE_REWARDS),
        ),
        RippleDef("perpetual_motion", "Perpetual Motion", "Your Reservoir cap is 8.", mods = CombatMods(reservoirCap = 8)),
        RippleDef(
            "relic_knight", "The Relic Knight", "Order enemies start each fight with Plate 1, and Relic cards cost 25% less.",
            mods = CombatMods(enemyBoosts = listOf(EnemyBoost(StatusType.PLATE, 1, faction = Faction.ORDER))),
            perks = setOf(Perk.RELIC_DISCOUNT),
        ),
        RippleDef("canonized", "Canonized", "Order Standing +1, and +1 Omen.", now = listOf(RunEffect.Standing(Faction.ORDER, 1), RunEffect.Omens(1))),
        RippleDef(
            "margins_in_the_machine", "Margins in the Machine", "+1 Paradox, and a rare card.",
            now = listOf(RunEffect.Paradox(1), RunEffect.GainCard(CardPool(rarity = Rarity.RARE))),
        ),
        RippleDef(
            "unwritten_knight", "The Unwritten Knight", "Errata enemies have 10% less HP this run.",
            mods = CombatMods(enemyHpPct = mapOf(Faction.ERRATA to 90)),
        ),
        RippleDef(
            "hollow_knight", "The Hollow Knight", "Era natives, the Automa Cavaliere among them, have 10% less HP this run.",
            mods = CombatMods(enemyHpPct = mapOf(Faction.ERA to 90)),
        ),
        RippleDef("history_holds", "History Holds", "−1 Paradox, and heal 15% of your max HP.", now = listOf(RunEffect.Paradox(-1), RunEffect.HealPct(15))),
    )

    // ---- The Salt Waste, 1191 (Act III) ---------------------------------------------------------------------------

    private fun saltWasteEvents(): List<EventDef> = listOf(
        EventDef(
            "saltwaste.shadow_of_the_gnomon", "The Shadow of the Gnomon", "A Needle of Black Glass", EventKind.DIVERGENCE,
            "A needle of black glass stands in the salt, taller than a lance, and its shadow points the wrong way. Brother " +
                "Aurel is a day's ride behind you. The Gnomon hums, and every faction that will ever exist is listening.",
            listOf(
                choice("aurel", "Let Aurel find it, as written.", standing(Faction.ORDER, 1), flag("saltwaste.aurel_finds"), ripples = listOf("canon_begins", "long_shadow")),
                choice("touch", "Touch it yourself.", paradox(2), flag("saltwaste.you_touched"), ripples = listOf("two_shadows", "needle_knows_you")),
                choice(
                    "shatter", "Let it shatter into a thousand shards.", standing(Faction.ERRATA, 1), standing(Faction.ORDER, -1),
                    flag("saltwaste.shattered"), ripples = listOf("thousand_shards", "everyones_time"),
                ),
                choice(
                    "take", "Crate it for the Convergence.", standing(Faction.CONVERGENCE, 1), standing(Faction.ORDER, -1),
                    flag("saltwaste.gnomon_taken"), ripples = listOf("matter_moves", "calculated_shadow"),
                ),
            ),
        ),
        EventDef(
            "saltwaste.aurels_vigil", "Brother Aurel's Vigil", "A Fire in the Dark", EventKind.ENCOUNTER,
            "A young monk in a crusader's surcoat sits alone by a fire, saying the night office from memory. Brother " +
                "Aurel. He has dreamed of a needle of black glass, and he asks if you have seen it.",
            listOf(
                choice("vigil", "Keep the vigil with him.", RunEffect.HealPct(15), standing(Faction.ORDER, 1), flag("saltwaste.aurel_befriended")),
                choice("warn", "Tell him what the needle becomes.", paradox(2), RunEffect.Omens(1), flag("saltwaste.aurel_warned")),
                choice("leave", "Leave him to his prayers."),
            ),
            consequence = "saltwaste.aurel_alone",
        ),
        EventDef(
            "saltwaste.aurel_alone", "Aurel, Alone", "A Riderless Horse", EventKind.ENCOUNTER,
            "Aurel went on ahead without you. You find his horse first, then him, in the lee of a dune with an arrow in " +
                "his shoulder, still praying.",
            listOf(
                choice("tend", "Tend the wound.", RunEffect.LoseHp(4), standing(Faction.ORDER, 1), RunEffect.GainCard(CardPool(rarity = Rarity.UNCOMMON))),
                choice("hunt", "Hunt the archers.", RunEffect.Fight("horse_archers")),
                choice("leave", "Leave him to be found, as written."),
            ),
        ),
        EventDef(
            "saltwaste.salt_caravan", "The Salt Caravan", "Bells across the Flat", EventKind.ENCOUNTER,
            "A caravan is crossing the salt by night, camel bells muffled with rags. Its guards are nervous: something " +
                "made of salt has been following them since the last well.",
            listOf(
                choice("trade", "Trade for what they carry.", hours(-40), RunEffect.GainCard(CardPool(rarity = Rarity.RARE))),
                choice("guard", "Ride with their guards.", RunEffect.Fight("salt_wraiths", hoursMult = 2)),
                choice("pass", "Let the bells pass."),
            ),
        ),
        EventDef(
            "saltwaste.well", "A Well in the Waste", "A Stone Ring in the Salt", EventKind.ENCOUNTER,
            "A ring of stones in the salt, and water far below. A patrol from the other side of the war is watering " +
                "its horses. They have seen you, and they haven't drawn.",
            listOf(
                choice("drink", "Drink, and fill your skins.", RunEffect.HealPct(25)),
                choice("share", "Share the water with the patrol.", RunEffect.HealPct(10), RunEffect.Omens(1)),
                choice("deep", "Climb down into the dark.", paradox(1), RunEffect.GainArtifact()),
            ),
        ),
        EventDef(
            "saltwaste.glass_field", "The Glass Field", "Black Glass in the Sand", EventKind.ENCOUNTER,
            "The sand here is studded with black glass, sharp as obsidian and warm to the touch. Each shard shows a " +
                "different sky.",
            listOf(
                choice("shard", "Pocket a shard.", RunEffect.GainArtifact(), paradox(2)),
                choice("map", "Map which way the shards point.", RunEffect.Omens(2)),
                choice("leave", "Walk around it."),
            ),
        ),
        EventDef(
            "saltwaste.truce", "A Parley under Truce", "Two Banners at a Well", EventKind.DILEMMA,
            "A crusader knight and an Ayyubid emir are talking terms under a truce, each with one squire. Someone in the " +
                "dunes has a crossbow on both of them.",
            listOf(
                choice("stop", "Stop the crossbowman.", RunEffect.LoseHp(6), RunEffect.GainCard(CardPool(rarity = Rarity.UNCOMMON)), flag("saltwaste.truce_kept")),
                choice("shout", "Shout a warning.", RunEffect.Omens(1)),
                choice("stay", "Stay out of it.", paradox(1), flag("saltwaste.truce_broken")),
            ),
        ),
        EventDef(
            "anomaly.sundial", "The Sundial Runs Backward", "A Shadow Turning the Wrong Way", EventKind.ANOMALY,
            "Every shadow on the dunes swings back toward the morning, to the moment your last fight began. You could " +
                "live it again, and this time keep twice what you earn.",
            listOf(
                choice("relive", "Relive it.", paradox(1), RunEffect.Fight(null, hoursMult = 2)),
                choice("step_out", "Step out of the shadow."),
            ),
        ),
        EventDef(
            "anomaly.salt_mirror", "The Salt Mirror", "A Flat of Mirror-Salt", EventKind.ANOMALY,
            "The salt flat is a mirror. In it you stand a little to the left of where you are, holding a card you never took.",
            listOf(
                choice("reach", "Reach into the mirror.", paradox(2), RunEffect.GainCard(CardPool(rarity = Rarity.RARE))),
                choice("break", "Break it.", paradox(-1), RunEffect.LoseHp(5)),
            ),
        ),
    )

    private fun saltWasteRipples(): List<RippleDef> = listOf(
        RippleDef("canon_begins", "The Canon Begins", "Order Standing +1, and heal 20% of your max HP.", now = listOf(RunEffect.Standing(Faction.ORDER, 1), RunEffect.HealPct(20))),
        RippleDef("long_shadow", "The Long Shadow", "Fixed intents deal 15% less damage to you.", mods = CombatMods(fixedDamagePct = 85)),
        RippleDef(
            "two_shadows", "Two Shadows", "+2 Omens, and your Foresee effects see 1 more card.",
            now = listOf(RunEffect.Omens(2)), mods = CombatMods(foreseeBonus = 1),
        ),
        RippleDef(
            "needle_knows_you", "The Needle Knows You", "Start each fight with 6 Block.",
            mods = CombatMods(startEffects = listOf(Effect.Block(Amount(6)))),
        ),
        RippleDef(
            "thousand_shards", "A Thousand Shards", "−2 Paradox, and a rare card.",
            now = listOf(RunEffect.Paradox(-2), RunEffect.GainCard(CardPool(rarity = Rarity.RARE))),
        ),
        RippleDef(
            "everyones_time", "Everyone's Time", "+1 Neutral energy at every Dawn.",
            mods = CombatMods(extraIncome = mapOf(EnergyColor.NEUTRAL to 1)),
        ),
        RippleDef(
            "matter_moves", "Matter Moves", "Convergence cards appear twice as often in card rewards, and Convergence enemies have 10% less HP.",
            mods = CombatMods(enemyHpPct = mapOf(Faction.CONVERGENCE to 90)), perks = setOf(Perk.CONVERGENCE_REWARDS),
        ),
        RippleDef(
            "calculated_shadow", "Calculated Shadow", "Start of each fight: Foresee 2.",
            mods = CombatMods(startEffects = listOf(Effect.Foresee(2))),
        ),
    )

    // ---- Boss Artifacts (docs/05 "Boss Artifacts"): strong, each with a drawback ------------------------------------

    private fun bossArtifacts(): List<ArtifactDef> = listOf(
        ArtifactDef(
            "aurelians_seal", "Aurelian's Seal", "+1 Faith energy at every Dawn. You can't Borrow.", 0,
            mods = CombatMods(extraIncome = mapOf(EnergyColor.FAITH to 1), borrowLimit = 0), boss = true,
        ),
        ArtifactDef(
            "meridian_shard", "MERIDIAN Shard", "Your Foresee effects see 1 more card. Enemies that read your Present start each fight with Might 1.", 0,
            mods = CombatMods(foreseeBonus = 1, enemyBoosts = listOf(EnemyBoost(StatusType.MIGHT, 1, predictiveOnly = true))), boss = true,
        ),
        ArtifactDef(
            "thousandfold_ribbon", "The Thousandfold's Ribbon",
            "Your Other Hand holds 1 more card (no Other Hand? you gain one that holds 2). Start each fight with +1 Paradox.", 0,
            mods = CombatMods(otherHandBonus = 1, startEffects = listOf(Effect.GainParadox(1))), boss = true,
        ),
        ArtifactDef(
            "babbages_governor", "Babbage's Governor", "Draw 1 more card at every Dawn. −6 max HP.", 0,
            mods = CombatMods(drawBonus = 1), onGain = listOf(RunEffect.MaxHp(-6)), boss = true,
        ),
        ArtifactDef(
            "cavaliere_heart", "The Cavaliere's Heart", "Start each fight with 8 Block and Plate 1. Enemies have 10% more HP.", 0,
            mods = CombatMods(startEffects = listOf(Effect.Block(Amount(8)), Effect.GainPlate(1)), hpPct = 110), boss = true,
        ),
        ArtifactDef(
            "joachims_prophecy", "Joachim's Prophecy",
            "+1 Omen now and at the start of each act, and each Omen also reveals a second Moment of the next step. Start each fight with +1 Paradox.", 0,
            mods = CombatMods(startEffects = listOf(Effect.GainParadox(1))),
            perks = setOf(Perk.OMENS_PER_ACT, Perk.OMEN_EXTRA_PEEK), onGain = listOf(RunEffect.Omens(1)), boss = true,
        ),
    )

    // ---- Imprints (docs/05 "Imprints", docs/03 "Sample Imprints") ---------------------------------------------------

    private fun imprints(): List<ImprintDef> = listOf(
        // Universal
        ImprintDef("thick_skin", "Thick Skin", "+8 max HP.", onGain = listOf(RunEffect.MaxHp(8)), repeatable = true),
        ImprintDef("quick_study", "Quick Study", "Card rewards offer 4 cards instead of 3.", perks = setOf(Perk.QUICK_STUDY)),
        ImprintDef(
            "pathfinder", "Pathfinder", "+1 Omen now and at the start of each act.",
            perks = setOf(Perk.OMENS_PER_ACT), onGain = listOf(RunEffect.Omens(1)),
        ),
        ImprintDef("anchorite", "Anchorite", "Your first Rewind each act leaves your Anchor in place.", perks = setOf(Perk.ANCHORITE)),
        ImprintDef("long_credit", "Long Credit", "You may Borrow 3 a turn instead of 2.", mods = CombatMods(borrowLimit = 3)),
        ImprintDef("paradox_sink", "Paradox Sink", "−2 Paradox at the start of each act, instead of −1.", perks = setOf(Perk.PARADOX_SINK)),
        ImprintDef("tempered", "Tempered", "Start each fight with 5 Block.", mods = CombatMods(startEffects = listOf(Effect.Block(Amount(5))))),
        ImprintDef("second_wind", "Second Wind", "Heal 4 HP after each fight you win.", perks = setOf(Perk.HEAL_AFTER_FIGHT)),
        // The Vowknight
        ImprintDef(
            "steadfast", "Steadfast", "Start each fight with Plate 1.", operative = "vowknight",
            requires = ImprintReq(4, faction = Faction.ORDER), mods = CombatMods(startEffects = listOf(Effect.GainPlate(1))),
        ),
        ImprintDef("oathbound", "Oathbound", "Your Strike deals 2 more damage.", operative = "vowknight", mods = CombatMods(signatureBonus = 2)),
        ImprintDef(
            "keeper_of_vows", "Keeper of Vows", "The first Vow you break each fight costs no Penance.", operative = "vowknight",
            requires = ImprintReq(2, trait = DeckTrait.VOW), mods = CombatMods(firstPenanceFree = true),
        ),
        ImprintDef(
            "long_memory", "Long Memory", "Remember also counts the cards in your Erased.", operative = "vowknight",
            requires = ImprintReq(3, trait = DeckTrait.REMEMBER), mods = CombatMods(rememberErased = true),
        ),
        // The Oracle
        ImprintDef(
            "precomputed", "Precomputed", "Forecast becomes Foresee 3.", operative = "oracle",
            requires = ImprintReq(4, faction = Faction.CONVERGENCE), mods = CombatMods(signatureBonus = 1),
        ),
        ImprintDef(
            "deep_forecast", "Deep Forecast", "Start of each fight: Foresee 2.", operative = "oracle",
            mods = CombatMods(startEffects = listOf(Effect.Foresee(2))),
        ),
        ImprintDef(
            "cache_hit", "Cache Hit", "Your first Subroutine each fight costs 1 less.", operative = "oracle",
            requires = ImprintReq(2, trait = DeckTrait.SUBROUTINE), mods = CombatMods(firstSubroutineDiscount = 1),
        ),
        ImprintDef(
            "bayesian_prior", "Bayesian Prior", "The first Known card you play each turn draws 1 card.", operative = "oracle",
            requires = ImprintReq(3, trait = DeckTrait.CALCULATED), mods = CombatMods(knownDraw = 1),
        ),
        // The Splinter
        ImprintDef(
            "margin_notes", "Margin Notes", "Your Other Hand holds 4 cards.", operative = "splinter",
            requires = ImprintReq(4, faction = Faction.ERRATA), mods = CombatMods(otherHandBonus = 1),
        ),
        ImprintDef(
            "unstable_equilibrium", "Unstable Equilibrium", "You Unravel at 12 Paradox instead of 10.", operative = "splinter",
            requires = ImprintReq(3, trait = DeckTrait.PARADOX), mods = CombatMods(unravelAt = 12),
        ),
        ImprintDef(
            "doubled_breath", "Doubled Breath", "Start each fight with 1 Flux energy and draw 1 card.", operative = "splinter",
            mods = CombatMods(startEffects = listOf(Effect.GainEnergy(EnergyColor.FLUX, Amount(1)), Effect.Draw(1))),
        ),
    )

    // ---- Artifacts found from Act II on --------------------------------------------------------------------------

    private fun laterArtifacts(): List<ArtifactDef> = listOf(
        ArtifactDef(
            "vitruvian_compass", "Vitruvian Compass", "Start of each fight: gain Plate 2.", 150,
            mods = CombatMods(startEffects = listOf(Effect.GainPlate(2))),
        ),
        ArtifactDef("desert_waterskin", "Desert Water-skin", "Heal 4 HP after each fight you win.", 130, perks = setOf(Perk.HEAL_AFTER_FIGHT)),
        ArtifactDef(
            "crusaders_reliquary", "Crusader's Reliquary", "Start of each fight: gain 6 Block.", 140,
            mods = CombatMods(startEffects = listOf(Effect.Block(Amount(6)))),
        ),
    )
}
