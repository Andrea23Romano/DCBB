# 01: Vision

## Elevator Pitch

> **Anachronist** (working title) is a roguelike deckbuilder for Android about a war over history.
>
> - **The war.** Fight for a medieval order of time-walking knights, for a machine mind from 2049, or for the **Errata**, people from timelines that were erased.
> - **The verbs.** Bend time itself: see when every blow will land, delay doom at a price, schedule your strikes, rewind your mistakes, and fork your cards into versions from other histories.
> - **The Chronicler.** A small AI running entirely on your phone writes the history you're changing, and the game is complete without it.

It is **"Dawncaster, but better"**: a love letter that keeps what makes Dawncaster (DC) great, and pushes harder on originality, consequence and replayability.

## The Fantasy

You are an **Anachronist**: someone whose own timeline faded away, which makes you the one person who can walk through every century.

- In London you burn a notebook, and in Milan the machines pray.
- You rewind a disastrous afternoon, and the version of you that you abandoned comes looking for you in the desert of 1191.
- Every card you hold could have been something else in another history, and sometimes it becomes that.

The core feeling: **"I changed history, and the game noticed."**

---

## Design Pillars

### 1. Time is the mechanic

Time manipulation is the core verb at every layer, not a theme skin.

- **In combat:** enemy intents sit on a visible **Track** with countdown clocks. You can Delay them, at the price of **Pressure**, and **Schedule** your own effects onto the same Track ([04](04-combat.md#the-track)).
- **On the map:** you can see branches ahead with Foresight. The futures you didn't choose can **come back changed**. **Anchors** let you rewind, and your abandoned self lingers as an **Echo** ([05](05-run-structure.md#anchors-and-rewind)).
- **In the meta-game:** your runs are branches that persist ([06](06-meta-progression.md#archive-and-echoes)).

### 2. Every choice branches

- The **Causality Ledger** carries your choices forward across eras. **Divergence** events change history and apply **Ripples** to the rest of the run.
- Past runs return as **Echoes**, Chronicles and Codex entries.
- Consequence is the reward.

### 3. Deep but readable

- DC-level build depth: colored energy, carryover, talents, multi-faction builds.
- Mobile-first clarity: portrait, one thumb, no timers.
- **Long-press explains everything.**
- Under 25 keywords at launch, introduced progressively ([04](04-combat.md#mobile-combat-ux)).
- TalkBack works from day one.

### 4. The AI is the Chronicler, not the dealer

- Generative AI personalizes **words**: scenes, chronicles, voices and variant names. It also **chooses among legal options** and **plans card variants** that the engine checks against a power budget.
- Rules, balance and fairness stay deterministic and human-designed.
- The game is fully playable offline, and with the AI switched off ([07](07-genai-design.md#principles)).

### 5. Respect the player

- A premium one-time price.
- No ads, no pay-to-win, no timers, no FOMO.
- Unlocks add *options*, not stats ([06](06-meta-progression.md#anti-grind-principles)).
- Sessions fit a commute (Quick Mission, about 15 minutes) and survive interruption, because you can suspend anywhere.

---

## Dawncaster: What We Keep, What We Change

**What DC gets right:**

- A huge pool of hand-illustrated cards: 900+ on mobile, 1,100+ in the 2026 Steam release.
- A colored energy system that **carries over between turns** (capped at 8).
- Cards built around a **basic attack**.
- Talents gained on level-up.
- Exploration by picking **1 of 3 cards** from an event deck.
- A premium, no-pay-to-win model.
- An engaged accessibility community.

**What reviews criticize:** the most common critiques are a familiar formula, and heavy reliance on luck in rewards.

| Dawncaster | Anachronist |
|---|---|
| Colored energy carries over (cap 8) | **Reservoir** carryover (cap 6), plus **Borrow** up to 2 from next turn (**Debt**). Energy moves both ways in time. |
| Basic-attack interplay | One **Signature Action** per operative (Strike, Forecast, Shift), modified by cards, Relics and Artifacts |
| Talents on level-up | **Imprints**, with faction and deck requirements that make builds intentional |
| Pick 1 of 3 event cards | The **Timestream**: 3 Moments, **Foresight** to peek ahead, **Returning Moments** (skipped branches come back changed), and **Anchors** with Rewind |
| Weapons and enchantments | **Relics** and other Constants (3 slots), **Artifacts** (out-of-place historical objects), **Inscribe** upgrades |
| Multi-color classes | Faction operatives plus one mid-run **Defection** that adds a second color, and a story to go with it |
| Static event text | Designer-authored events narrated by the **Chronicler**, with **Causality Ledger** callbacks across eras |
| Enemies act every turn | The **Track**: visible countdowns, Delay with **Pressure**, **Fixed** boss moves, **Scheduled** effects, faction-specific intents (Fixed, Predictive, Forked) |
| Luck-heavy rewards | Foresight previews, **Glimpse** the alternate reward (for Paradox), rarity pity, or **Fork** a card you already own |
| A fixed card pool | A **Variant Pool**: your forged Branch Variants can carry over, so your card pool becomes unique |
| Weekly challenges | **Fixed Points**, daily and weekly, with deterministic mechanics |
| n/a | **Paradox**, a run-wide push-your-luck meter that ties rewinding, glimpsing and defecting together |

---

## Target Players

- **DC, Slay the Spire and Monster Train players** on mobile who want depth that fits a portrait screen.
- **Narrative roguelike fans** who love a story that grows across runs (the Hades effect).
- **Premium mobile buyers** who avoid free-to-play.
- **Blind and low-vision players.** DC has an active community there, and we design TalkBack support in from the start.

## Platform and Sessions

- **Android phones**, played in portrait. Tablets are supported. Fully offline.
- **Session lengths:**
  - full mission: about 40–45 minutes
  - Quick Mission: about 15 minutes
  - suspend and resume anywhere, even mid-combat
- **Later:** iOS and PC are possible ([10](10-roadmap.md#open-questions)).

## Business Model

Free demo plus a one-time unlock, with optional expansions and cosmetic supporter packs. On-device AI means **no per-player server costs** ([10](10-roadmap.md#business-model)).

---

## Art Direction

- **Hand-painted illustration** in the tradition DC is praised for.
- **Faction visual languages:**
  - **Order:** illuminated-manuscript borders, gold leaf, stained-glass light, the sundial-cross
  - **Convergence:** white porcelain geometry, cyan light, gold circuitry; brass and clockwork in older eras
  - **Errata:** a collage of eras, torn paper, risograph misprint offsets, margin doodles, the caret ‸
  - **The Hush:** negative space, literal cut-outs in the paint
- **Era palettes:** London fog and soot · Milan fresco ochre · Salt Waste white salt and black glass.
- **Branch Variants** reuse the original art with **shader treatments** (double exposure, chromatic offset, misprinted registration) and a variant frame. **No generated art.**
- **Energy pips are shaped:** ☀ Faith, ⬡ Compute, ◎ Flux, ○ Neutral. Color is never the only signal.

## Audio Direction

| Faction | Musical identity |
|---|---|
| **Order** | Plainchant, bells, organ drones |
| **Convergence** | Clockwork ticks and music-box arpeggios that become synth arpeggios in later eras |
| **Errata** | Tape loops, detuned strings, a chorus of overlapping voices |
| **The Hush** | Removal. The mix ducks, and silence is the sound. |

- **Adaptive layers** add era instrumentation.
- During enemy phases, **the Track ticks** as a rhythmic motif.

## Accessibility

- TalkBack semantics, announcements and a logical focus order, plus a "describe the board" explainer ([07](07-genai-design.md#archivist-explainer)).
- Shaped energy pips, a colorblind-safe palette and a high-contrast mode.
- Scalable text, reduced motion, optional haptics, and a mirrored layout for left-handed play.
- No timed inputs, ever.

## Title Ideas

| Title | Note |
|---|---|
| **Anachronist** | Working title. The player's role. |
| Branchfall | |
| The Unbroken Line | |
| Errata | |
| Hourfall | |
| Gnomon | |

All of them need a trademark search before anything is final.

---

## References

**Dawncaster: what we keep**

- Mechanics (carryover colored energy, cap 8, draw 5, cards by color, basic-attack cards): [Review (In An Age)](https://inanage.com/2025/06/02/review-dawncaster/) · [148Apps review](https://www.148apps.com/dawncaster/dawncaster-review/)
- Exploration by picking 1 of 3 event cards, and talents on level-up: [Level Winner beginner's guide](https://www.levelwinner.com/dawncaster-beginners-guide-tips-tricks-strategies/)
- Store listings: [Google Play](https://play.google.com/store/apps/details?id=games.WanderlostInteractive.Dawncaster) · [Steam release coverage (NoobFeed)](https://www.noobfeed.com/games/dawncaster-the-rpg-cardventure)

**Dawncaster: reception**

- [Pocket Gamer review](https://www.pocketgamer.com/dawncaster/review/)
- [Vaporlens summary of Steam reviews](https://vaporlens.app/app/3966890/dawncaster_the_rpg_cardventure.md)

**Dawncaster: accessibility community**

- [AppleVis](https://www.applevis.com/apps/ios/games/dawncaster-deckbuilding-rpg)
- [AFB AccessWorld review](https://afb.org/aw/spring2026/dawncaster-review)
