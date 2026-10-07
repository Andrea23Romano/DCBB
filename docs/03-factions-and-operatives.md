# 03: Factions and Operatives

Each faction is defined by four things:

- an **energy color**
- a **temporal zone** it bends
- a **keyword family**
- an **enemy behavior** that you meet when you fight it

**Operatives** are the playable classes. The MVP ships **one operative per faction**. Later operatives and three **hybrid** operatives (unlocked through Defection) expand each faction.

Lore for each faction is in [02](02-world-and-lore.md#the-factions). Rules for every keyword used here are in [04](04-combat.md#keywords).

---

## Faction Identities

| | **The Order** | **The Convergence** | **The Errata** |
|---|---|---|---|
| Energy | Faith ☀ (gold) | Compute ⬡ (cyan) | Flux ◎ (violet) |
| Temporal zone | **The Past** (discard pile) | **The Future** (draw pile) | **The Present** (hand) |
| Fantasy | Unbreakable conviction; the past as a weapon | Perfect information; engines that scale | Being two places at once; fortune as a tool |
| Keyword family | Vow, Relic, Remember, Litany, Martyr | Calculated, Iterate, Subroutine, Compile | Fork, Shift, Paradox, Misprint, Bleed-through |
| Strengths | Sustain, Block, big payoffs for setup | Consistency, card quality, late-combat scaling | Flexibility, burst, adapting mid-turn |
| Weaknesses | Inflexible (Vows); reshuffles reset Remember | Slow starts; Glitch and Disrupt hurt | Variance; Paradox can Unravel the run |
| As enemies | Fixed charging hits, Plate | Predictive intents, Disrupt | Forked intents, Doubles |

**Natural matchups.** Faith steadies Chaos, Chaos breaks Prediction, and Prediction outmaneuvers Faith. In practice:

- the Vowknight handles Errata enemies well
- the Splinter confuses Convergence enemies
- the Oracle plans around the Order's Fixed hits

## How to Read the Card Tables

- **Cost:** `1F+1` means one Faith pip plus one generic pip. F is Faith, C is Compute, X is Flux. Full rules in [04](04-combat.md#costs-and-paying).
- **Pts:** the card's budget points against its target. Every sample card is within the ±10% window. The formula is in [08](08-card-dsl.md#primitives-and-point-costs).
- **Rarity:** St (starter), C (common), U (uncommon), R (rare), L (legendary).

---

## The Vowknight

*Order operative. "The vows are the only thing that never Faded."*

| HP | Income | Signature Action | Constants focus |
|---|---|---|---|
| 75 | 2F + 1N | **Strike**: deal 4 damage | Vows and Relics |

**How they play.**

- The Vowknight takes **Vows** early. Vows are self-imposed restrictions that buff the Strike.
- They sequence cards for **Litany**, and they let the Past fill up so **Remember** cards hit hard.
- The Vowknight is the most forgiving operative: high HP, plenty of Block, and a free attack every turn.
- **How they lose:** breaking Vows at the wrong time, or reshuffling just before a Remember payoff.

### Starter deck (10)

| Card | ×  | Type | Cost | Rarity | Text | Pts |
|---|---|---|---|---|---|---|
| Line Strike | 4 | Attack | 1 | St | Deal 5. Attuned: +2. | 12 / 12 |
| Shield of the Line | 4 | Skill | 1 | St | Gain 4 Block. Attuned: +2 Block. | 12 / 12 |
| Vow of the Sword | 1 | Constant (Vow) | 1F | St | Vow: play at most 1 Skill per turn. While kept, your Strike deals +4. | 11.2 / 12 |
| Remembered Blow | 1 | Attack | 1F+1 | St | Deal 9. Remember 3 Attacks: +5. | 24 / 24 |

### Sample card pool

| Card | Type | Cost | Rarity | Text | Pts |
|---|---|---|---|---|---|
| Hold Fast | Skill | 1F | C | Gain 4 Block. Litany (Skill): gain Plate 1. | 12.6 / 12 |
| Sundial Cut | Attack | 1F | C | Deal 5. Remember 2 Skills: apply Weak 1. | 12.4 / 12 |
| Crusader's Charge | Attack | 2F | C | Deal 10. Litany (Attack): +4. | 24.8 / 24 |
| Litany of Steel | Skill | 1 | U | Gain 4 Block. Litany (Attack): draw 1. | 13.2 / 13.2 |
| Gnomon Blade | Constant (Relic) | 1F+1 | U | Your Strike deals +3 and gives 2 Block. | 25.9 / 26.4 |
| Oathkeeper's Stand | Skill | 2F | U | Gain 9 Block. If you have an unbroken Vow: gain Plate 2. | 27.6 / 26.4 |
| Smite the Heretic | Attack | 1F+1 | U | Deal 10. Double damage against Errata and Tear enemies. | 28 / 26.4 |
| Recite the Canon | Skill | 1F | U | Recall 1. Remember 5: Recall 2 instead. Retain. | 13.2 / 13.2 |
| Vow of Silence | Constant (Vow) | 1F | R | Vow: never Borrow or Delay. While kept: at Dawn, gain 1 Faith. | 16 / 15 |
| Verdict of the Line | Attack | 2F+1 | L | Deal 18, +1 per card in your Past (max +14). Erase. | 49 / 50.4 |

> **Design note:** *Hold Fast* rewards back-to-back Skills, while *Vow of the Sword* forbids them. The best Vowknight decks are the ones that resolve that tension, for example by choosing *Vow of Silence* instead.

### Sample Imprints

Imprints are level-up passives ([05](05-run-structure.md#imprints)).

| Imprint | Requirement | Effect |
|---|---|---|
| Steadfast | 4+ Order cards | Start each combat with Plate 1. |
| Keeper of Vows | 2+ Vows in deck | The first Vow you break each combat causes no Penance. |
| Long Memory | 3+ Remember cards | Remember also counts cards in the Erased. |

---

## The Oracle

*Convergence operative. "MERIDIAN kept me. I'm still deciding whether to thank it."*

| HP | Income | Signature Action | Constants focus |
|---|---|---|---|
| 65 | 2C + 1N | **Forecast**: Foresee 2 | Subroutines |

**How they play.**

- **Forecast** every turn to make your next draws **Known**. Known cards fuel **Calculated** bonuses.
- Install **Subroutines** that work every turn without you.
- **Iterate** cards grow stronger each time you play them.
- The Oracle is the precision operative: it rarely gets surprised and gets stronger as a fight goes on.
- **How they lose:** a slow first turn against a fast elite, or enemies that Glitch and Disrupt.

### Starter deck (10)

| Card | × | Type | Cost | Rarity | Text | Pts |
|---|---|---|---|---|---|---|
| Pulse | 4 | Attack | 1 | St | Deal 5. Attuned: +2. | 12 / 12 |
| Firewall | 4 | Skill | 1 | St | Gain 4 Block. Attuned: +2 Block. | 12 / 12 |
| Probability Lance | 1 | Attack | 1C | St | Deal 4. Calculated: +4. | 12.8 / 12 |
| Watchdog.exe | 1 | Constant (Subroutine) | 1C | St | Dusk: gain 2 Block. | 11.5 / 12 |

### Sample card pool

| Card | Type | Cost | Rarity | Text | Pts |
|---|---|---|---|---|---|
| Recursive Strike | Attack | 1 | C | Deal 3. Iterate +2. | 12 / 12 |
| Predictive Shield | Skill | 1C | C | Gain 4 Block. Foresee 1. | 11.6 / 12 |
| Scatter Protocol | Attack | 1C | C | Deal 3 to all enemies. Calculated: Foresee 2. | 11.4 / 12 |
| Deterministic Model | Skill | 1C+1 | U | Foresee 3. Draw 2. Calculated: gain 1 Compute. | 24 / 26.4 |
| Lance.exe | Constant (Subroutine) | 2C | U | Dusk: deal 6 to the weakest enemy. | 27.4 / 26.4 |
| Rollback | Skill | 1C | U | Restore the HP you lost during the last enemy phase (max 12). Erase. | 13 / 13.2 |
| Hold Pattern | Skill | 1C | U | Delay 1. Calculated: draw 1. | 13.6 / 13.2 |
| Optimal Path | Skill | 1C | R | Foresee 3. Put one of them into your Present. | 14 / 15 |
| Monte Carlo | Attack | 1C+1 | R | Deal 3 to a random enemy 5 times. Calculated: target the weakest enemy instead. | 27.3 / 30 |
| Proof of MERIDIAN | Constant (Subroutine) | 2C+1 | L | Dawn: gain 1 Compute per Known card in your Present (max 3). Dusk: Foresee 1. | 48 / 50.4 |

### Sample Imprints

| Imprint | Requirement | Effect |
|---|---|---|
| Precomputed | 4+ Convergence cards | Forecast becomes Foresee 3. |
| Cache Hit | 2+ Subroutines | Your first Subroutine each combat costs 1 less. |
| Bayesian Prior | 3+ Calculated cards | The first Known card you play each turn draws 1 card. |

---

## The Splinter

*Errata operative. "We split when our world Faded. Now there are two of us, and we only have one body's worth of luck."*

| HP | Income | Signature Action | Constants focus |
|---|---|---|---|
| 62 | 2X + 1N | **Shift**: swap 1 card between your Present and your Other Hand | Engines that feed on Shift and Paradox |

**Special rule: the Other Hand.**

- Your Other Self holds **3 face-up cards**.
- At Dawn, after your draw, the Other Hand refills to 3 from your Future.
- You can't play cards from the Other Hand, and it is **not discarded at Dusk**.
- **Shift** moves cards between the two hands.
- So the Other Hand is a stash that keeps cards between turns, and a menu of options.

**How they play.**

- Stash key cards in the Other Hand until the right moment.
- **Fork** cards to choose between faces.
- **Misprint** and **Bleed-through** bring in power from other branches.
- Spend **Paradox** for spikes, while watching the Unravel line.
- The Splinter is the highest-skill operative and its turns have the highest ceiling. It also humiliates Predictive enemies by changing its hand after they read it.
- **How they lose:** greed with Paradox (Unravel), and a low HP pool.

### Starter deck (10)

| Card | × | Type | Cost | Rarity | Text | Pts |
|---|---|---|---|---|---|---|
| Splinter Cut | 4 | Attack | 1 | St | Deal 5. Attuned: +2. | 12 / 12 |
| Double Guard | 4 | Skill | 1 | St | Gain 4 Block. Attuned: +2 Block. | 12 / 12 |
| Split the Moment | 1 | Skill | 1X | St | Fork a card in your Present. Draw 1. | 12 / 12 |
| Two Places at Once | 1 | Fork (Attack / Skill) | 1 | St | A: Deal 5. B: Gain 4 Block. | 12 / 12 |

### Sample card pool

| Card | Type | Cost | Rarity | Text | Pts |
|---|---|---|---|---|---|
| Borrowed Self | Skill | 0 | C | Shift 2. Gain 1 Paradox. | 6 / 6 |
| Fray Lash | Attack | 1X | C | Deal 3, +1 per Paradox (max +6). | 12 / 12 |
| Caret Strike | Attack | 1 | C | Deal 4. If you Shifted this turn: +3. | 11.6 / 12 |
| Crosstalk | Skill | 1X | U | Bleed-through 2. Gain 1 Paradox. | 14 / 13.2 |
| Misprinted Edict | Attack | 1X+1 | U | Misprint. A: Deal 14. B: Deal 7 to all enemies. C: Gain 10 Block, draw 1. | 26.3 / 26.4 |
| Paradox Engine | Constant | 1X | U | Dawn: gain 1 Flux and 2 Paradox. | 14.4 / 13.2 |
| Elsewhen Guard | Skill | 1X | U | Gain 4 Block. Shift 1. | 13.6 / 13.2 |
| Unwrite | Skill | 1X | R | Spend 2 Paradox. Remove an enemy intent from the Track (not Fixed). Draw 1. | 14 / 15 |
| Double Exposure | Fork (Attack / Skill) | 1X+1 | R | A: Deal 7 twice. B: Gain 11 Block. Shift 1. | 32.4 / 30 |
| The Thousand Doors | Constant | 2X+1 | L | Whenever you Shift a card into your Present, Fork it. The first card you Shift in each turn costs 1 less. | 45.6 / 50.4 |

### Sample Imprints

| Imprint | Requirement | Effect |
|---|---|---|
| Margin Notes | 4+ Errata cards | Your Other Hand holds 4 cards. |
| Unstable Equilibrium | 3+ Paradox cards | Unravel happens at 12 Paradox instead of 10. |
| Plural (legendary) | 2+ Fork cards | Once per turn, when you play a Fork card, also resolve its other face at half value. |

---

## Neutral and Era Cards

- **Neutral cards** can appear for anyone.
- **Era cards** join reward and shop pools only while you are in that era, so every era tastes different.
- Both use universal keywords only.

| Card | Pool | Type | Cost | Rarity | Text | Pts |
|---|---|---|---|---|---|---|
| Hold the Hour | Neutral | Skill | 1 | C | Delay 1. Foresee 1. | 12 / 12 |
| Stitch in Time | Neutral | Skill | 1 | C | Gain 3 Block. Schedule 2: gain 3 Block. | 13 / 12 |
| Observe | Neutral | Skill | 0 | C | Observe (collapse a Forked intent). Foresee 1. | 6 / 6 |
| Gaslight Ambush | London 1843 | Attack | 1 | C | Deal 4 to all enemies. | 12 / 12 |
| Punchcard Program | London 1843 | Skill | 1 | U | Foresee 2. Schedule 2: draw 2. | 13.6 / 13.2 |
| Vitruvian Guard | Milan 1495 | Skill | 1 | C | Gain 4 Block, +1 per Constant you have (max +3). | 12 / 12 |
| Codex Sketch | Milan 1495 | Skill | 1 | U | Choose a card in your Present: it gains Retain and costs 1 less this turn. | 12 / 13.2 |
| Mirage Step | Salt Waste 1191 | Skill | 0 | C | Gain 1 Block. Shift 1. | 6.4 / 6 |
| Salt Ward | Salt Waste 1191 | Skill | 2 | U | Gain 9 Block. Observe. | 25.6 / 26.4 |

---

## Later Operatives

These come after the MVP. Each one may add **at most two** new keywords, and the glossary is reviewed with every expansion.

| Operative | Faction | Fantasy | Signature | Focus |
|---|---|---|---|---|
| Inquisitor | Order | The Order's judge | **Judgement**: deal 2 damage per Litany triggered this turn | Litany chains, Smite |
| Penitent | Order | Pain as prayer | **Scourge**: lose 2 HP, gain 1 Faith | Martyr, HP as a resource |
| Proxy | Convergence | A swarm-mind body | **Deploy**: install a Drone token (Subroutine: Dusk, deal 2) | Subroutines, Compile |
| Overclocker | Convergence | Runs hot | **Overclock**: gain 1 Compute and 1 Debt | Borrow up to 4, a Heat track |
| Misprint | Errata | A living typo | **Reroll**: re-roll the face of a Misprint card in your Present | Paradox, Misprint |
| Scavenger | Errata | Steals from other branches | **Salvage**: Bleed-through 1 | Bleed-through, stealing enemy intents |

### Hybrid operatives

Hybrids are unlocked by **Defecting** ([06](06-meta-progression.md#unlocks)). Their income is 1 of each of their two colors plus 1 Neutral, with an extra draw.

| Operative | Factions | Fantasy | Signature | Focus |
|---|---|---|---|---|
| Machine Saint | Order + Convergence | A Proxy that found faith | **Vigil.exe**: Foresee 1 and gain 2 Block | *Liturgies*: Vows that are also Subroutines |
| Heretic | Order + Errata | An excommunicated knight who loves the branches | **Recant**: break a Vow without Penance, then Fork a card | Forked Vows: choose the restriction when played |
| Rogue Process | Convergence + Errata | A pruned fork of MERIDIAN | **Fork the Forecast**: Foresee 2, then Shift 1 | Misprinted Subroutines |

---

## Enemy Rosters

Enemy stats are prototype starting points. Intents use the vocabulary in [04](04-combat.md#enemies-and-intents).

### Era natives

Era natives can be anyone who lived in that era, on any side of its conflicts. In the Salt Waste, for example, that includes crusader and Ayyubid patrols as well as the stranger things the Fray leaves behind. They fight intruders from other times, whoever they are, and the game doesn't take sides.

| Enemy | Era | HP | Intents (rotation) | Notes |
|---|---|---|---|---|
| Gaslight Footpad | London 1843 | 20 | Attack 6 ① → Attack 3 + Weak 1 ① | Often in pairs |
| Rookery Brawler | London 1843 | 28 | Attack 9 ① → **Charge** Attack 18 ② | Teaches Delay and Pressure |
| Lamplighter | London 1843 | 22 | Attack 4 + Burn 2 ① | |
| Condottiero | Milan 1495 | 32 | Guard 6 → Attack 8 → Charge Attack 16 ② | |
| Plague Doctor | Milan 1495 | 24 | Burn 3 ① → Attack 5 + Exposed 1 ① | |
| Sforza Crossbowman | Milan 1495 | 20 | Charge Attack 14 ② → Attack 5 ① | Kill it before it fires |
| Salt Wraith | Salt Waste 1191 | 22 | Unpick: Blank a card ① → Attack 7 ① | Tear-touched |
| Sandglass Golem | Salt Waste 1191 | 40 | Plate 2 ① → Charge Attack 10 ② | |
| Mirage | Salt Waste 1191 | 18 | Forked: Attack 9 / Guard 9 ① | Errata-touched |

### Faction squads

Squads appear in any era, with era skins: a Brass Proxy is clockwork in 1495 and brass in 1843.

| Enemy | Faction | HP | Behavior |
|---|---|---|---|
| Squire of the Line | Order | 24 | Attack 6 ① ↔ Guard 5 ① |
| Sergeant-at-Arms | Order | 34 | Plate 2 (first turn) → Attack 8 ① |
| Penitent | Order | 26 | Martyr: gains Might 1 when it loses HP · Attack 5 ×2 ① |
| **Inquisitor** (elite) | Order | 48 | Strike 7 ① every round + **Purge 30 ③ (Fixed)**, which recharges |
| Brass Proxy | Convergence | 26 | **Predictive**: more Attacks than Skills in your Present → Guard 8 + Attack 6; otherwise Attack 10 |
| Calculating Engine | Convergence | 30 | Glitch 2 ① → Attack 7 ① → Guard 7 ① |
| Drone Swarm | Convergence | 3 × 8 | Each drone: Attack 3 ① |
| **Pruner** (elite) | Convergence | 52 | **Disrupt** (Delay your Scheduled items 1) · **Prune** (Erase the top card of your Future) · Charge Attack 12 ② |
| Misprint | Errata | 21 | **Forked**: Attack 12 / Weak 2 ① |
| Double | Errata | 24 | Copies the last card you played as its intent (damage → Attack, Block → Guard) |
| **Seam-runner** (elite) | Errata | 44 | Untargetable every other round ("in the Margins") · Forked: Attack 15 / Steal a card from your Present (returned on death) |
| Loose End | Tear | 12 | Unpick: Blank a card in your Present ① |
| Ravel | Tear | 30 | Unpick: Erase the top card of your Future ① → Attack 8 ① |
| **The Rent** (elite) | Tear | 60 | Snip: your Constants don't trigger next turn · Charge Attack 14 ② · Blank 2 |
| Echo of You | (yours) | 60% of your max HP | Plays your deck (from a Rewind or from the Archive). Cards are mapped to intents deterministically: Attacks → Attack, Skills → Guard or Buff, Constants → passives. |

### Bosses

| Boss | Where | HP | Mechanics | Fixed signature move |
|---|---|---|---|---|
| **The Lattice Engine** | Act I, London 1843 | 120 | Predictive. Installs "Subroutines" on itself (Guard 5 per round, then Might +1 per round, ...). | **Cascade** ③: Attack 6 × installed Subroutines |
| **The Automa Cavaliere** | Act II, Milan 1495 | 150 | Its heart depends on your Act II Divergence choice ([02](02-world-and-lore.md#the-mvp-mission-the-first-hour)): MERIDIAN's escapement → Predictive + Glitch; Order relic → its own Vow (+Might when you break the pattern it forbids); Errata misprint → Forked intents. | **Joust** ③: Attack 28 |
| **Grand Master Aurelian** | Nexus (Order champion) | 200 | Liturgical cycle of Fixed hits. Gains Might when you play 3+ Attacks in a turn. Phase 2, *Gnomon's Shadow*: Disrupts your Scheduled items, and all his intents become Fixed. | **Canon Unbroken** ④: Attack 40 |
| **MERIDIAN Ascendant** | Nexus (Convergence champion) | 180 | Predictive twice per round, Subroutines, Disrupt. Phase 2, *Optimal Line*: each round it copies the most-played card in your deck as its intent. | **Convergence** ③: Erase 2 cards from your Present, then Attack 20 |
| **The Thousandfold** | Nexus (Errata champion) | 170 | Two Forked intents per round. Summons Doubles. Phase 2, *Thousand Selves*: every 2 rounds she swaps HP with a Double, so you must find the right self. | **Chorus** ③: Attack 4 × (Doubles + 1) to you, Weak 2 |

**Which champion you face.**

- The Nexus boss comes from a faction you **don't belong to**.
- Without a Defection, two factions qualify, and you face the one where your **Standing is lowest** at the end of Act III.
- Ties go to the faction the Causality Ledger touched most ([05](05-run-structure.md#causality-ledger)).
- After a Defection, only one faction is left, and its champion comes for the Nexus.
