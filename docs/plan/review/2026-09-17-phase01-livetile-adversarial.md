# Phase 01 — adversarial review of the Live Tile API trust surface (opus), 2026-09-17

The provider's own call() surface holds: identity comes from Binder, shared uids are refused, an owner extra naming
another app is refused, XML is gated lexically before any parser sees it, image URIs must belong to the caller, and
install identity is signer plus first-install time, re-checked on every call. Every serious hole is outside call().

## Findings
- F1 HIGH: the legacy badge broadcast lets any app set or clear any other app's badge. getSentFromPackage() is only
  non-null when the sender opts in, which no real sender does, so the owner is whatever package string the sender
  puts in the extra. No rate limit, the per-app kill switch does not cover this source, a victim app that never used
  the API has no row in Settings to turn off, and the documented 3-day expiry is never evaluated on its own.
- F2 HIGH: an image URI is read from the caller's own provider with no read timeout and no cap on concurrent
  ingests, on the incoming binder thread; about 16 hung calls exhaust the shell's binder pool - and this is the Home
  app.
- F3 MEDIUM: a 4096-px image inside a 200 KB file decodes to ~4 MB, up to 9 faces per owner, and render() decodes the
  whole queue from disk on every accepted verb, inside the store lock, on the caller's thread.
- F4 MEDIUM: images are written to disk before the 16 MB quota is checked, so an owner already at quota can force
  ~144 MB/min of write-then-delete churn while every call correctly returns error=quota.
- F5 MEDIUM: the Samsung badge authority is read without checking that it belongs to a system package, so on a device
  where that authority is free a malicious app can serve badge counts for every package.
- F6 MEDIUM-LOW: image rejection details name the package that owns an authority, which turns the provider into a
  package-visibility oracle for apps with no <queries> declaration.
- F7 MEDIUM-LOW: the per-package change URI is notified on an exported provider with no read permission, so any app
  can observe when another app updated its tile.
- F8 LOW: a restored or hand-edited state file's image "file" field is used as a path without validation, and the app
  declares no backup rules, so both the stored state and the kill-switch preferences are in the backup set.
- F9 LOW: there is no global Live Tile API switch, and the per-app rows only exist for apps that already called.
- F10 LOW: rate-limit state is process-local and keyed per package, so a restart refills every budget.
- F11 INFO: uninstall cleanup may ride only on the runtime receiver; the manifest receiver's delivery to a stopped
  shell is version-dependent and was not verified on device.

The full report, including what the design gets right, is the reviewer's text kept with this file's commit.
