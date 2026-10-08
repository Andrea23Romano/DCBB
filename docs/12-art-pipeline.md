# 12: Art Pipeline

How we make the art for *Anachronist*: **AI-assisted, human-directed**. Images are generated at development time with **hosted image services**, then chosen, corrected and finished by people, with every asset's origin recorded. What the art should look like is defined in [11](11-art-direction.md).

> **Runtime rule (unchanged):** the shipped game never generates images. The on-device model writes words only ([07](07-genai-design.md#principles)), and Branch Variants are shader effects on existing art.

---

## Who Does What

| Role | Who | Responsibilities |
|---|---|---|
| **Art director and finisher** | You | Run the hosted tools, choose the picks, do the finishing pass, give final approval |
| **Style keeper** | Claude | Maintains the style bible ([11](11-art-direction.md)) and prompt library; writes per-asset prompts from the manifest; reviews images against the golden set and scorecard; tracks the manifest and the provenance log; flags style drift |
| **Optional illustrator** | A human artist, if budget allows | Key art and the logo, where the store page needs the most polish |

I can look at images you put in the repo or attach to the chat, so reviews can happen right where the files are.

---

## Tools: Hosted Services

We use hosted image generators (for example Midjourney, OpenAI's image models in ChatGPT, or Google's Gemini image models). Their features and terms change often, so the requirements below matter more than any brand. Check them at the start of production and record what you used.

| Need | Why | What to look for |
|---|---|---|
| **Commercial use** | We sell the game | Terms that allow commercial use of outputs, often only on paid plans. Record the plan. |
| **Style references** | Consistency across hundreds of images | Passing golden-set images as style references |
| **Character references** | The recurring cast | Keeping a character consistent from reference images |
| **Editing** | Fixing hands and details | Region editing, inpainting, or instruction-based edits |
| **Aspect ratios and size** | The [element specs](11-art-direction.md#element-specs) | 1:1, 3:4, 9:16 and tall phone ratios; ≥1536 px or a good upscaler |
| **Transparent backgrounds** | Enemies, artifacts, portraits | Native transparency, or a plain background plus a cutout tool |
| **Job history** | Provenance | Job IDs, seeds or a saved generation history |
| **Privacy** | Unreleased designs | A private mode, or opting out of training on our content, if offered |

- **Pick one primary generator for production.** Mixing generators is the fastest way to lose consistency. Other tools are fine for brainstorming.
- **Re-run the style-lock checks if the primary tool changes model versions mid-production.** Model updates change the look.

---

## Folder Layout

```
art/                    binaries tracked with Git LFS (*.png, *.webp, *.psd, *.kra)
  bible/                palettes (.ase / .gpl), shared textures, frame sources
  golden/               the approved reference set, per medium
  characters/           character sheets for the recurring cast
  prompts/              prompt blocks and per-asset prompts (plain text, versioned)
  inbox/                raw generations waiting for review
  masters/              finished full-resolution masters
  export/               game-ready files, built by script from masters
  legal/                saved copies of tool terms, with dates
  manifest.yaml         every asset the build needs, with its spec and status
  provenance.csv        one row per approved asset
```

---

## Prompt Architecture

Every prompt is assembled from six blocks, always in this order. Only the **subject** changes from asset to asset; the other blocks come from the style bible, so consistency is built in.

| # | Block | Source | Example |
|---|---|---|---|
| 1 | **Subject** | The manifest entry: who or what, doing what | `a weary crusader knight carrying a battered great helm under one arm` |
| 2 | **Medium** | The faction block from [11](11-art-direction.md#faction-media), or the native block | Order: `illuminated manuscript painting, egg tempera colors, ...` |
| 3 | **Era** | The era row from [11](11-art-direction.md#era-modifiers) | `late 12th century, Third Crusade, mail hauberk and great helm, no plate armor` |
| 4 | **Core** | The rendering core (fixed) | see below |
| 5 | **Composition** | The element template (fixed per element) | see below |
| 6 | **Exclusions** | Fixed, phrased positively where possible | see below |

### Fixed blocks (v0)

- **Core:** `painterly gouache and oil illustration, visible directional brushwork, crisp focal subject with soft simplified background, warm key light from the upper left and a colored rim light from behind, strong value contrast on the subject, edges fading softly into warm vellum paper, mythic and bittersweet mood`
- **Native medium** (era natives and neutral subjects): `painterly illustration on plain warm vellum, era palette, no gold leaf, no collage`
- **Exclusions:** `clean unmarked surfaces, no lettering, no writing, no symbols that resemble text, no signature, no watermark, no border, no frame, not photographic, not a 3D render`

> Many hosted tools handle "no X" poorly, because naming X can summon it. Keep exclusions short, use the tool's own negative or exclusion option where it has one, and expect to fix leftovers in the finishing pass.

### Composition templates (per element)

| Element | Composition block |
|---|---|
| Card illustration | `single subject, square composition, subject centered within the middle of the frame, 3/4 view, background simplified toward the edges` |
| Enemy | `full figure from head to feet, 3/4 view facing down and to the left, plain flat mid-grey background, no ground, no scenery` |
| Boss splash | `heroic low camera angle, full figure, tall vertical composition, empty space in the lower fifth` |
| Operative / hub portrait | `bust portrait, chest up, 3/4 view, plain background, consistent framing` |
| Era background | `tall vertical environment painting, no people, quiet mid-value middle band, detail concentrated at the top and bottom` |
| Artifact | `a single object, 3/4 top-down view, centered, plain flat background, soft contact shadow` |

### Worked example: the Vowknight card

Assembled as one paragraph for the tool:

> a weary crusader knight in mail and a white tabard with a sundial cross, carrying a battered great helm under one arm, a scar across the sword hand, a strip of cloth tied to the sword hilt, looking past the viewer · illuminated manuscript painting, egg tempera colors, burnished gold leaf background with tooled pattern, thin ink outlines, lapis blue and vermilion, medieval devotional composition, painterly brushwork, vellum texture · late 12th century, Third Crusade, mail hauberk and great helm, no plate armor · painterly gouache and oil illustration, visible directional brushwork, crisp focal subject with soft simplified background, warm key light from the upper left and a colored rim light from behind, strong value contrast on the subject, edges fading softly into warm vellum paper, mythic and bittersweet mood · single subject, square composition, subject centered within the middle of the frame, 3/4 view, background simplified toward the edges · clean unmarked surfaces, no lettering, no writing, no symbols that resemble text, no signature, no watermark, no border, no frame, not photographic, not a 3D render

Plus the golden-set images as **style references**, and the Vowknight sheet as a **character reference** once it exists.

---

## The Golden Set and Character Sheets

- **The golden set** is a small library of approved images: 6–8 per medium (Order, Convergence, Errata, Tear, native), plus 2 that show the core on its own.
  - They are the style references fed to the hosted tool with every generation.
  - They are also the side-by-side standard in every review.
  - The style-lock sprint creates version 1. The set changes only by deliberate decision, never by drift.
- **Character sheets** fix the recurring cast ([11](11-art-direction.md#the-recurring-cast)): front, 3/4 and back views, 3–4 expressions, palette, and five visual anchors. The sheet is the character reference for every asset that shows that character.

---

## Generation Workflow

1. **Brief.** I write the per-asset prompt from the manifest entry, using the six blocks.
2. **Generate.** 4–8 variations in the primary tool, with the golden set (and character sheet, if any) as references.
3. **Shortlist.** Pick 1–3, judging at the element's real display size, not full screen.
4. **Refine.** Use the tool's editing to fix composition or details. Re-roll rather than fight a broken image.
5. **Choose** one.
6. **Finishing pass** ([below](#the-finishing-pass)).
7. **QA scorecard** ([below](#qa-scorecard)). Anything that fails goes back to step 2 or 4.
8. **Approve** and save the master.
9. **Export.** A script builds the game sizes from masters.
10. **Log** the provenance row.

---

## The Finishing Pass

Every shipped image goes through a person's hands. This is where quality, consistency and authorship come from. Target: 10–20 minutes per card illustration.

1. **Crop** to the element spec, and check the safe area.
2. **Repair** hands, eyes, weapons, armor straps and broken symmetry. Paint out text-like marks, signatures and watermarks.
3. **History pass.** Fix anachronisms (plate armor in 1191, wrong costume) unless they are intentional.
4. **Grade** with the shared faction and era adjustment presets, so palettes match the swatches in [11](11-art-direction.md).
5. **Ground.** Fade the edges into the shared vellum texture. Keep masters clean; the engine adds the thread overlay.
6. **Cut out** enemies, artifacts and portraits with a clean alpha edge and no halos.
7. **Export** the master as full-resolution PNG, and let the script make the game sizes.

Any raster editor works (Krita, GIMP, Photoshop, Affinity). Keep the adjustment presets in `art/bible/` so everyone grades the same way.

---

## QA Scorecard

Every check must pass before an asset is approved.

| Check | Pass rule |
|---|---|
| **Style match** | Side by side with the golden set: same brushwork, light direction and ground |
| **Medium** | The faction medium is unmistakable, or absent for natives |
| **Palette** | Dominant colors sit within the faction and era swatches (eyedropper check) |
| **Readability** | Passes the element's thumbnail, grayscale and silhouette tests ([11](11-art-direction.md#readability-tests)) |
| **Anatomy and objects** | No extra or fused fingers, broken weapons or melted details |
| **History** | Era details are correct, or the anachronism is intentional and noted |
| **Exclusions** | No text-like marks, signatures, watermarks, brands, or likeness of a living person |
| **Rating** | Within the rating target in the [content principles](02-world-and-lore.md#content-principles) |
| **Spec** | Correct canvas, safe area, and background or alpha |
| **Provenance** | The row in `provenance.csv` is complete |

---

## Provenance Log

One row per approved asset in `art/provenance.csv`. It makes every asset reproducible or replaceable, supports authorship claims, and answers store disclosure questions.

```csv
asset_id,file,element,tool,model_or_version,plan,date,prompt,references,job_or_seed,variations_reviewed,human_edits,editor,approved_by,approved_date,notes
card.order.line_strike,masters/cards/order/line_strike_v03.png,card,<tool>,<version>,<plan>,2026-10-12,"<full prompt>","golden/order/03.png; characters/vowknight.png",<job id>,8,"repainted hand; removed text mark; graded Order preset",<name>,<name>,2026-10-13,
```

---

## Legal and Disclosure Checklist

*Not legal advice. Review with a lawyer before launch.*

- **Terms.** Confirm each service allows commercial use of outputs on our plan, and save a dated copy of its terms in `art/legal/`.
- **Names.** Never put a living artist's name in a prompt, nor "in the style of" any living artist. No franchise or brand names. No real living people.
- **Copyright.**
  - The US Copyright Office's January 2025 report on copyrightability says prompts alone don't make the user the author.
  - Human contributions such as selection, arrangement and modification can be protected.
  - So: make the finishing pass meaningful and record it. Treat the human-made layer (frames, icons, layouts, compositions, edits) as the core of our protectable art.
- **Steam disclosure.** If we release on PC, Steam's content survey requires disclosing pre-generated AI content that players see or hear, and the store page shows it. Valve revised the form in January 2026.
- **Mobile.** We know of no equivalent requirement on Google Play for pre-generated assets, but we disclose anyway (store listing and credits), consistent with the transparency rules in [07](07-genai-design.md#safety-policy-and-ethics).
- **Replaceability.** Because every asset has its prompt, references and edits on record, any image can be regenerated or replaced if a tool's terms or a legal question ever require it.

---

## The Style-Lock Sprint

The first art task. It turns the v0 direction into a locked golden set.

**Subjects** (one per medium, plus a background):

| # | Subject | Medium | Element |
|---|---|---|---|
| 1 | The Vowknight | Order | Card illustration |
| 2 | Brass Proxy (1843 brass body) | Convergence | Enemy |
| 3 | *Two Places at Once* (the Splinter) | Errata | Card illustration |
| 4 | London, 1843 street | Native | Era background |
| 5 | Loose End | Tear | Enemy |

**Process:**

1. Generate 8 variations per subject in each candidate tool, using the v0 blocks.
2. Shortlist 2 per subject and do a light finishing pass.
3. Mock a fanned hand of cards with a placeholder frame, on a real phone.
4. Score each subject and tool on:
   - consistency across subjects
   - readability at real size
   - distinctiveness of the three media
   - AI-artifact rate
   - finishing minutes per image

**Outputs:**

- the **primary tool** decision
- **golden set v1**
- **prompt blocks v1**
- palette adjustments, written back into [11](11-art-direction.md)

---

## MVP Asset Manifest

Counts for the MVP content ([10](10-roadmap.md#mvp-content)). Item-level entries live in `art/manifest.yaml`, generated from the content data once it exists.

| Element | MVP count | Notes |
|---|---|---|
| Card illustrations | ~120 | One per card, starters included |
| Enemies | ~25 | Including 4 elites |
| Bosses | 5 | Sprite plus splash each: 2 act bosses, 3 Nexus champions |
| Operatives | 3 | Character sheet, bust, full body |
| Hub residents | 4 | 4 expressions each; Wren & Wren share a sheet |
| Era backgrounds | 5 | London, Milan, the Salt Waste, the Still Hour, the Margins |
| Moment art | 8 | One per Moment type |
| Artifacts | ~25 | Including 3 boss Artifacts |
| UI textures | ~6 | Vellum, thread, gold leaf, porcelain, collage paper, torn edge |
| **Illustrated total** | **~225** | AI-assisted, human-finished |
| Icons | ~50 | Hand-made vectors: energy 4, intents 8, keywords 25, statuses ~10 |
| Card frames | 1 system | Built: 4 media × 3 types × 4 rarities |
| Store assets | 1 set | Icon, feature graphic, screenshots |

### Production order

1. **Style-lock sprint**, producing golden set v1.
2. **Character sheets:** the 3 operatives, Aurelian, a MERIDIAN Proxy, the Thousandfold.
3. **Frames and icons**, in parallel; they don't depend on the sprint.
4. **Vertical-slice art for Phase 2:** the 12 starter cards, the prototype enemies, the London background, the 3 operatives.
5. **The rest of the MVP manifest.**

---

## Working Loop

1. **Brief.** I write per-asset prompts from the manifest into `art/prompts/`.
2. **Generate.** You run them in the primary tool and put the picks in `art/inbox/`, or attach them in chat.
3. **Review.** I compare each image with the golden set and the scorecard, and reply with pass or fail notes plus prompt or edit fixes.
4. **Finish.** You do the finishing pass. I log provenance and update the manifest, and the export script builds the game sizes.
5. **Drift check.** Every ~20 approved assets, we review a contact sheet against the golden set and adjust the blocks if the look is drifting.
