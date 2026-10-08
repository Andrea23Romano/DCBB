# 11: Art Direction — Illuminated Weave

> **Status:** direction chosen. Palettes and prompt blocks are **v0** until the style-lock sprint ([12](12-art-pipeline.md#the-style-lock-sprint)) produces the golden set.

**Illuminated Weave** in one sentence: painterly illustration on a woven paper-and-thread ground, where every faction is painted in its own **medium**. The Order is an illuminated manuscript, the Convergence is engraving on porcelain, and the Errata are collage cut from both. The Tear is real rips through the picture itself.

**Why this direction:**

- **Familiar and ours.** It is close to the hand-painted feel Dawncaster players love, but unmistakably our own.
- **The Weave in every image.** A paper-and-thread ground and woven frames turn the setting's central metaphor into the visual identity.
- **Readable factions.** Faction = medium makes factions readable without relying on color alone.
- **Good for AI art.** Painterly rendering hides AI artifacts better than clean realism, and strict media constraints fight style drift.

How we produce the art (hosted AI tools, curation, finishing, provenance) is in [12](12-art-pipeline.md).

---

## Principles

1. **One core, three media.** Everything shares the [rendering core](#the-rendering-core). Factions change the *medium*, not the painter.
2. **Read at thumb size.** Every image must work at 128 px: one subject, strong value contrast, a simple silhouette.
3. **The Weave is always there.** The paper-and-thread ground and the woven frames are applied in the finishing pass, so they are identical everywhere.
4. **History, not a costume party.** Era details (clothes, arms, architecture, objects) are researched. Anachronisms are deliberate story beats, never accidents.
5. **Human-directed.** AI generates options. People choose, fix and finish, and nothing ships unfinished ([12](12-art-pipeline.md#the-finishing-pass)).
6. **Words are UI, never art.** No text inside images. The engine renders all text.

---

## The Rendering Core

These rules apply to every illustrated asset: cards, enemies, portraits, backgrounds and artifacts.

| Aspect | Rule |
|---|---|
| **Technique** | A painterly gouache-and-oil look with visible, directional brushwork. Soft edges in the background, crisp edges on the focal subject. Not photographic, not 3D-rendered, not cel-shaded. |
| **Light** | One warm key light from the upper left (the "reading lamp"), plus a rim light in the faction color from behind. The direction never changes, so a hand of cards looks lit by one lamp. |
| **Value** | The subject has the strongest value contrast, and the background is compressed into the middle values. Every image must pass the grayscale test. |
| **Detail budget** | Detail is concentrated on the face, hands or focal object (about 20% of the image). Everything else is simplified into large brush masses. |
| **Color** | The subject takes the faction palette and the setting takes the era palette, with one accent color. Saturation peaks on the focal point. |
| **Composition** | One subject, centered or on a strong diagonal. 3/4 views preferred. Low camera for heroes and bosses, eye level for everyone else. |
| **Edges** | The image fades softly into the paper ground near the frame, with no hard rectangle, so the art sits *on* the Weave. |
| **Mood** | Mythic and bittersweet: dignity, weariness, wonder. Neither grimdark gore nor cheerfulness. |

### Never, in any asset

- Text, letters, numbers, rune-like scribbles that read as text, logos, signatures or watermarks.
- Modern brands or trademarks in historical scenes.
- The likeness of a living person. The names of living artists in prompts.
- Photo-realism, glossy 3D, plastic skin, lens flares, or glowing "AI sheen".
- Extra fingers, fused limbs, or warped weapons. These are fixed in the finishing pass.
- Gore beyond the rating target ([02](02-world-and-lore.md#content-principles)).

---

## The Weave Ground and Frames

- **The ground.** Every card and portrait sits on warm vellum paper with a faint woven-thread texture.
  - The texture is a shared file applied in the finishing pass or by the engine. It is never generated per image, so it is identical across assets.
- **Frames** are built, not generated: vector shapes plus shared textures.

| Frame part | Varies by | Values |
|---|---|---|
| Border band | Faction medium | Order: gold-leaf illumination · Convergence: engraved porcelain rim · Errata: taped collage strip · Neutral: plain woven band |
| Top shape | Card type | Attack: pointed arch · Skill: rounded arch · Constant: square with four corner pins |
| Thread color | Rarity | Common: iron grey · Uncommon: silver · Rare: gold · Legendary: living thread (animated shader) |

- **Branch Variants** reuse the same art with an in-engine shader (double exposure plus a misregistered offset) and a variant frame. They are never re-generated.
- **Energy pips** keep their shapes everywhere: ☀ Faith, ⬡ Compute, ◎ Flux, ○ Neutral.

---

## Faction Media

Each medium has a palette, materials, a shape language, a linework rule, a light accent, and a **prompt block** (v0). Prompt blocks are assembled as described in [12](12-art-pipeline.md#prompt-architecture).

### The Order: Illuminated Manuscript

Every Order image looks like a page of a 12th–15th-century illuminated manuscript brought to life: egg-tempera color, gold leaf, patterned backgrounds.

| Swatch | Hex | Use |
|---|---|---|
| Lapis ultramarine | `#2B3F8C` | Robes, skies, shadows |
| Vermilion | `#C8412B` | Crosses, banners, blood-of-saints red |
| Gold leaf | `#C9A24A` (highlight `#F0D98A`) | Halos, backgrounds, rims |
| Vellum | `#EFE3C8` | Ground, tabards |
| Verdigris | `#4E7D6A` | Sparing accent |
| Iron-gall ink | `#2A1E16` | Outlines |

- **Materials and motifs:** gold-leaf glints, tooled gold backgrounds with diaper patterns, stained-glass light, the sundial-cross, gnomon shadows, banners, vellum, mail, white tabards.
- **Shape language:** verticals, arches, triangles. Symmetrical, stable compositions, with figures framed like saints in niches.
- **Linework:** thin dark ink outlines on figures, in manuscript style, over painterly interiors.
- **Light accent:** a warm gold rim.
- **Do:** flatten backgrounds into patterned gold or color fields. **Don't:** use sci-fi materials or modern polished steel.
- **Prompt block:** `illuminated manuscript painting, egg tempera colors, burnished gold leaf background with tooled pattern, thin ink outlines, lapis blue and vermilion, medieval devotional composition, painterly brushwork, vellum texture`

### The Convergence: Engraving on Porcelain

As if an engraver had drawn on glazed white porcelain: precise cyan hatching over smooth white forms. In older eras, the same mind wears brass and enamel clockwork.

| Swatch | Hex | Use |
|---|---|---|
| Porcelain white | `#F4F6F7` | Proxy bodies, surfaces |
| Cyan ink | `#1F9BB5` | Engraving lines, hatching |
| Cyan glow | `#6FE3F2` | Accent only: eyes, joints, lenses |
| Deep navy | `#13233A` | Shadows, backgrounds |
| Brass | `#B0894A` | Proxies before 1900 |
| Gold filigree | `#D4B15A` | Circuit-like ornament |

- **Materials and motifs:** porcelain, enamel, brass gears, glass lenses, punch cards, engraved schematic lines, concentric circles, hexagonal tessellation.
- **Shape language:** circles, hexagons, radial symmetry, perfect geometry. Calm and centered.
- **Linework:** fine parallel hatching and cross-hatching in cyan, like copperplate engraving, with clean contours.
- **Light accent:** a cool cyan glow from inside joints, eyes and lenses. The key light stays warm, for contrast.
- **Do:** keep surfaces clean and luminous. **Don't:** drift into chrome-and-neon cyberpunk. MERIDIAN is a cathedral, not a nightclub.
- **Prompt block:** `porcelain and engraving style, white glazed porcelain surfaces with fine cyan ink engraving and cross-hatching, precise geometry, concentric circles, soft inner cyan glow, brass clockwork details, painterly, calm and luminous`

### The Errata: Collage

Errata images are assembled from fragments of other images: cut-out pieces of manuscript and engraving, newsprint, and paintings of other centuries, sewn and taped together, with misprint offsets.

| Swatch | Hex | Use |
|---|---|---|
| Errata violet | `#6A3FA0` | Dominant color |
| Misprint magenta | `#D2357F` | Offsets, accents |
| Newsprint grey | `#B9B4A8` | Paper fragments |
| Aged tape | `#E3D29B` | Tape, seams |
| Ink black | `#222222` | Doodles, marginalia |
| *Borrowed* | Order gold and Convergence cyan, desaturated | Fragments taken from the other factions |

- **Materials and motifs:** torn paper edges, masking tape, stitches, staples, marginalia doodles, the proofreader's caret ‸, double exposures, misregistered color (offset-printing errors), patchwork costumes from many eras.
- **Shape language:** asymmetry, overlapping layers, broken silhouettes reassembled. The figure must still read as **one silhouette**, with the collage living inside it.
- **Linework:** mixed. Each fragment keeps its source's linework.
- **Light accent:** fragments may be lit differently, which is a deliberate inconsistency, but the whole figure keeps the core key light for readability.
- **Do:** let the seams show. **Don't:** let the collage break the silhouette or the focal face.
- **Prompt block:** `mixed-media collage portrait, torn paper fragments of illuminated manuscript and porcelain engraving stitched together, misregistered magenta and violet print offset, masking tape, marginalia doodles, patchwork clothing from different centuries, painterly, readable single silhouette`

### The Tear: Rips Through the Canvas

The Tear is not drawn *in* the picture; it tears the picture. Ragged rips through the painted surface reveal a black void behind, with loose threads hanging from the edges. The Unwoven are figures made of snapped thread and absence.

| Swatch | Hex | Use |
|---|---|---|
| Void | `#0B0A10` | The darkest value in the game, reserved for the Tear |
| Void depth | `#3A3440` / `#5B5566` | Barely-visible depth inside the void |
| Thread bone | `#E9E2D0` | Loose threads, frayed edges |
| Edge shadow | `#2B2830` | The underside of torn paint |

- **Materials and motifs:** torn canvas edges, unravelled thread, missing pieces of the image, silhouettes cut out of the scene.
- **Shape language:** jagged diagonal rips and negative space.
- **Light:** none comes from the void. The only light is the scene's own light catching the torn edges.
- **Do:** make it feel like damage to the art itself. **Don't:** add glows, particles or energy effects. The Tear is absence, not magic.
- **In the engine,** a shader animates the tearing on cards the Tear touches, so generated art only needs the static version.
- **Prompt block:** `the painting itself is torn, ragged rips through the canvas revealing pure black void behind, loose threads hanging from the torn edges, figure made of snapped threads and empty space, silhouette cut out of the scene, no glow`

### Era Natives and Neutral Subjects

- Painted in the rendering core with the **era palette** and **no faction medium**, on a plain vellum ground. That keeps the faction media special.
- Tear-touched or Errata-touched natives get a light touch of that medium: one rip, or one misprint offset.

---

## Era Modifiers

| Era | Palette | Setting | Accuracy notes |
|---|---|---|---|
| **London, 1843** | Fog grey `#8E9196` · Soot `#3B3836` · Gaslight amber `#E0A44A` · Brick `#8A4B3A` · Thames grey-green `#5E6B60` | Gaslit streets, print shops, rookeries, constables, early engines | Early-Victorian dress, mostly working-class. Gas lamps, cobbles, coal smoke. |
| **Milan, 1495** | Fresco ochre `#C9954C` · Terracotta `#B4593A` · Sinopia `#9C4A36` · Plaster white `#EDE3D1` · Lombard sky `#7FA3C0` | The Sforza court, workshops, scaffolds, condottieri, plague masks | Late-quattrocento fashion. Leonardo's workshop clutter: drawings, wooden mechanisms. Milanese plate armor. |
| **The Salt Waste, 1191** | Salt white `#F2EEE6` · Black glass `#121214` · Heat-haze gold `#D9B56E` · Bleached sky `#CFD8DE` · Rust rock `#8C5A3C` | Salt flats, mirages, the black glass needle, crusader and Ayyubid patrols | Third Crusade arms of both sides: mail, great helms, surcoats, lamellar. **No plate armor in 1191.** |
| **The Still Hour** (hub) | Dusk violet `#4B3B5E` · Candle gold `#E8B86A` · Stone grey `#6E6A72` | A cloister-observatory in endless dusk, with bells | A Romanesque cloister crossed with observatory instruments |
| **The Margins** | Paper white `#F4F0E6` · Ink `#1E1E1E` · misprint magenta and cyan offsets | The spaces between branches: pages, scraps, borrowed skies | A collage of every era |

> **Accuracy is part of the fantasy.** Mail and great helms in 1191, Milanese plate in 1495: getting it right makes time travel feel real. When the art shows something out of its century, it should be on purpose (an Errata patchwork, a Proxy's brass body).

---

## The Recurring Cast

Characters who appear in many assets get a **character sheet** before any production art:

- front, 3/4 and back views
- 3–4 expressions
- their palette
- 5 visual anchors

The sheets become the reference images given to the hosted tools ([12](12-art-pipeline.md#the-golden-set-and-character-sheets)).

| Character | Faction | Visual anchors |
|---|---|---|
| **The Vowknight** | Order | Battered great helm carried under the arm · white tabard with the sundial-cross and a frayed hem · mail · a scar across one hand · a strip of vow-cloth tied to the sword |
| **The Oracle** | Convergence | Porcelain Proxy shell with a human face engraved into the faceplate · cyan-lit eyes · a 2049 lanyard still hanging from the neck · hexagonal joints · one cracked shoulder plate |
| **The Splinter** | Errata | Two overlapping selves in double exposure, slightly offset · patchwork coat from two eras · one gloved hand, one bare · the caret ‸ stitched on the chest |
| **Grand Master Aurelian** | Order | Very old and upright · a gnomon shadow behind him like a gold-leaf halo · black glass shard in his gauntlet · white beard · tabard faded to grey |
| **Brother Aurel** (young) | Order | Aurelian's face at 25 · sunburnt · crusader mail · wide eyes |
| **MERIDIAN** (any Proxy) | Convergence | A faceless porcelain oval with a single concentric-circle lens · an era-specific body: clockwork, brass, or white synthetic |
| **KESTREL** | Convergence | A small, birdlike porcelain Proxy · head always slightly tilted · a ribbon it keeps by the window |
| **The Thousandfold** | Errata | A woman made of many overlapping versions, edges offset in magenta and violet · a crown of torn paper |
| **Wren & Wren** | Errata | The same face twice in different centuries' clothes, in mirrored poses |
| **Sister Ysolde** | Order | Middle-aged · warm eyes · ink-stained fingers · a ledger of burned branches |
| **The Archivist** | none | Hooded, face never fully lit · threads trailing from the sleeves · a shuttle-shaped pendant (a spoiler-safe hint) |

---

## Element Specs

Production rules for each kind of asset. "AI + finishing" means generated in a hosted tool, then corrected and finished by a person ([12](12-art-pipeline.md#generation-workflow)).

| Element | Made by | Canvas | Delivery | Composition and safe area | Readability test |
|---|---|---|---|---|---|
| **Card illustration** | AI + finishing | 1:1, generated at ≥1536 px | PNG master + 1024 px export | Subject inside the central 70%. The outer 12% may be covered by the frame. | 128 px thumbnail |
| **Card frame** | Built (vector + shared textures) | 5:7 card | Layered SVG / PNG | Varies by type, rarity and faction ([above](#the-weave-ground-and-frames)) | A 220 px card in a fanned hand |
| **Enemy** (combat) | AI + cutout + finishing | 3:4, full figure on a plain mid-grey background | Transparent PNG, master height 1536 px | Feet visible, facing down-left toward the player. Scale classes: normal 1.0, elite 1.2, boss 1.5. | Silhouette test |
| **Boss splash** | AI + finishing | 9:16 (1440×2560) | PNG | Hero composition from a low camera, with space at the bottom for the name | Full phone screen |
| **Operative art** | AI + finishing, from the character sheet | Bust 3:4 + full body 9:16 | PNG | Matches the sheet's anchors | 128 px bust |
| **Hub portraits** | AI + finishing | Bust 3:4 × 4 expressions | Transparent PNG | Identical framing across expressions | Expression readable at 200 px |
| **Era background** | AI + finishing | 9:19.5 (1440×3120) | PNG / WebP | A quiet, mid-value middle band where the combat UI sits. Detail at the top and bottom only. No characters. | UI overlay test |
| **Moment art** (map cards) | AI or crops | 3:4 | PNG | One emblematic object or scene per Moment type | 96 px |
| **Artifact** | AI + cutout | 1:1 object on a plain background | Transparent PNG, 1024 px | 3/4 top-down view, same key light | 96 px silhouette |
| **Icons** (energy, intents, keywords, statuses) | Drawn by hand as vectors (AI for sketches only) | 24 px grid, 2 px stroke | SVG | Consistent stroke weight and corner radius | 16 px, plus color-blind check |
| **UI textures** (paper, thread, gold) | AI, then made seamless | Tileable 1024 px | PNG / WebP | No visible repeats | n/a |
| **VFX** (Branch Variant, Tear rip, Track tick) | Engine shaders | n/a | AGSL + static fallback | n/a | Motion test on device |
| **Store assets** | AI + heavy finishing (consider a human illustrator for key art and the logo) | Google Play: 512×512 icon, 1024×500 feature graphic | PNG | Brand-safe, no small text in art | Store page test |

---

## Readability Tests

Run on every asset before approval. The full scorecard is in [12](12-art-pipeline.md#qa-scorecard).

1. **Thumbnail.** At the element's test size, can someone name the subject within two seconds?
2. **Grayscale.** With color removed, does the subject still separate from the background?
3. **Silhouette.** Does the filled black shape still read? Required for enemies and artifacts.
4. **Faction without frame.** Can you tell the faction from the medium alone?
5. **Color-blind.** Under simulated deuteranopia and protanopia, do faction cues and energy pips stay distinct? Their shapes should carry it.
6. **The hand test.** Fan five cards at real size on a phone. Do they read as one game?
