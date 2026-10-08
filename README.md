# DCBB

DC but better (because I love DC)

**Anachronist** (working title) is a roguelike deckbuilder for Android, inspired by *Dawncaster*.

- **Premise.** Three factions wage a war over **the Weave**, a fabric of time whose threads branch instead of breaking:
  - **the Order of the Unbroken Line**, medieval knights who walk through time by relic
  - **the Convergence**, an AI from 2049
  - **the Errata**, people from erased timelines
- **Time-bending in every layer:**
  - **combat:** enemy intents on a visible Track that you can delay, at a price
  - **map:** skipped futures come back, and you can rewind to Anchors
  - **meta:** every run persists as a branch
- **The antagonist.** Every cut, purge and reckless fork widens **the Tear**, a rip in the Weave where every erased history ends up, and it wants back in. None of the factions can win this war, and none of them can stop.
- **On-device AI.** A small LLM running on the phone, **the Chronicler**, narrates the history you change. It never touches the rules, and the game is complete without it.

**Status:** Phase 1, the combat prototype. Start with the **[design documents](docs/README.md)**.

## Running the combat prototype

Phase 1 proves the combat before anything else gets built ([roadmap](docs/10-roadmap.md#phase-1-status)). Everything runs on the JVM.

You need **JDK 17 or newer**. The Gradle wrapper fetches everything else.

```bash
./gradlew test                     # the rules engine's test suite

./gradlew :cli:installDist         # build the text client, then play a fight:
cli/build/install/cli/bin/cli      # pick an operative, a deck and an encounter
cli/build/install/cli/bin/cli --op splinter --deck mid --enc misprinted_alley --seed 7

./gradlew :web:webDist             # the browser test client: one self-contained page,
open web/build/dist/index.html     # works offline (or use your file manager)

./gradlew :sim:run --args="--seeds 200 --out reports/sim-latest.md"     # balance report
./gradlew :sim:run -q --args="--trace vowknight/mid/the_rent/2"         # one fight, blow by blow
./gradlew :sim:run -q --args="--replay '<code from the web client>'"    # play back a copied fight
```

- **Browser client:** tap a card to line it up, then tap it again (or **Play**) to play it. **Hint** asks the planner bot, **Bot plays turn** lets it finish your turn, **Undo** steps back, and **Copy log** copies a replay code you can send to reproduce a fight exactly.
- **Text client:** type `help` for commands. For example, `p 2 e1` plays card 2 on enemy 1, `sig` uses your Signature Action, `hint` asks the bot, and `end` ends your turn.

| Module | What's inside |
|---|---|
| [`core-rules`](core-rules/src/commonMain/kotlin/dcbb/core) | The combat engine: a pure reducer `(state, action) → (state, events)` with seeded RNG streams. Also the content (every sample card in [docs/03](docs/03-factions-and-operatives.md), 21 enemies, 14 encounters), the [docs/08](docs/08-card-dsl.md) budget calculator and rules-text renderer, the bots, and the tests. |
| [`web`](web/src/jsMain/kotlin/dcbb/web) | The browser test client: `core-rules` compiled to JavaScript, in one self-contained page. |
| [`cli`](cli/src/main/kotlin/dcbb/cli/Main.kt) | A text client for playing single fights. |
| [`sim`](sim/src/main/kotlin/dcbb/sim/Main.kt) | The batch simulator that writes the balance report. |
| [`reports`](reports/README.md) | The latest balance report, the frozen first baseline, and what changed between them. |
