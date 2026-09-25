# Edge agent T — Tess arithmetic edge cases, emulator-5556

Read `briefs/qa-edge-common.md` first; every rule there applies. Your emulator is **emulator-5556**. E26 (`e26.sh`)
passes on the final build except its 8 reply-audibility checks, which the owner accepted for a host-audio reason; it
shows how Tess is asked, typed and spoken (the speech route is `vmic5556`), and how a reply is read. The oracle is
`scripts/gen_calc_cases.py` (the host port of Windows' rules; import it and call `run_keys` / its Tess helpers for an
expected value, never read the app for one).

## Your cases (the phase doc's "Tess arithmetic (T15-2):" bullet); E26 already proves "point five times four" (2),
"negative three times four" (−12) and an overflow ("Overflow."): cite those checks, do not repeat them.
Row EDGE_TESS (`edge_tess.sh`):
1. A 1,000-digit result, spoken in the engine's e-notation (H17): an utterance whose exact result has about 1,000
   digits. The expected reply comes from the oracle.
2. A unit the Converter lacks: "5 parsecs in miles" is not understood, and no `[calc] tess` line is logged.
3. A conversion across categories: "5 miles in kilograms" is not understood.
4. An arithmetic request while the Calculator app shows another value: the app's display is unchanged afterwards (read
   `calc_display` before and after).
Each case typed; case 1 also spoken, as E26 does. Restore: the Calculator's display and history as found, Tess idle,
Home.
