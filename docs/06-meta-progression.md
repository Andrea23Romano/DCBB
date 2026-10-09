# 06: Meta-Progression

The game remembers more than your unlocks. **Every run is a branch that persists.** Your past runs become places in the setting, enemies, and stories.

The guiding rule is **progression adds options and story, not raw power**. That answers the "grind" critique leveled at DC and its peers. It also keeps difficulty honest: a win at Entropy 10 means the same thing on day 2 as on day 200.

All meta state is stored locally. The game is offline-first.

---

## The Still Hour

The Still Hour is the hub (lore in [02](02-world-and-lore.md#the-still-hour)). Between runs you can:

- choose a **mission** (from the Chronoscape) and an **operative**
- talk to the residents: **Sister Ysolde**, **KESTREL**, **Wren & Wren**, and **the Archivist**
- browse the **Archive**, **Canonize** variants and branches, and read the **Codex**

### Conversations

There are two layers of conversation, and only the first can carry the plot:

- **Authored beats.** These carry the meta-story ([below](#meta-story-delivery)). They unlock by milestone, for example *"first Defection"*, *"first loss to the Tear"* or *"won as all three factions"*.
- **Callbacks.** These are GenAI, P1 ([07](07-genai-design.md#hub-callbacks)). A short remark of at most 40 words, appended after an authored beat, that references your recent runs. For example: *Ysolde: "You let the boy touch the Gnomon. Even after you left us. …Thank you."*

---

## The Chronoscape

The Chronoscape is a persistent map of the war: a long strip of the Weave with eras woven in bands, and **Nexus Points** marked as knots. The MVP has 1 Nexus; launch targets 6–8.

| Nexus state | Meaning |
|---|---|
| **Order / Convergence / Errata** | Anchored by that faction. Weight flows its way. |
| **Contested** | Never won, or recently lost |
| **Torn** | Movement III only. The Tear has ripped it open. |

- **Winning a mission** anchors its Nexus for the faction you served at the end of the run. If you Defected, you choose which of your two factions claims it.
- **What control does:**
  - On a Nexus your faction controls, you start that mission with +1 Standing with your faction.
  - If another faction controls it, its squads are more common there (+1 combat per act).
  - Changes in control unlock **authored story beats** and new missions.
- **Torn Nexus Points** (late game) offer Tear-heavy mission variants with unique rewards. Mending one restores it to Contested.
- **After a faction ending**, the Chronoscape drifts back to Contested over the next runs, one Nexus at a time. The war resumes, as it always does ([02](02-world-and-lore.md#endings)).
- **After the Shuttle ending**, the Chronoscape gains a **Balance** view showing how evenly the factions hold the Weave.
  - "**Where the Weave needs you**" highlights the faction that holds the fewest Nexus Points.
  - Runs played for that faction earn +50% Faction Rank XP and a Shuttle-thread cosmetic.
  - No faction may ever win, and now the player is the one keeping it that way.

---

## Archive and Echoes

### The Archive

Every finished run, win or loss, is filed as a **branch record**:

```yaml
branch: "0x3F"
date: 2026-10-07
operative: vowknight
factions: [order, errata]        # defected
mission: the_first_hour
outcome: victory                 # or defeat_act2, etc.
deck: [...]                      # final deck, including Branch Variants
artifacts: [...]
ledger_summary: { flags: [...], divergences: [...], paradox_peak: 7, rewinds: 1 }
chronicle: { title: "...", text: "...", source: generated | template }
```

- You can browse it in the hub. Each record shows the **Run Chronicle** ([07](07-genai-design.md#run-chronicle)), the final deck, a timeline of key choices, and the Divergences you created.
- **Canonize** up to **12** branches as favorites. Canonized branches are kept forever. Others rotate out after 100 records.

### Echoes from past runs

Your past branches come back to haunt you.

- **Frequency.** In later runs there is a 1-in-3 chance per act that one Elite Moment is an **Echo** of a past branch. Canonized and recent branches are weighted more heavily.
- **How it fights.** It plays that branch's deck. Cards are mapped to intents deterministically, as described in [03](03-factions-and-operatives.md#faction-squads).
- **Its voice.** It speaks with lines generated from that branch's ledger (P1): *"I burned the notes. You hesitated."*
- **Rewards.** Defeat it to **inherit** one card from its deck, including its Branch Variants.
- Echoes created by Rewind *within* a run are covered in [05](05-run-structure.md#anchors-and-rewind).

---

## Variant Pool

Branch Variants that you forged during a run ([08](08-card-dsl.md#the-forge)) can outlive it.

- **Canonizing.** At the end of a run, choose which forged variants to **Canonize** into your personal **Variant Pool**. Each faction holds up to **30**, and you can retire variants at any time.
- **Reappearing.** Canonized variants can appear as card rewards in future runs. They arrive already forked, with both faces, in a "Variant" frame.
- **Unique to you.** Over time your card pool becomes unlike anyone else's.
- **No model needed.** Variants are stored as **data**, the source card plus its VariantPlan, so they work with GenAI off and on devices without a model.
- **Share codes** (post-launch idea). A short code would encode a variant's plan. Re-creating it is deterministic, so friends could trade variants without any server.

---

## Unlocks

| What | How |
|---|---|
| **MVP operatives** (Vowknight, Oracle, Splinter) | Available from the start, so all three factions are playable on day one |
| **Faction Rank 1–10** | XP earned with that faction's operatives, which also accrues on losses |
| Cards added to pools | Ranks 2, 4, 6, 8: new cards join the faction pool. Options, not upgrades. |
| Artifacts added to pools | Ranks 3 and 7 |
| Operative Imprints added | Rank 5 |
| Cosmetics | Every rank: card backs, frames, tabard and Proxy colors, Errata collage patterns |
| **Later operatives** | One at Faction Rank 5 and one at a meta-story milestone, per faction |
| **Hybrid operatives** | Win a mission after Defecting between their two factions, in either direction. Example: Order → Convergence or Convergence → Order unlocks the **Machine Saint**. |
| **Missions and eras** | Chronoscape progress and meta-story movements |
| **Entropy levels** | Win at the previous level (per operative) |

**There are no permanent stat upgrades** at all: no +HP and no extra starting Hours. Quality-of-life unlocks are allowed, such as Codex filters or seeing what a Glimpse would have shown.

---

## Entropy

Entropy is the difficulty ladder. Levels **0–20** are tracked per operative, and each level adds its modifier to all the ones below it.

| Lvl | Modifier |
|---|---|
| 1 | +1 Elite per act |
| 2 | Enemies have +5% HP |
| 3 | −1 starting Foresight |
| 4 | Antiquarian prices +15% |
| 5 | Still Points heal 20% (instead of 30%) |
| 6 | Act bosses gain a second Fixed move |
| 7 | Paradox thresholds −1 (Frayed 3, Unstable 6, Unravel 9) |
| 8 | Returning Moments always come back in their harshest form |
| 9 | Start each run with a *Loose Thread* hazard card in your deck |
| 10 | One Anchor per **run** instead of per act |
| 11 | Elites gain +1 Might |
| 12 | Card rewards offer 2 cards (a Glimpse still adds 3) |
| 13 | Elite signature moves are Fixed |
| 14 | Max HP −5% |
| 15 | Rarity pity grows by +1% instead of +2% |
| 16 | Rewind costs 2 more Paradox |
| 17 | Enemies have +10% more HP (+15% total) |
| 18 | Reservoir cap 4 |
| 19 | Nexus bosses gain a third phase |
| 20 | **The Tear Widens**: start at 3 Paradox; Tear Moments appear even when Stable |

---

## Fixed Points

Fixed Points are seeded challenge runs. DC's weekly challenges inspired them.

| Mode | Seed | Operative | Mutators | Attempts |
|---|---|---|---|---|
| **Daily Fixed Point** | Same for everyone that day | Rotates daily | 1 | The first attempt counts |
| **Weekly Fixed Point** | Same for everyone that week | Your choice | 3 | Unlimited; your best counts |

- **Fairness.** Mechanics must be identical for everyone, so in Fixed Points:
  - the **Select** pattern (Ripples, Returning Moments) uses its seeded fallback
  - the **Forge** uses its deterministic procedural plans
  - narration may vary, because it is flavor only, or can come from the pre-baked pool ([07](07-genai-design.md#determinism-and-fairness))
- **Scoring:** victory, remaining HP, total turns, and peak Paradox (lower is better). Scores are local first. Optional online leaderboards come later ([10](10-roadmap.md#open-questions)).
- **Example mutators:**

| Mutator | Effect |
|---|---|
| **Misprint Week** | Every card rolls a Misprint face when drawn |
| **Still Waters** | No Borrow, but the Reservoir cap is 10 |
| **Fast Forward** | All intents −1 countdown (min 1); your Scheduled effects also resolve 1 sooner |
| **Canon Law** | Vows cost 0, and breaking one costs double Penance |
| **Open Seams** | Every Shrine is an Errata Seam |
| **Calculated Risk** | Every card you draw is Known |
| **The Tear Widens** | Entropy 20's modifier at any level |
| **Glass Cannon** | Max HP −40%, damage +40% |

---

## Codex

The Codex is the game's encyclopedia. Entries unlock as you encounter things:

- cards, including your Variants
- enemies, Artifacts, eras and Nexus Points
- characters and factions
- keywords

**Branch Codex** (GenAI, P2). Every Divergence you create gets an alternate-history encyclopedia entry, *"Branch 0x3F: where the notes burned"* ([07](07-genai-design.md#branch-codex-entries)).

---

## Meta-Story Delivery

The three movements are described in [02](02-world-and-lore.md#the-meta-story-three-movements).

| Movement | Triggered by | Delivered through |
|---|---|---|
| **I: The War** | First runs | Mission intros, hub beats, the Archivist's questions |
| **II: Three Fractures** | Per faction: hub beats at that faction's Rank 3 and 6, then a **revelation mission** at Rank 9 (*The Last Hour*, *The Halting Problem*, *Every Version of Us*) | That faction's hub voice (Ysolde, KESTREL, Wren & Wren), the revelation mission, and its Nexus boss's new behavior |
| **III: No Right Thread** | All three Fractures complete *and* the hybrid operatives unlocked | The Archivist's reveal, Torn Nexus Points, the Tear's chorus, final missions, endings |
| **After the Shuttle ending** | The true ending | The Chronoscape's Balance view, and new hub beats acknowledging the endless war |

- **Hybrids fit the arcs.** Hybrids unlock by Defecting ([Unlocks](#unlocks)), so a player usually meets the Fractures while crossing between factions. Seeing a faction from the inside, then leaving it, is the point.
- **Rule:** every story-critical beat is **authored**. GenAI adds personal color *around* the beats, such as callbacks, chronicles and Echo lines. It never decides or reveals plot, and a spoiler guard stops it hinting at twists the player hasn't reached ([07](07-genai-design.md#validation-layers)).

---

## Anti-Grind Principles

1. **Unlocks add choices, not stats.**
2. **Every run pays.** Faction Rank XP accrues on losses too, and Quick Missions count.
3. **No FOMO.** There are no login rewards, streaks or timed currencies. Fixed Points are optional fun, and missing one costs nothing.
4. **Mastery is the progression.** Entropy, hybrids and the Variant Pool deepen the game for experts without walling off newcomers.
