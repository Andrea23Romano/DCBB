package dcbb.core.content

import dcbb.core.model.CombatMods
import dcbb.core.model.Effect
import dcbb.core.model.EnemyBoost
import dcbb.core.model.Faction
import dcbb.core.model.Rarity
import dcbb.core.model.StatusType
import dcbb.core.model.Tgt
import dcbb.core.run.ArtifactDef
import dcbb.core.run.CardPool
import dcbb.core.run.ChoiceDef
import dcbb.core.run.EraDef
import dcbb.core.run.EraFight
import dcbb.core.run.EventDef
import dcbb.core.run.EventKind
import dcbb.core.run.MomentType
import dcbb.core.run.Perk
import dcbb.core.run.Purpose
import dcbb.core.run.Requirement
import dcbb.core.run.RippleDef
import dcbb.core.run.RunContent
import dcbb.core.run.RunEffect

/**
 * The First Hour (docs/02 "The MVP Mission"), Act I: London, 1843. The run-layer content of the first playable slice:
 * the era's Era Deck, its events and Divergence, the Artifacts the engine supports so far, and the Ripples of
 * *The Notes*. Event prose is the authored fallback text (docs/05 "How events are built").
 */
object FirstHour {

    val run: RunContent by lazy {
        RunContent(
            content = Prototype.content,
            eras = listOf(london()),
            events = events() + shrines(),
            artifacts = artifacts(),
            ripples = ripples(),
            shrines = mapOf(
                Faction.ORDER to "shrine.order_chapel",
                Faction.CONVERGENCE to "shrine.convergence_terminal",
                Faction.ERRATA to "shrine.errata_seam",
            ),
        )
    }

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
        composition = linkedMapOf(
            MomentType.COMBAT to 10,
            MomentType.ELITE to 2,
            MomentType.EVENT to 5,
            MomentType.ANOMALY to 1,
            MomentType.ANTIQUARIAN to 2,
            MomentType.STILL_POINT to 2,
            MomentType.SHRINE to 1,
            MomentType.CACHE to 1,
        ),
        titles = mapOf(
            MomentType.ANTIQUARIAN to listOf("A Curiosity Shop on Holywell Street", "The Pawnbroker of Seven Dials", "A Bookseller off Paternoster Row"),
            MomentType.STILL_POINT to listOf("A Quiet Pew at St Bride's", "A Coaching Inn at Dusk", "The Reading Room after Hours"),
            MomentType.CACHE to listOf("A Loose Floorboard", "An Unclaimed Parcel", "A Strongbox in the Thames Mud"),
        ),
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
                choice("read", "Read it first.", paradox(2), RunEffect.Foresight(1), flag("read_forbidden_page")),
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
                choice("buy", "Buy every number in print.", hours(-25), RunEffect.Foresight(1)),
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
                choice("patrols", "Pay her for the patrols.", hours(-15), RunEffect.Foresight(1)),
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
                choice("read", "Read the ending.", RunEffect.Foresight(1), paradox(1)),
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
                choice("give", "Give her money for a room.", hours(-20), RunEffect.Foresight(1), standing(Faction.ERRATA, 1)),
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
                choice("foresight", "Download a forecast.", RunEffect.Foresight(2), requires = Requirement.OWN_FACTION),
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
            "+1 Foresight per act. Foresight reveals a second Moment of the next step.", 130,
            perks = setOf(Perk.FORESIGHT_EXTRA_PEEK), onGain = listOf(RunEffect.Foresight(1)),
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
            "signal_loop", "Signal Loop", "+1 Foresight per act, and +1 Paradox.",
            now = listOf(RunEffect.Foresight(1), RunEffect.Paradox(1)),
        ),
        RippleDef("as_written", "As Written", "Order Standing +1.", now = listOf(RunEffect.Standing(Faction.ORDER, 1))),
        RippleDef(
            "tear_notices", "The Tear Notices", "+1 Paradox, and your next Cache holds more.",
            now = listOf(RunEffect.Paradox(1)),
        ),
    )
}
