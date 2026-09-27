# The stubs behind the red unit logs (review/2026-09-26-L13-345-fix-review-b.md N8)

Each stub was a one-line change to `OverlayItemPress.lift` in `app/src/main/kotlin/app/tileshell/ui/components/ModalOverlay.kt`,
run through `./gradlew :app:testDebugUnitTest --tests 'app.tileshell.ui.components.ModalOverlayTest'`, then restored.

| Red log | The stub | Fails |
|---|---|---|
| `red-ModalOverlayTest-L13-5-stub-gradle.txt` (11 tests) | `val runs = pressed && onItem` in place of `pressed && onItem && lifted`: the lift ignores `lifted`. This is reconstructed from the fix plan's wording ("a stub that ignores `lifted`"); the edit itself was not kept. | `theOverlayRemovedUnderTheFingerRunsNothing` |
| `red-ModalOverlayTest-L13-3-open-stub-gradle.txt`, `-open-stub.xml` (12 tests) | `sed -i 's/val runs = pressed \&\& onItem \&\& lifted \&\& open/val runs = pressed \&\& onItem \&\& lifted/'` (the exact command used): the lift ignores `open`. | `aLiftAfterTheOverlayWasDismissedRunsNothing` |

Green: `green-ModalOverlayTest-L13-3.xml`, 12 of 12, on the restored source.
