# 10: Roadmap

**Design first, then build in thin, testable slices.** Every phase ends with a concrete exit check, so the fun is proven before the ambitious parts (GenAI, fine-tuning, meta) get built on top of it.

---

## Phases

| Phase | Goal | Contents | Exit check |
|---|---|---|---|
| **0: Design** | This document set | Vision, lore, systems, GenAI, architecture | Reviewed; open questions triaged |
| **1: Combat prototype** | Prove the combat is fun | `core-rules` for combat; 3 operatives (starter decks plus about 40 cards); about 10 enemies; Track, Pressure, Reservoir and Borrow; a text or desktop debug UI; `sim` bots with first balance reports | Playtesters want "one more fight". Sims show no dominant degenerate line (Delay-lock, infinite Borrow). Average turn under 20 s. |
| **2: Android vertical slice** (no GenAI) | Prove the whole loop on a phone | The full MVP content below; Compose UI; saves and resume; progressive tutorial; **VFX spike** (card motion, Track animation, the branch shader); the art **style-lock sprint** and vertical-slice art ([12](12-art-pipeline.md#the-style-lock-sprint)) | The mission "The First Hour" is playable start to finish at 60 fps on a mid-range phone. The ADR-001 go/no-go on Compose game feel is made ([09](09-tech-architecture.md#stack-decision-adr-001)). |
| **3: GenAI P0** | Prove the Chronicler | `genai` and `llm-service`; LiteRT-LM with **off-the-shelf** Gemma 4 E2B (prompt-only); event narration, Run Chronicle, barks; queue, cache, fallbacks, Report flow; AI pack delivery | All [07](07-genai-design.md#evaluation) gates except faction voice. Fallback rate ≤ 10%. No frame drops from generation. |
| **4: Forge, fine-tuning and tiers** | Make it ours and make it scale down | The Forge pipeline; the `ml/` pipeline; multi-task LoRA for Tiers A and B; tier classification; the full eval suite | **All** 07 gates pass on the reference phones |
| **5: Meta and content** | Make it a game you keep playing | Chronoscape, Archive and Echoes, Variant Pool, Entropy, Fixed Points, Codex, the Movement I hub story; content grown toward launch targets; closed beta | Retention and difficulty curves are healthy in the beta. Crash-free sessions ≥ 99.5%. |
| **6: Launch and beyond** | Ship 1.0 | Launch content; then expansions (missions, operatives, hybrids, Movements II and III) | n/a |

### Phase 1 status

Phase 1 started in October 2026, alongside the art style work. The repository now holds:

- **`core-rules`:** the combat rules engine, a pure reducer with seeded RNG streams. It carries every sample card from [03](03-factions-and-operatives.md) (51 cards, each with a generated Inscribed version), 22 enemies in 27 encounters, the [08](08-card-dsl.md) budget calculator and rules-text renderer, the bots, and the test suite.
- **The run layer**, started early so play-tests cover whole runs: Act I of *The First Hour* ([05](05-run-structure.md#act-i-in-the-prototype)). It has the Weft and its dealing rules, HP, Paradox and Debt carried between fights, card rewards with Glimpse and Skip, the Antiquarian, Still Points, Caches, Shrines, six events including the Divergence *The Notes* and its Ripples, two Anomalies, nine Artifacts, the Paradox thresholds, and the Lattice Engine. Returning Moments, Rewind, Imprints, Defection and Acts II–III come later.
- **`cli`:** a playable text client.
- **`sim`:** the batch simulator, with a turn-planning bot, a greedy bot, a random floor, and a run bot. Its first baseline is [reports/sim-baseline.md](../reports/sim-baseline.md), and the current numbers are in [reports/sim-latest.md](../reports/sim-latest.md) for fights and [reports/run-latest.md](../reports/run-latest.md) for runs.
- **`web`:** a browser test client, published as a private page for play-testing during development. It plays runs and single fights. Runs and fights can be copied as replay codes and played back exactly in the simulator.

The first baseline tripped one exit check: Tear enemies that only Blank could soft-lock a fight. Two tuning passes fixed that, added interest to Borrow so it stops being an every-turn habit, and brought every encounter into its difficulty band. One thing stays open: the Vowknight is clearly the easiest operative, and a stronger bot confirmed that this is the design, not the bot. Runs show the same gap: with the run bot, the Vowknight clears Act I in nearly every run, the Oracle in about 60% and the Splinter in about half. Rebalancing is deliberately parked while the systems are built. See [reports/README.md](../reports/README.md#baseline-findings).

## MVP Content

This is the Phase 2 vertical slice, reused by Phases 3–4.

| Content | Count |
|---|---|
| Operatives | 3 (Vowknight, Oracle, Splinter) |
| Cards | ~120 (about 30 per faction plus about 30 neutral and era cards) |
| Enemies | ~25, including 4 elites |
| Bosses | 2 act bosses + 3 Nexus champions |
| Events | ~20, including 3 Divergences (one per act) |
| Artifacts | ~25, including 3 boss Artifacts |
| Imprints | ~24 (6 per faction plus 6 universal) |
| Missions / eras | 1 ("The First Hour") / 3 |

## Launch Targets (1.0)

| Content | Count |
|---|---|
| Operatives | 9 + 3 hybrids |
| Cards | ~320 |
| Enemies | ~70 |
| Missions / eras | 4–6 / 8 |
| Events | ~80 |
| Artifacts | ~70 |
| Story | Movements I–II complete; Movement III in the first major update, or at launch if scope allows |

---

## Business Model

- **Free to download, with a real demo:** Act I of *The First Hour* with the Vowknight. A **one-time purchase** unlocks the full game.
- **Optional paid expansions** add missions, operatives and eras. **Supporter packs** are cosmetic only.
- **No ads, no pay-to-win, no energy timers, no loot boxes.**
- **Why this works with GenAI.** Inference runs on the player's phone, so there are **no per-player server costs**. A one-time price is sustainable without subscriptions, and the game never needs to "phone home".

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| On-device inference too slow or hot on mid-range phones | Features feel laggy; battery complaints | Device tiers; prefetching in natural pauses; the 1.5 s rule with fallbacks; thermal step-down; Off mode is a complete game |
| Model download size (≈ 1.5–2 GB on Tier A) | Players skip it | Optional on-demand AI pack; Wi-Fi default; clear value preview; Tier B's smaller model |
| Generated text quality or consistency | Immersion breaks; lore drift | Fine-tuning on our Lore Bible; small, structured packets; validators; authored fallbacks; human-rated eval gates |
| Forge variants are unbalanced | Runs trivialized or ruined | One DSL and one budget; auto-tune; capped ops; at most 3 Forks per run; simulation checks against source cards |
| Player sentiment about GenAI and AI-assisted art | Review-bombing; distrust | Transparency (quill marks, a store disclosure of AI-assisted art); a strong, consistent art direction with a human finishing pass on every image; rules and story written by people; an Off switch for the Chronicler; no image generation at runtime |
| AI art drifts or looks generic | The game looks cheap or inconsistent | One primary generator; golden set and character sheets as references; fixed prompt blocks; QA scorecard; drift checks every ~20 assets ([12](12-art-pipeline.md)) |
| AI art ownership and tool terms | Weak IP protection; a tool changes its terms | Meaningful, recorded human finishing; human-made frames, icons and layouts; provenance log; saved copies of tool terms; every asset replaceable from its record |
| Complexity creep (Track, Borrow, Reservoir, Paradox, Fork…) | New players bounce | Keyword cap of 25; progressive disclosure across the first runs; long-press explanations everywhere; the Archivist explainer |
| Compose falls short on game feel | Product looks flat | VFX spike in Phase 2 with a go/no-go; Godot 4 as the documented fallback |
| Scope | Never ships | MVP cut lines above; meta systems deferred to Phase 5; Movement III can slip past 1.0 |
| Store policy and regional rules (AI content, rating, historical symbols) | Launch blocked or delayed in some markets | In-app Report flow from day one; no free-text input; rate the content we actually ship; restricted historical symbols on swappable art layers |
| Teacher-model terms or base-model licenses | Legal exposure | Verify terms before generating data; `ml/MODEL_LICENSES.md`; a model card per release |

---

## Open Questions

| # | Question | Notes |
|---|---|---|
| 1 | **Final title?** | Working title *Anachronist*. Alternatives in [01](01-vision.md#title-ideas). Needs a trademark check. |
| 2 | **Art production capacity** | The direction is set ([11](11-art-direction.md)) and art is AI-assisted ([12](12-art-pipeline.md)). Open: which hosted tool wins the style-lock sprint, who does the finishing pass on ~225 MVP images, and whether to commission a human illustrator for key art and the logo. |
| 3 | **Price point** | Benchmark against premium mobile deckbuilders. |
| 4 | **iOS and PC (Steam)?** | Compose Multiplatform and LiteRT-LM make this plausible. Decide after Phase 3. |
| 5 | **Localization** | Gemma models are multilingual, so narration could be localized cheaply. The authored fallbacks and rules templates still need professional localization. Which languages at launch? |
| 6 | **Online features** | Fixed Points leaderboards (server-side replay verification) and variant share codes. Worth a backend? |
| 7 | **Number of operatives at launch** | 9 + 3 is the target. Cut to 6 + 3 if the schedule slips? |
| 8 | **Audio** | Faction leitmotifs ([01](01-vision.md#audio-direction)). Composer budget and adaptive music scope. |
| 9 | **Parley experiment** | Revisit after 1.0? It is out of scope today ([07](07-genai-design.md#future-experiments)). |
| 10 | **Player-named Anachronists** | Let players name their operatives (used by chronicles)? It needs a name filter. |
| 11 | **Rating** | PEGI 12 or 16 depends on which dark chapters of history make the launch eras (the Somme, Los Alamos, ...). Decide when the eras are locked. |
