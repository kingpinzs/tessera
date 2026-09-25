# Phase 15 — Calculator key vocabulary and the calc-cases.tsv schema

One vocabulary shared by three things that must agree: the Calculator UI's key tags (`calc_key:<name>`), the engine's
command names, and the fixture table docs/plan/qa/phase-15/calc-cases.tsv that E11, E12, E26 and E29 drive. A key name is
a PHYSICAL key on the W10M (10586) layouts of r11/calculator.md (§2.8 Standard, §4.11–4.12 Programmer, §6.1 Scientific),
never an internal command: an inverse function is typed as `inv` then its base key, exactly as a person presses them.

## Key names

Digits `0`–`9`, hex digits `A`–`F` (Programmer only), and:

| Name | Glyph | Modes | Notes |
|---|---|---|---|
| `decimal` | . | S, Sc, P (disabled in P) | |
| `add` `subtract` `multiply` `divide` | + − × ÷ | all | |
| `equals` | = | all | |
| `negate` | ± | all | |
| `percent` | % | S | Windows' percent (80 + 15 % = → 92) |
| `sqrt` | √ | S, Sc | |
| `square` | x² | S, Sc | Sc: `inv square` = x³ |
| `reciprocal` | ¹⁄x | S (Sc under `inv`, per the source) | |
| `clear_entry` `clear` `backspace` | CE C ⌫ | all | |
| `mc` `mr` `mplus` `mminus` `ms` | MC MR M+ M- MS | S, Sc; P has `ms` | `mlist` (M˅) opens the memory flyout, not typed in cases |
| `inv` | ↑ | Sc, P | the 2nd-function toggle |
| `pow` | xʸ | Sc | `inv pow` = ʸ√x |
| `sin` `cos` `tan` | | Sc | `inv sin` = sin⁻¹ … |
| `pow10` | 10ˣ | Sc | `inv pow10` = eˣ |
| `log` | log | Sc | `inv log` = ln |
| `exp` | Exp | Sc | scientific-notation entry (1 `exp` 3 = 1e+3), NOT eˣ |
| `mod` | Mod | Sc, P | |
| `pi` | π | Sc | |
| `factorial` | n! | Sc | |
| `lparen` `rparen` | ( ) | Sc, P | |
| `angle` | DEG / RAD / GRAD | Sc | cycles DEG → RAD → GRAD → DEG |
| `hyp` | HYP | Sc | hyperbolic toggle |
| `fe` | F-E | Sc | forces e-notation display |
| `lsh` `rsh` | Lsh Rsh | P | `inv lsh` / `inv rsh` = rotate, per the source |
| `or` `xor` `not` `and` | Or Xor Not And | P | |
| `radix_hex` `radix_dec` `radix_oct` `radix_bin` | the HEX / DEC / OCT / BIN rows | P | tapping a row selects that radix |
| `word` | QWORD / DWORD / WORD / BYTE | P | cycles QWORD → DWORD → WORD → BYTE → QWORD |

Where the modern source repo's layout differs from the 10586 layout r11 measured, the 10586 layout wins for WHICH keys
exist; the source wins for WHAT a key computes.

## calc-cases.tsv

UTF-8, tab-separated, one header line, then one case per line; `#` starts a comment line. Columns:

| Column | Content |
|---|---|
| `id` | unique, `<mode>-<nnn>` |
| `mode` | `standard` · `scientific` · `programmer` · `converter` · `date` · `tess` |
| `setup` | space-separated `key=value` settings applied before the keys (empty = defaults): `angle=deg\|rad\|grad`, `radix=hex\|dec\|oct\|bin`, `word=qword\|dword\|word\|byte`; converter: `category=<name as the list shows it> from=<unit name> to=<unit name>`; date: `op=difference\|add\|subtract` |
| `keys` | standard / scientific / programmer: space-separated key names from the table above, pressed in order from a cleared calculator (the driver presses `clear` first); converter: the digits and `decimal` typed into the "from" field; date: `from=YYYY-MM-DD to=YYYY-MM-DD` (difference) or `from=YYYY-MM-DD years=<n> months=<n> days=<n>` (add / subtract); tess: empty |
| `expected` | exactly the text the display shows afterwards — the result line (standard / scientific / programmer: the main display with the en-US digit grouping, "," every three digits; programmer: the display in the current radix with its grouping, r11/calculator.md 4.5), the "to" field (converter), or the result text (date; two result lines joined with ` \| `); an error is its string exactly ("Cannot divide by zero") |
| `tess_utterance` | tess rows only: the typed request, words only (no apostrophes, no "%": "percent") |
| `tess_reply` | tess rows only: the full reply sentence, from T15-52's rule |
| `source` | why this expectation holds: the microsoft/calculator file:line whose rule it follows (at 4fd3fc53bade573ea2ac1e1ebad9bf603713f85e), or `host` for plain arithmetic |

Expectations are computed on the host (Python `fractions` / `decimal` / `datetime`) from the rules in the Microsoft
source — NEVER by running the app's engine.
