# Phase 05 build-start checks — dictionary, emoji artwork, FlorisBoard reuse

Date 2026-09-22. Agent. Scope: the three "verified at build start" items in
`docs/plan/phase-05-keyboard.md` Decisions (2026-09-16) and interview item 4. Everything below was
verified against the source's own licence text at a pinned version; README summaries were not
trusted. Reproduced end to end by `tools/fetch-keyboard.sh` (section 4).

## 1. Dictionary — SCOWL 2020.12.07 (membership) + 12dicts 6.0.2 "2+2+3frq" (ranking)

**Verdict: PASS.** Both sources are permissive, non-Google, American, and offline.
Output `app/src/main/assets/keyboard/en_US.tsv`: **124,847 entries**, UTF-8, `word<TAB>count`,
no header, count descending then word ascending (code point order), letters (any script, so
"café" and "Atatürk" survive: 308 non-ASCII words) plus apostrophe, no hyphens (SCOWL ≤ 60 has
none), case as SCOWL gives it (21,679 capitalised entries, "I" capital). Common contractions
present: don't, it's, I'm, I've, I'll, can't, won't, you're, isn't, he's, that's, there's,
what's, let's (the script asserts a sample of them).

### 1a. Membership: SCOWL 2020.12.07

- Source: `https://downloads.sourceforge.net/wordlist/scowl-2020.12.07.tar.gz`,
  sha256 `5587667caa20c4891390c2d42dbb4d5c4c3f41bee77af1457ece3ba23fb859cc` (2,569,810 bytes).
- Recipe: the tarball's own `mk-list -d final -v1 --accents=both en_US <size>` for sizes
  10, 20, 35, 40, 50, 55, 60. That is `speller/make-hunspell-dict`'s recipe for the official
  hunspell en_US dictionary (`mk-list --accents=strip en_US 60`), with the two options the
  `-large` variant adds: level-1 spelling variants (`-v1`: both "judgment"/"judgement") and
  accented forms kept beside their stripped forms (`--accents=both`: "café" and "cafe").
  A word's level = the smallest list it appears in. Level counts: 10: 4,444 · 20: 8,147 ·
  35: 37,756 · 40: 7,425 · 50: 44,932 · 55: 6,531 · 60: 15,612. Level 60 is what SCOWL's README
  calls the normal spell-checking size; 70 would exceed 150k and adds UKACD-derived words.
- Why 2020.12.07 and not the current rel-2026.02.25: the 2026 `Copyright` says *"Data from the
  Corpus of Contemporary American English (COCA) was also used. All data from COCA comes from
  3-gram data that is not freely available; however, the usage is within the rights given by the
  NDA that was signed when purchasing the data."* That NDA cannot be read or verified here; the
  2020 tarball's `Copyright` enumerates every source with its own licence, and the pre-built
  `final/` lists are only distributed on SourceForge for 2020.12.07 (the GitHub release carries
  only aspell/hunspell packages).
- Licence text (`Copyright` in the tarball, sha256
  `283326a422e29c510e2ba6805518c418ce3c35ed1fadc3eec83d2da4f6c5a055`, shipped verbatim as
  `app/src/main/assets/licenses/scowl-2020.12.07-Copyright.txt`), collective notice:

  > The collective work is Copyright 2000-2018 by Kevin Atkinson as well as any of the
  > copyrights mentioned below:
  >
  > Copyright 2000-2018 by Kevin Atkinson
  >
  > Permission to use, copy, modify, distribute and sell these word lists, the associated
  > scripts, the output created from the scripts, and its documentation for any purpose is
  > hereby granted without fee, provided that the above copyright notice appears in all copies
  > and that both that copyright notice and this permission notice appear in supporting
  > documentation. Kevin Atkinson makes no representations about the suitability of this array
  > for any purpose. It is provided "as is" without express or implied warranty.

  Component notices for the levels used (all quoted in the same file): Moby Words *"has been
  place into the public domain. Use, sell, rework, excerpt and use in any way on any platform"*;
  Brian Kelk's UK frequency list *"That is the intention"* (public domain, quoted e-mail);
  *"The 12Dicts package and Supplement is in the Public Domain"*; ENABLE *"is herewith formally
  released into the Public Domain"*; WordNet 1.6 (used only to build the inflection database):
  *"Permission to use, copy, modify and distribute this software and database and its
  documentation for any purpose and without fee or royalty is hereby granted, provided that you
  agree to comply with the following copyright notice and statements"*; VarCon (Kevin Atkinson,
  Benjamin Titze: same permission wording as above) and its Ispell origin (Geoff Kuenning,
  BSD-style: retain the notice, mark modifications, no endorsement). UKACD's "include this
  document verbatim" clause applies only from level 80 up; not used, and the full `Copyright`
  is shipped anyway. Obligation met by shipping the notice in the About page's licence folder.

### 1b. Ranking: 12dicts 6.0.2, `Lemmatized/2+2+3frq.txt` (+ `2+2+3lem.txt` for inflections)

- Source: `https://downloads.sourceforge.net/wordlist/12dicts-6.0.2.zip`,
  sha256 `64ac1d35acb66b550c7ebc56e080b62e0bad8f5984d72059dc2e05ac48780e52` (1,992,138 bytes).
- Licence (ReadMe.html in the zip, sha256 `268f5a0af3a035833b3821b8fa0ba6e75ac7893aeca58cc518ebd6f6c4293f18`;
  the paragraph is lifted verbatim by the script into `12dicts-6.0.2-LICENSE.txt`):

  > The 12dicts lists were compiled by Alan Beale. I explicitly release them to the public
  > domain, but request acknowledgment of their use. (Actually, the dependency of the 2of12inf
  > list and the 2+2+3 lists on AGID prevents their release into the public domain. However, I
  > do not impose any additional requirements on their use beyond those imposed by AGID and its
  > sources, as described in agid.txt.) - Alan Beale -

  AGID's terms (`agid.txt`, shipped as `12dicts-6.0.2-agid.txt`, transcoded ISO-8859-1 → UTF-8):

  > Copyright 2000 by Kevin Atkinson
  >
  > Permission to use, copy, modify, distribute and sell this database, the associated scripts,
  > the output created form the scripts and its documentation for any purpose is hereby granted
  > without fee, provided that the above copyright notice appears in all copies and that both
  > that copyright notice and this permission notice appear in supporting documentation.

  AGID's own inputs are Moby (public domain), WordNet 1.6 (notice above), ENABLE2K and its
  supplement (public domain) — all quoted in `agid.txt`.
- Provenance of the frequencies: the ReadMe states the bands were computed *"using a commercial
  word frequency database, supplied by Brigham Young University, based on its Corpus of
  Contemporary American English (COCA)"* and, explicitly, that the previous edition's Google web
  data was dropped: *"In the previous version, word frequency information was obtained from data
  collected from the World Wide Web supplied by Google."* Only the 21 band labels are published,
  under Beale's terms above; no COCA data is redistributed.
- Band → count mapping (documented in the script). ReadMe: *"Band 21 contains lemmas whose words
  together appear between 16 and 31 times in the BYU data. Each other band contains lemmas of
  twice the frequency of the following band"*, so bands are log2-spaced and
  `count = 2^(30 − band)` preserves the ratios (band 1 → 536,870,912 … band 21 → 512).
  Inflections are omitted from 2+2+3frq (*"omitting … all regular inflections"*), so they are
  taken from the matching `2+2+3lem` entry and inherit the lemma's band. Matching is exact
  (case included; 2+2+3frq carries "I", "Monday" itself). 68,340 entries got a band this way.
  The 56,507 SCOWL words absent from 2+2+3frq (possessives, names, rarer words) get a band from
  their SCOWL level, chosen so cumulative sizes line up (level 10 ≈ 4.4k words ≈ frq bands 1–11;
  level 20 ≈ 12.6k ≈ bands 1–15; level 35 ≈ 50k ≈ past band 21), then one band per level:
  10 → 11 (524,288) · 20 → 15 (32,768) · 35 → 21 (512) · 40 → 22 (256) · 50 → 23 (128) ·
  55 → 24 (64) · 60 → 25 (32).
- Known artefacts, left as the source gives them: the 2+2+3 lemma grouping files archaic and
  slang forms under their headword, so "art", "wast" (under be), "ins" (in), "yous", "ya"
  (you) inherit bands 1–2; "I'm" sits at SCOWL level 35 and "I've"/"I'll" at 40 (SCOWL's
  contraction levels), so they rank 512 / 256 while "don't"/"it's" (level 10) rank 524,288.
  The phase's word-learning (Decisions, 2026-09-17 item 6) is the intended corrective.

### 1c. Rejected candidates (one line each)

- Google Books n-grams, Web1T / Norvig `count_1w`, `google-10000-english`, AOSP LatinIME
  dictionaries, HeliBoard/OpenBoard dictionaries (AOSP-derived): Google provenance (P5); not
  needed since a non-Google pair qualifies.
- `wordfreq` (rspeer), hermitdave/FrequencyWords (OpenSubtitles), Wiktionary frequency lists,
  Wikipedia-derived counts: data licensed CC-BY-SA (or derived from CC-BY-SA text); share-alike
  is excluded by the rules.
- SUBTLEX-US, COCA/BYU lists, Brown corpus lists: research-only / non-commercial / paid terms.
- Open American National Corpus frequency data (anc.org): the page
  `https://anc.org/data/anc-second-release/frequency-data/` carries **no licence statement at
  all** (fetched 2026-09-22; only a data-format description), and the host's TLS certificate had
  expired (`curl: (60) SSL certificate problem: certificate has expired`), so a pinned fetch
  would need `--insecure`. Unverifiable → rejected.
- Leipzig Corpora Collection (wortschatz.uni-leipzig.de): the download page is behind an Anubis
  JavaScript proof-of-work wall, so neither its terms nor a pinned download can be fetched by a
  script; rejected without reaching the licence.
- Leeds Internet corpora frequency lists (corpus.leeds.ac.uk): connection timed out twice
  (90 s); unreachable, and mixed UK/US web English anyway.
- Moby Words, ENABLE, `/usr/share/dict/words` (web2), dwyl/english-words: public domain but no
  frequency data and (ENABLE/web2) lower-case only; Moby and ENABLE are already inside SCOWL.
- Brian Kelk's UK frequency classes: public domain but the raw class list is not in the SCOWL
  tarball (only `r/uk-freq-class/notes.txt`), British spelling, no capitalised words; its
  classes are already folded into SCOWL's levels 10/20/35/50/70.
- SCOWL rel-2026.02.25 (SCOWLv2): COCA n-gram data under an unreadable NDA (see 1a).

## 2. Emoji artwork — Microsoft Fluent Emoji, Flat style, default skin tone

**Verdict: PASS.** MIT, verified at a pinned commit.
Output: **1,595 PNGs** (128×128, 8-bit RGBA, IHDR/IDAT/IEND only) + `index.tsv`,
**6,859,555 bytes total** (PNGs 6,755,193 + index 104,362; `du -sh` shows 9.8M because of
4 KiB blocks). Every Fluent asset matched exactly one fully-qualified emoji-test 17.0 sequence
and vice versa (the script asserts both directions).

- Source: `https://github.com/microsoft/fluentui-emoji.git` at commit
  `1ffb34c752ecf5d402f04cfb4b392c77f57c54bc` (main, 2026-08-24, "Pin GitHub Actions to
  full-length commit SHAs (#193)"), fetched with `git fetch --filter=blob:none --depth 1` and a
  non-cone sparse checkout of `/LICENSE`, `/assets/*/metadata.json`, `/assets/*/Flat/*.svg`,
  `/assets/*/Default/Flat/*.svg` (3,190 files). Content pin: sha256 over `sha256sum` of those
  files in byte order = `d32f5c93f3f9ed5a1c1469e98b0e8664b4cab0b0a9dc8832c205fd9724f9a008`.
- LICENSE at that commit (sha256 `c2cfccb812fe482101a8f04597dfc5a9991a6b2748266c47ac91b6a5aae15383`,
  shipped as `fluentui-emoji-MIT.txt`):

  > MIT License
  >
  > Copyright (c) Microsoft Corporation.
  >
  > Permission is hereby granted, free of charge, to any person obtaining a copy of this software
  > and associated documentation files (the "Software"), to deal in the Software without
  > restriction, including without limitation the rights to use, copy, modify, merge, publish,
  > distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
  > Software is furnished to do so, subject to the following conditions:
  >
  > The above copyright notice and this permission notice shall be included in all copies or
  > substantial portions of the Software.
  >
  > THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND […]

- Repository shape at the commit: 1,595 asset folders, each with `metadata.json` (`unicode`,
  `group`, `cldr`, `unicodeSkintones` …); 1,285 have `Flat/<name>_flat.svg`, 310 (the
  skin-tone-bearing ones) have `Default/Flat/<name>_flat_default.svg`. All 1,595 have Flat art.
  Only 8 flags exist (no country flags). Fluent's `unicode` omits some FE0F selectors, so
  matching ignores FE0F on both sides.
- Order and names: `https://www.unicode.org/Public/17.0.0/emoji/emoji-test.txt`
  (Version 17.0, dated 2025-08-04), sha256
  `1d8a944f88d7952f7ef7c5167fef3c67995bcae24543949710231b03a201acda`; 5,225 entries, 3,944
  fully-qualified; only fully-qualified rows are used (not component / minimally-qualified /
  unqualified). `/Public/emoji/` lists releases only up to 16.0; 17.0 lives under
  `/Public/17.0.0/emoji/`.
- Unicode licence: `https://www.unicode.org/license.txt`, sha256
  `e7a93b009565cfce55919a381437ac4db883e9da2126fa28b91d12732bc53d96` (shipped as
  `unicode-emoji-LICENSE.txt`; the file is unversioned, so a copyright-year roll-over will
  trip the pin and needs a re-read and re-pin):

  > UNICODE LICENSE V3 — COPYRIGHT AND PERMISSION NOTICE — Copyright © 1991-2026 Unicode, Inc.
  > […] Permission is hereby granted, free of charge, to any person obtaining a copy of data
  > files and any associated documentation (the "Data Files") or software and any associated
  > documentation (the "Software") to deal in the Data Files or Software without restriction,
  > including without limitation the rights to use, copy, modify, merge, publish, distribute,
  > and/or sell copies of the Data Files or Software, and to permit persons to whom the Data
  > Files or Software are furnished to do so, provided that either (a) this copyright and
  > permission notice appear with all copies of the Data Files or Software, or (b) this
  > copyright and permission notice appear in associated Documentation.

- Rasteriser: **Inkscape 1.1.2** (installed), one `--shell` process for all files
  (`export-area-page`, 128×128, background opacity 0), ~15 s. Chosen over the alternatives
  because it renders the Fluent SVGs faithfully (checked visually: faces, keycaps, ZWJ
  sequences, gradients, clip paths) and is already on the host; `cairosvg` via `uvx` rendered
  the samples at the wrong size (1.1 KB, 32 px-looking output) and would add a Python
  dependency; ImageMagick 6's `convert` rendered correctly but delegates SVG parsing to whatever
  library it was built with, which is not pinned. Inkscape's pHYs and tEXt("Software") chunks
  are stripped by a 30-line Python chunk filter so the PNG bytes carry no tool metadata.
- `index.tsv` columns: `codepoints_hex_space_separated` (as emoji-test prints them, e.g.
  `1F3F3 FE0F 200D 1F308`) · `unicode_string` · `unicode_group` · `unicode_subgroup` ·
  `cldr_name` (emoji-test's CLDR short name) · `png_file_name` (lower-case code points joined
  by `-`, e.g. `1f3f3-fe0f-200d-1f308.png`). Rows are in emoji-test order.
- Counts per Unicode group: Smileys & Emotion 168 · People & Body 343 · Animals & Nature 158 ·
  Food & Drink 130 · Travel & Places 218 · Activities 85 · Objects 262 · Symbols 223 · Flags 8.

## 3. FlorisBoard reuse boundaries

**Verdict: reuse allowed, Apache-2.0 throughout the parts a Kotlin IME would take; no NOTICE
file exists, so §4(d) does not apply; §4(a)–(c) do.**

- Pinned: `https://github.com/florisboard/florisboard.git` commit
  `5d6e1ef824e32b128fda890b0ac2dce645b23f5c` (main, 2026-08-21, "fix: replace Title Case with
  Sentence case (#3320)"), 890 tracked files. `LICENSE` sha256
  `ef12461367711fc4c32c42e810976c67495a244714d8dc59503504a3d29cfceb` = the unmodified Apache
  License 2.0 text ("Apache License / Version 2.0, January 2004 /
  http://www.apache.org/licenses/"). README §License: *"Copyright 2020-2026 The FlorisBoard
  Contributors / Licensed under the Apache License, Version 2.0 (the "License"); …"*.
- Header sweep of every `.kt/.java/.rs/.c/.cpp/.h/.py`: all carry
  `Copyright (C) 20xx-2025/2026 The FlorisBoard Contributors` + Apache-2.0 header, except:
  `lib/compose/…/icons/KeyboardForwardDelete.kt` (Copyright 2025 The Android Open Source
  Project, Apache-2.0 — a Material icon path), and files with no header at all
  (`lib/native/src/main/rust/src/lib.rs`, `lib.c`, `libnative/dummy/src/lib.rs`, 10 snygg unit
  tests, 3 `utils/*.py`), which fall under the repository LICENSE. SPDX tags: 2 × Apache-2.0,
  1 × CC-BY-4.0 (`AI_POLICY.md`, a policy document, not code). Third-party library notices are
  rendered at runtime via mikepenz AboutLibraries (`ThirdPartyLicensesScreen.kt`), so there is
  no bundled NOTICE/THIRD_PARTY file.

| Component | Files (all `app/src/main/…` unless noted) | Header | Licence |
|---|---|---|---|
| Glide / swipe typing classifier | `kotlin/dev/patrickgold/florisboard/ime/text/gestures/StatisticalGlideTypingClassifier.kt` (669 lines), `GlideTypingClassifier.kt`, `GlideTypingGesture.kt`, `GlideTypingManager.kt`, `SwipeGesture.kt`, `SwipeAction.kt` | `Copyright (C) 2025 The FlorisBoard Contributors` (SwipeGesture/SwipeAction 2020-2025) | Apache-2.0. The classifier's doc comment credits the method to Étienne Desticourt's write-up in AnySoftKeyboard PR #1870 (AnySoftKeyboard is Apache-2.0); the code is FlorisBoard's own. Depends only on androidx.collection, `java.text.Normalizer`, and FlorisBoard's `TextKey`/`KeyData`/`Subtype` types plus `nlpManager` for the word list — portable with those replaced by Tessera's key model and `en_US.tsv`. |
| Suggestion / spelling framework | `ime/nlp/NlpManager.kt`, `NlpProviders.kt`, `SuggestionCandidate.kt`, `SpellingResult.kt`, `PunctuationRule.kt`, `BreakIteratorGroup.kt`, `ime/dictionary/DictionaryManager.kt`, `UserDictionary.kt` | FlorisBoard Contributors, 2021/2022-2025 | Apache-2.0. **Note: there is no Latin suggestion algorithm to port.** `ime/nlp/latin/LatinLanguageProvider.kt` is a stub: `spell()` returns canned "typo1/typo2/typo3" for the word "typo", `suggest()` returns `emptyList()`, and `preload()` reads a test map from `assets/ime/dict/data.json` (807 KB word→int, no stated provenance; do not reuse). Only the interfaces/plumbing are reusable. |
| Emoji data | `assets/ime/media/emoji/root.txt` (3,965 lines), `en.txt`, `de/es/fr/it/pt.txt` | `# Auto-generated by emojicon.py using CLDR v48` — no copyright line; generator not in the repo | Derived from Unicode CLDR (Unicode License V3). Not needed: `index.tsv` from emoji-test.txt covers order, groups and names. |
| Emoji code | `ime/media/emoji/Emoji.kt`, `EmojiData.kt`, `EmojiCategory.kt`, `EmojiSet.kt`, `EmojiHistory.kt`, `EmojiPaletteView.kt`, `EmojiSuggestionProvider.kt`, `FlorisEmojiCompat.kt` | FlorisBoard Contributors, 2022/2024-2025 | Apache-2.0. |
| Layouts | `assets/ime/keyboard/org.florisboard.layouts/extension.json` (`"license": "apache-2.0"`, per-layout `authors`; qwerty: patrickgold) + `layouts/{characters (76 files), charactersMod, numeric, numericAdvanced, numericRow, phone, phone2, symbols, symbols2, symbols2Mod, symbolsMod}/*.json`; loaders `ime/keyboard/LayoutManager.kt`, `KeyData.kt`, `ime/text/keyboard/TextKeyboard.kt` | JSON has no headers; licence declared in `extension.json` meta; Kotlin headers FlorisBoard Contributors 2021-2025 | Apache-2.0. Sibling packs `org.florisboard.composers`, `.currencysets`, `.localization`, themes, languagepack: all `"license": "apache-2.0"` in their `extension.json`. Phase 05's W10M geometry comes from R6 §2, so at most the JSON key-code convention is worth borrowing. |
| Native | `lib/native` (Rust JNI stub, `dummy` crate), `libnative/dummy` | none | Repository Apache-2.0; nothing an IME needs. |

Obligations when a file is ported (Apache-2.0 §4, quoted from the pinned LICENSE):
*"(a) You must give any other recipients of the Work or Derivative Works a copy of this License;
and (b) You must cause any modified files to carry prominent notices stating that You changed
the files; and (c) You must retain, in the Source form of any Derivative Works that You
distribute, all copyright, patent, trademark, and attribution notices from the Source form of
the Work, excluding those notices that do not pertain to any part of the Derivative Works; and
(d) If the Work includes a "NOTICE" text file as part of its distribution, then any Derivative
Works that You distribute must include a readable copy of the attribution notices contained
within such NOTICE file […]"*. Concretely for Tessera: keep the `Copyright (C) … The
FlorisBoard Contributors` + Apache header at the top of every ported file, add a line such as
`Modified for Tessera (app.tileshell) 2026: <what changed>` in that header, ship the Apache-2.0
text in `app/src/main/assets/licenses/` (the existing `kokoro-Apache-2.0.txt` is the same text;
a `florisboard-Apache-2.0.txt` copy with the FlorisBoard copyright line on top makes the About
page attribution explicit), and nothing for (d) because FlorisBoard has no NOTICE file. If the
AOSP icon file were taken, its "The Android Open Source Project" line stays too.

## 4. `tools/fetch-keyboard.sh` — reproducibility proof

Inputs pinned by sha256 (SCOWL tarball, 12dicts zip, emoji-test.txt, license.txt) or by commit
plus a content sha256 (Fluent); every mismatch exits 1 before any output is written. Requires
curl, tar, unzip, git ≥ 2.34, perl, python3, Inkscape 1.x, sha256sum.

Run 1, clean state (`rm -rf .keyboardsrc app/src/main/assets/keyboard` and the five licence
files first), `tools/fetch-keyboard.sh`, exit 0:

```
fetch scowl-2020.12.07.tar.gz
fetch 12dicts-6.0.2.zip
fetch emoji-test-17.0.txt
fetch unicode-license.txt
fetch fluentui-emoji@1ffb34c752ec (sparse: Flat SVGs + metadata)
ok    fluentui-emoji tree sha256 matches
en_US.tsv: 124847 entries (68340 ranked by 2+2+3frq, 56507 by SCOWL level); per SCOWL level {10: 4444, 20: 8147, 35: 37756, 40: 7425, 50: 44932, 55: 6531, 60: 15612}
emoji: 1595 fully-qualified sequences have Fluent Flat artwork (1595 Fluent assets)
render emoji with Inkscape 1.1.2 (0a00cf5339, 2022-02-04) (one --shell process)
emoji: 1595 PNGs written, 6859555 bytes including index.tsv
   168  Smileys & Emotion
   343  People & Body
   158  Animals & Nature
   130  Food & Drink
   218  Travel & Places
    85  Activities
   262  Objects
   223  Symbols
     8  Flags

payload:
1.8M	app/src/main/assets/keyboard/en_US.tsv
9.8M	app/src/main/assets/keyboard/emoji

checksums:
  en_US.tsv  2994817eecd128fbee5d270e1c0ffff02c4dfaae719bfb5ef419780281cc3ca3
  index.tsv  b43e19938cd37ac27ddfdddfab23fbd91cedd9d46a711b83890e51b483f58e05
  emoji PNGs 1511ceeac695f140c3375b107b8099c9199f3530dc3dbbd10c9838f440abb36c  (all PNGs concatenated in byte order)
```

Run 2, `TZ=Asia/Tokyo LC_ALL=C tools/fetch-keyboard.sh` (downloads cached — `have …` lines —
everything regenerated), exit 0, identical output apart from `fetch` → `have`, and the same
three checksums. Independent hashes over copies of both runs' outputs
(`sha256sum en_US.tsv`; `sha256sum emoji/index.tsv`; `LC_ALL=C ls *.png | LC_ALL=C sort | xargs
cat | sha256sum`; the five licence files concatenated):

```
RUN 1                                                                      RUN 2
en_US.tsv 2994817eecd128fbee5d270e1c0ffff02c4dfaae719bfb5ef419780281cc3ca3  same
index.tsv b43e19938cd37ac27ddfdddfab23fbd91cedd9d46a711b83890e51b483f58e05  same
PNGs      1511ceeac695f140c3375b107b8099c9199f3530dc3dbbd10c9838f440abb36c  same (1595 files, 6755193 bytes)
licences  1ea957279d496c3a83695c6057e10daeaf52c3d81cbc995c6cc53dc5348a4ce8  same
diff -r run1-assets run2-assets → IDENTICAL
```

A third and fourth run (after the negative tests below) produced the same three checksums.

Negative tests (the script must fail, not fall through):

```
# SCOWL_SHA replaced by zeros in a temporary copy of the script, cached tarball moved aside
fetch scowl-2020.12.07.tar.gz
sha256 mismatch for https://downloads.sourceforge.net/wordlist/scowl-2020.12.07.tar.gz: got 5587667c… want 0000…
exit=1

# one byte-level change appended to a cached Fluent SVG
have  fluentui-emoji@1ffb34c752ec
fluentui-emoji tree sha256 mismatch: got e372fb67991174be0a36919c99a6cd076ed06fffa6e1299a3e0c431594713c2b want d32f5c93…
(the sparse checkout does not hold the pinned files; delete .keyboardsrc/fluentui-emoji and rerun)
exit=1
```

Files this check adds (nothing else touched; no Kotlin, Gradle, manifest or phase doc):
`tools/fetch-keyboard.sh`, this file, and a `.gitignore` block for `.keyboardsrc/`,
`app/src/main/assets/keyboard/` and the five fetched licence files (`git check-ignore`
confirmed). Generated payload lives only in the ignored paths.

---

## 4. Show / hide: can the input method own the slide? (Decisions "Show / hide", UNVERIFIED) — NO

Checked at build start by building the keyboard with **no show or hide animation of its own** (the panel
is drawn at rest from its first frame; `KeyboardView` has no animation on appearing) and capturing the
window coming up and going down at 60 fps (`E3M/`, method in `qa/phase-05/README.md`):

* in: the panel's edge travels from the nav bar to rest in 167 ms after its first visible frame, 90 % of
  the travel in 117 ms, and its first frame is only 33 % opaque — the window is also **faded** in;
* out: off-screen in 117 ms, fading as it goes (opacity 0.88 at the start of the move, 0.52 half way).

Motion the keyboard never draws is on the screen, so it is applied to the window from outside: since
Android 11 the IME window's show and hide are an insets animation run by the focused app's
`InsetsController` on the IME window's leash, and `InputMethodService` exposes no API that sets its curve,
duration or alpha. The Decisions' failure branch therefore applies as written: the system's slide is
used, the result is recorded (INDEX Change Log, 2026-09-22), H10 asks Jeremy to accept it, and E3's
show / hide clause checks only what the IME draws (the press popup, E3M).

## 5. The band under a raised panel (Decisions "Moving the keyboard", UNVERIFIED) — YES

`onComputeInsets` with `TOUCHABLE_INSETS_REGION` set to the panel gives a see-through, non-touchable band
below a raised panel: E9 raises the panel 649 px and a tap on `bottom_field`, which then lies in that band,
focuses it (`E9/E9.txt`).
