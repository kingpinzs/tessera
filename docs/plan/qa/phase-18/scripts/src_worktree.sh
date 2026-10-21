#!/usr/bin/env bash
# src_worktree.sh <path> <commit> — a detached git worktree of this repository at <commit>, ready for gradle.
#
# Why (2026-10-20, phase 18's resume): step 0 fast-forwarded phase-18 onto phase-04, so app/ at HEAD carries phase 20's
# and phase 04's code and is no longer the gate build's source (5ba4238c). A row that compiles tests "from the gate
# build's source" (rowsb.sh's JVM frame) runs gradle in a checkout of the gate commit made here, never in this worktree.
#
# The git-ignored build inputs (the speech, keyboard and OpenCV payloads, the licence files, local.properties) are
# HARD-LINKED from this worktree with `cp -al`, never copied (~/projects/CLAUDE.md; the shared files stay read-only).
# An existing worktree at <path> is reused when its HEAD is <commit> and app/ is clean; anything else is refused.
set -uo pipefail
dest="${1:?path}"; commit="${2:?commit}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../../../.." && pwd)"
want="$(git -C "$REPO" rev-parse --verify "$commit^{commit}")" || { echo "src_worktree: no commit $commit" >&2; exit 2; }

if [ -e "$dest" ]; then
  have="$(git -C "$dest" rev-parse HEAD 2>/dev/null)"
  [ "$have" = "$want" ] || { echo "src_worktree: $dest exists at ${have:-not a worktree}, not $want" >&2; exit 3; }
  [ -z "$(git -C "$dest" status --porcelain -- app)" ] || { echo "src_worktree: $dest has changes under app/" >&2; exit 4; }
else
  git -C "$REPO" worktree add --detach "$dest" "$want" >/dev/null 2>&1 || { echo "src_worktree: git worktree add failed" >&2; exit 5; }
fi

for p in app/libs app/src/main/assets/speech app/src/main/assets/keyboard .opencvsrc \
         app/src/main/assets/licenses/12dicts-6.0.2-LICENSE.txt app/src/main/assets/licenses/12dicts-6.0.2-agid.txt \
         app/src/main/assets/licenses/fluentui-emoji-MIT.txt app/src/main/assets/licenses/scowl-2020.12.07-Copyright.txt \
         app/src/main/assets/licenses/unicode-emoji-LICENSE.txt; do
  [ -e "$REPO/$p" ] || continue
  [ -e "$dest/$p" ] && continue
  mkdir -p "$(dirname "$dest/$p")"
  cp -al "$REPO/$p" "$dest/$p" || { echo "src_worktree: cp -al $p failed" >&2; exit 6; }
done
[ -e "$dest/local.properties" ] || cp "$REPO/local.properties" "$dest/local.properties"
echo "$dest $(git -C "$dest" rev-parse --short=9 HEAD)"
