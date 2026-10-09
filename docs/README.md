# Anachronist: Design Documents

> **Status:** Phase 1, combat prototype ([status](10-roadmap.md#phase-1-status)). The rules engine, a text client and the balance simulator are in the repository; everything else is design. Implementation follows the [roadmap](10-roadmap.md).
> **Working title:** *Anachronist*. **Codename:** DCBB ("Dawncaster, but better").

**Anachronist** is a roguelike deckbuilder for Android about a war over history.

- Three factions fight across the centuries over **the Weave**, a fabric of time whose threads **branch** instead of breaking:
  - **the Order of the Unbroken Line**, medieval knights who walk through time by relic
  - **the Convergence**, an AI from 2049
  - **the Errata**, people from erased timelines
- You bend time in every layer of play:
  - **combat:** enemy intents on a visible Track that you can delay, at a price
  - **map:** skipped futures come back, and you can rewind to Anchors
  - **meta:** every run persists as a branch
- Every cut, purge and reckless fork widens **the Tear**, a rip in the Weave where every erased history ends up, and it wants back in.
- An on-device AI, **the Chronicler**, narrates the history you change. It never touches the rules, and the game is complete without it.

---

## Reading Order

| # | Document | What's inside |
|---|---|---|
| 01 | [Vision](01-vision.md) | Pitch, pillars, what we keep and change from Dawncaster, audience, art, audio, accessibility |
| 02 | [World and Lore](02-world-and-lore.md) | The laws of the Weave, the three factions and their twists, the Tear, the player's role, eras, Nexus Points, the MVP mission, the meta-story and endings, content principles |
| 03 | [Factions and Operatives](03-factions-and-operatives.md) | Faction identities, the 3 MVP operatives with starter decks and sample cards, later and hybrid operatives, enemy rosters, bosses |
| 04 | [Combat](04-combat.md) | Zones, rounds, energy (Reservoir, Borrow, Attuned), **the Track**, the 25 keywords, statuses, worked examples, mobile UX |
| 05 | [Run Structure](05-run-structure.md) | Missions, the Weft, Returning Moments, Anchors and Rewind, Paradox, rewards, Imprints, Artifacts, Defection, events, the Causality Ledger |
| 06 | [Meta-Progression](06-meta-progression.md) | The Still Hour, Chronoscape, Archive and Echoes, Variant Pool, unlocks, Entropy, Fixed Points, Codex |
| 07 | [Generative AI Design](07-genai-design.md) | The Chronicler: principles, patterns, feature specs, context packets, validation, controls, safety, evaluation |
| 08 | [Card DSL and Power Budget](08-card-dsl.md) | The card language, point costs, budgets, the validator, the Forge (generated Branch Variants) |
| 09 | [Technical Architecture](09-tech-architecture.md) | Stack decision, modules, determinism, LLM runtime, device tiers, model delivery, fine-tuning pipeline, testing |
| 10 | [Roadmap](10-roadmap.md) | Phases with exit checks, MVP and launch content, business model, risks, open questions |
| 11 | [Art Direction](11-art-direction.md) | Illuminated Weave: the rendering core, faction media, era palettes, the recurring cast, element specs, readability tests |
| 12 | [Art Pipeline](12-art-pipeline.md) | Making AI-assisted art with hosted tools: prompt blocks, golden set, finishing pass, QA, provenance, legal, the style-lock sprint, the MVP manifest |

**Short on time?** Read [01](01-vision.md), then the [Track](04-combat.md#the-track), [Anchors and Rewind](05-run-structure.md#anchors-and-rewind) and the [GenAI principles](07-genai-design.md#principles).

---

## Glossary

| Term | Meaning | See |
|---|---|---|
| **Accelerate N** | Keyword: reduce a Track item's countdown by N (minimum 1) | [04](04-combat.md#keywords) |
| **Act** | One of a mission's three parts (Root, Branch, Nexus), each in its own era | [05](05-run-structure.md#missions-and-acts) |
| **Afterimage** | Keyword: the effect repeats at half value next Dawn | [04](04-combat.md#keywords) |
| **Anachronist** | The player's role: someone unmoored from a Faded branch, who can walk any era | [02](02-world-and-lore.md#the-anachronist) |
| **Anchor** | A run-state snapshot (1 per act) that you can Rewind to | [05](05-run-structure.md#anchors-and-rewind) |
| **Antiquarian** | The shop | [05](05-run-structure.md#services) |
| **Archive** | Persistent record of every run (branch) you've played | [06](06-meta-progression.md#archive-and-echoes) |
| **Archivist** | The Still Hour's enigmatic keeper; also the P2 rules-explainer feature | [02](02-world-and-lore.md#the-still-hour), [07](07-genai-design.md#archivist-explainer) |
| **Artifact** | A run-long passive item: an out-of-place historical object | [05](05-run-structure.md#artifacts) |
| **Attuned** | Keyword: a bonus if the card's whole cost was paid in its faction's energy | [04](04-combat.md#attuned) |
| **Bleed-through N** | Keyword: add N random cards from another faction's pool to your Present | [04](04-combat.md#keywords) |
| **Block** | Keyword: prevents damage; yours expires at Dawn | [04](04-combat.md#keywords) |
| **Borrow / Debt** | Spend up to 2 energy you don't have, plus 1 interest the first time each turn. It is repaid from your next Dawn's income, even when that Dawn is in your next combat | [04](04-combat.md#reservoir-and-borrow) |
| **Branch** | A timeline: a thread of the Weave, split off by a Divergence | [02](02-world-and-lore.md#the-laws-of-the-weave) |
| **Branch Pool** | Where unchosen Moments wait to return | [05](05-run-structure.md#returning-moments) |
| **Branch Variant** | A permanent second face for a card, created by the Forge | [08](08-card-dsl.md#the-forge) |
| **Calculated** | Keyword: a bonus if the card is Known | [04](04-combat.md#keywords) |
| **Canon** | The Order's sacred history, read from scripture, prophecy and the Gnomon's visions; it runs to the Last Hour | [02](02-world-and-lore.md#the-order-of-the-unbroken-line) |
| **Canonize** | Keep a branch or a variant permanently in your Archive or Variant Pool | [06](06-meta-progression.md#variant-pool) |
| **Causality Ledger** | Structured memory of a run's choices: flags, counters, Standing, Divergences | [05](05-run-structure.md#causality-ledger) |
| **Chronicler** | The on-device generative AI system | [07](07-genai-design.md) |
| **Chronoscape** | Persistent meta-map of Nexus Points and faction control | [06](06-meta-progression.md#the-chronoscape) |
| **Compile** | Keyword: merge two cards into a temporary Program | [04](04-combat.md#keywords) |
| **Compose / Narrate / Select** | The three GenAI patterns in scope | [07](07-genai-design.md#the-four-patterns) |
| **Constant** | A persistent card type (Vow, Relic, Subroutine); 3 slots | [04](04-combat.md#zones) |
| **Convergence** | The AI faction (MERIDIAN). Energy: Compute ⬡. Zone: the Future. | [02](02-world-and-lore.md#the-convergence) |
| **Dawn / Dusk** | The start and end of your turn | [04](04-combat.md#round-structure) |
| **Defection** | Once per run, adopt a second faction at its Shrine | [05](05-run-structure.md#shrines-and-defection) |
| **Delay N** | Keyword: push a Track item later; enemy intents gain Pressure | [04](04-combat.md#delay-and-pressure) |
| **Divergence** | A change to the past that forks a branch; also an event type that creates a Ripple | [02](02-world-and-lore.md#the-laws-of-the-weave), [05](05-run-structure.md#divergence-events-and-ripples) |
| **Echo** | A persisting past self: from a Rewind in this run, or from an Archived run | [05](05-run-structure.md#anchors-and-rewind), [06](06-meta-progression.md#echoes-from-past-runs) |
| **Entropy** | Difficulty ladder, levels 0–20 | [06](06-meta-progression.md#entropy) |
| **Era / Era Deck** | A historical setting; the deck of Moments dealt during its act | [02](02-world-and-lore.md#eras), [05](05-run-structure.md#era-deck-composition) |
| **Erase / the Erased** | Keyword and zone: removed for the rest of combat | [04](04-combat.md#zones) |
| **Errata** | The third faction: the erased and the never-born. Energy: Flux ◎. Zone: the Present. | [02](02-world-and-lore.md#the-errata) |
| **Fade** | What happens to branches that lose Weight: they thin until the thread snaps and falls into the Tear | [02](02-world-and-lore.md#the-laws-of-the-weave) |
| **Fixed** | Keyword: can't be Delayed or Accelerated (bosses' signature moves) | [04](04-combat.md#fixed) |
| **Fixed Points** | Daily and weekly seeded challenge runs | [06](06-meta-progression.md#fixed-points) |
| **Forecast** | The Oracle's Signature Action (Foresee 2) | [04](04-combat.md#signature-actions) |
| **Foresee N** | Keyword: look at the top N cards of your Future; they become Known | [04](04-combat.md#keywords) |
| **Foresight** | A map resource: reveals Moments' details and one Moment ahead | [05](05-run-structure.md#foresight) |
| **Forge** | The pipeline that creates Branch Variants (LLM plan, engine validation) | [08](08-card-dsl.md#the-forge) |
| **Finishing pass** | The human correction, grading and cleanup every AI-assisted image goes through before approval | [12](12-art-pipeline.md#the-finishing-pass) |
| **Fork** | Keyword: a card with two faces; choose one when played | [04](04-combat.md#keywords) |
| **Forked intent** | An Errata enemy intent with two possible outcomes | [04](04-combat.md#faction-behavior) |
| **Fray** | The turbulence other people's Divergences tear open; the Errata travel through it | [02](02-world-and-lore.md#how-each-faction-travels) |
| **Future / Present / Past** | The draw pile, hand and discard pile | [04](04-combat.md#zones) |
| **Glimpse** | See the alternate card reward for +1 Paradox | [05](05-run-structure.md#options-on-every-card-reward-screen) |
| **Gnomon** | The Order's relic of time travel, found in 1191 | [02](02-world-and-lore.md#the-order-of-the-unbroken-line) |
| **Golden set** | The approved reference images every new image is generated and judged against | [12](12-art-pipeline.md#the-golden-set-and-character-sheets) |
| **Hours** | The currency | [05](05-run-structure.md#services) |
| **Illuminated Weave** | The art direction: painterly illustration on a woven ground, one medium per faction | [11](11-art-direction.md) |
| **Imprint** | A level-up passive, sometimes with deck requirements | [05](05-run-structure.md#imprints) |
| **Inscribe** | A permanent card upgrade (about +30%) | [05](05-run-structure.md#other-upgrades) |
| **Iterate +N** | Keyword: grows each time it's played this combat | [04](04-combat.md#keywords) |
| **Known** | Card state: seen via Foresee before being drawn | [04](04-combat.md#card-states) |
| **Last Hour** | The end of history foretold by the Canon. *Spoiler:* it is the Tear. | [02](02-world-and-lore.md#the-order-of-the-unbroken-line) |
| **Litany** | Keyword: a bonus if the previous card this turn was of a given kind | [04](04-combat.md#keywords) |
| **Lore Bible** | Structured, tagged lore snippets used by writers and the Chronicler | [07](07-genai-design.md#context-packets) |
| **Margins** | The Errata's refuges in the gaps between branches | [02](02-world-and-lore.md#the-errata) |
| **Martyr** | Keyword: triggers the first time you lose HP each enemy phase | [04](04-combat.md#keywords) |
| **Medium** | A faction's visual medium: manuscript (Order), porcelain engraving (Convergence), collage (Errata), rips (Tear) | [11](11-art-direction.md#faction-media) |
| **MERIDIAN** | The Convergence's AI mind, born in 2049 | [02](02-world-and-lore.md#the-convergence) |
| **Misprint** | Keyword: becomes one of its alternate faces at random when drawn; also the temporary faces from Fork and Unravel | [04](04-combat.md#keywords), [08](08-card-dsl.md#misprint-tables) |
| **Moment** | A card in the Weft: Combat, Elite, Event, Anomaly, Antiquarian, Still Point, Shrine, Cache | [05](05-run-structure.md#the-weft) |
| **Neutral** | White ○ energy from income; pays only generic pips | [04](04-combat.md#colors) |
| **Nexus Point** | A pivotal moment where Weight flows; every mission ends at one | [02](02-world-and-lore.md#nexus-points) |
| **Ninth Law** | The hidden law of the Weave: there is no right thread. *Spoiler:* revealed in Movement III. | [02](02-world-and-lore.md#the-laws-of-the-weave) |
| **Observe** | Collapse a Forked intent to its outcome early | [04](04-combat.md#faction-behavior) |
| **Operative** | A playable class (Vowknight, Oracle, Splinter, ...) | [03](03-factions-and-operatives.md) |
| **Order of the Unbroken Line** | The Knights Crusaders faction. Energy: Faith ☀. Zone: the Past. | [02](02-world-and-lore.md#the-order-of-the-unbroken-line) |
| **Other Hand** | The Splinter's 3-card stash held by its Other Self | [03](03-factions-and-operatives.md#the-splinter) |
| **Paradox** | Run-wide strain meter (0–10); at 10 the timeline Unravels | [05](05-run-structure.md#paradox) |
| **Penance** | The cost of breaking a Vow (lose 4 HP) | [04](04-combat.md#keywords) |
| **Predictive** | A Convergence enemy that reads your Present at Dawn to choose its intent | [04](04-combat.md#faction-behavior) |
| **Pressure** | +25% potency per Delay applied to an enemy intent | [04](04-combat.md#delay-and-pressure) |
| **Program** | The temporary card created by Compile | [04](04-combat.md#keywords) |
| **Proxy** | A body built for MERIDIAN in some era | [02](02-world-and-lore.md#the-convergence) |
| **Recall N** | Keyword: return N cards from your Past to your Present | [04](04-combat.md#keywords) |
| **Relic** | Keyword (Constant): equipment that modifies your Signature Action | [04](04-combat.md#keywords) |
| **Remember N** | Keyword: a bonus if your Past holds N cards of a kind | [04](04-combat.md#keywords) |
| **Reservoir** | Carried-over energy (cap 6) | [04](04-combat.md#reservoir-and-borrow) |
| **Retain** | Keyword: not discarded at Dusk | [04](04-combat.md#keywords) |
| **Returning Moment** | An unchosen Moment that comes back, changed | [05](05-run-structure.md#returning-moments) |
| **Rewind** | Return to your Anchor (map), or restore HP lost in the last enemy phase (card effect) | [05](05-run-structure.md#anchors-and-rewind) |
| **Ripple** | A run modifier created by a Divergence choice; one is selected from a legal set | [05](05-run-structure.md#divergence-events-and-ripples) |
| **Schedule N** | Keyword: place an effect on the Track; it resolves before enemies | [04](04-combat.md#scheduled-effects) |
| **Shift N** | Keyword: swap cards between your Present and your Other Hand | [04](04-combat.md#keywords) |
| **Shrine** | A faction altar: a boon, Desecration, or Defection | [05](05-run-structure.md#shrines-and-defection) |
| **Shuttle** | The Anachronist's true role, passing between the factions forever so none can win. *Spoiler:* the true ending. | [02](02-world-and-lore.md#endings) |
| **Signature Action** | An operative's free once-per-turn action (Strike, Forecast, Shift) | [04](04-combat.md#signature-actions) |
| **Standing** | Your relationship with each faction (−3 to +3) | [05](05-run-structure.md#standing) |
| **Still Hour** | The hub, outside time | [02](02-world-and-lore.md#the-still-hour) |
| **Still Point** | A rest Moment: heal, Inscribe, or Steady | [05](05-run-structure.md#services) |
| **Subroutine** | Keyword (Constant): an effect that runs every turn | [04](04-combat.md#keywords) |
| **Tear** | The antagonist force: a rip in the Weave where every cut-away history ends up, and it wants back in | [02](02-world-and-lore.md#the-tear) |
| **Track** | The shared timeline of enemy intents and your Scheduled effects | [04](04-combat.md#the-track) |
| **Unpick** | The Tear's intent: Blank or Erase the top card of your Future | [04](04-combat.md#intent-types) |
| **Unravel** | What happens at 10 Paradox: the Weave tears open around you | [05](05-run-structure.md#unravel) |
| **Unwoven** | The Tear's creatures: figures of snapped thread and absence | [02](02-world-and-lore.md#the-tear) |
| **VariantPlan** | The JSON plan the LLM writes for a Branch Variant | [08](08-card-dsl.md#variantplan-schema) |
| **Vow** | Keyword (Constant): a self-imposed restriction that grants a benefit while kept | [04](04-combat.md#keywords) |
| **Weave** | All of time: a fabric whose threads (branches) can split | [02](02-world-and-lore.md#the-laws-of-the-weave) |
| **Weft** | Map exploration: each step deals 3 Moments and you pick one | [05](05-run-structure.md#the-weft) |
| **Weight** | How "real" a branch is; the finite resource the war is fought over | [02](02-world-and-lore.md#the-laws-of-the-weave) |
