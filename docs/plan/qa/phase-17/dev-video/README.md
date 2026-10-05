# Phase 17 — Movies & TV: development proof (not the gate)

What the Movies & TV builder ran on the shared emulator (`emulator-5554`, tileshell_fhd) while building tasks 7, 11–15
and 17 (5). The gate's rows (E11–E15, E18–E23, the edge cases) are the lead's, written from the phase doc; these rows
only show that each part did what its brief asked before it was handed back. Evidence folders (`<ROW>/`, earlier runs
kept as `<ROW>-run<n>/`) stay on the machine that ran them and are never committed.

## How a row runs

Every script sources `scripts/v17.sh` first: it takes the device lock, installs this worktree's debug APK when the
device holds another build (`V17_APK` names a kept copy of a build), and defines the helpers. Each row restores what it
changed. The Videos grant is given for a row when the device lacks it and put back as it was found.

- Fixtures: `scripts/make_fixtures.sh <dir>` (a 10-s video, one flat colour per second; a truncated copy, an empty file,
  a copy with an `.srt` beside it, a rotated copy). Never in the repo.
- The catalogue fixture: `../scripts/catalogue_server.py` (TMDB, the image host, Wikidata, an http video).
- The media-server fixture: `../fixtures/jellyfin/jellyfin_fixture.sh` (the pinned Jellyfin 12.1 container; its README
  records what differs from BS-5 on this image).

**Port 8090 is taken on this PC.** The doc fixes the catalogue fixture at `10.0.2.2:8090`; here `0.0.0.0:8090` is
published by another service of the owner's (a docker container), so the AVD's `10.0.2.2:8090` reaches THAT service.
These rows run the fixture on **8091** (`V17_PORT`), and every line that names the fixture's address reads
`10.0.2.2:8091`. The server answers `/3/configuration` with its own port in the image base.

## The rows

| Script | Row | What it shows |
|---|---|---|
| `a_player.sh` | A_PLAYER | a MediaStore video in the player: the picture at 3.5 s, a 70 % seek, pause, Y5's chrome from a dump, the fade's `[motion]` lines, the menu, the `video` session and `[music] session app.tileshell id=video -> none`, the conditional CC and a subtitle line, no session after Back |
| `a_sources.sh` | A_SOURCES | `http://` playing, airplane mode's "Can't reach this video", a 404, `rtsp://`, a truncated and an empty file, `:video` killed with the launcher's pid unchanged |
| `a_focus.sh` | A_FOCUS | the video pauses the shell's Music, the media key pauses the video, Music does not resume after Back |
| `a_odd.sh` | A_ODD | a rotated file upright, Zoom to fill, Cast to device, full screen, Autoplay, Repeat, an APK update mid-play leaving no session |
| `b_hub.sh` | B_HUB | the chrome and the pane against E19's values, My videos (tiles, caption, no duration, the observer, the tap into the player), settings and About, the Videos grant from the empty state |
| `c_catalogue.sh` | C_CATALOGUE | no key → no request; the key typed and saved; three results with artwork; `bearer ok` on every API request and no token to the image host; the title page; offline; `/500`, `/429`, `/401` and Replace; a stopped fixture; the refresh after the cache's time is moved back; no artwork; no result; Remove |
| `c_watch.sh` | C_WATCH | "Watch on": not installed, installed with no restart, the title link, the search link, a title on no service, the Wikidata answer cached, the fixture app uninstalled |
| `d_server.sh` | D_SERVER | a wrong password; Add a server; the library; direct play; the token across a force-stop and only as ciphertext; the stopped container; a revoked token; removal clearing the token and the dynamic shortcut |
| `d_insecure.sh` | D_INSECURE | the insecure-server prompt: asked first, Cancel sends nothing (read on a server that would have seen it), Continue signs in; a private address is not asked about |
| `e_shortcuts.sh` | E_SHORTCUTS | the two static shortcuts and their pages' pane rows; no `video_mediaserver` with no server |
| `v_shots.sh` | V_SHOTS | screenshots of the designed pages, recorded only |

## Tags the pages carry (what a gate row can read)

- **Player** (`PlayerScreen.kt`): `player_root`, `video_surface` (the picture's own box), `player_scrim`, `player_track`,
  `player_played`, `player_thumb`, `player_seek`, `player_elapsed`, `player_remaining`, `player_cc`, `player_back10`,
  `player_playpause`, `player_fwd30`, `player_fullscreen`, `player_more`, `player_menu`,
  `player_menu:<cast|zoom|repeat|autoplay>` (a setting that is on reads `checked="true"`), `player_error`,
  `player_subtitle`. The controls leave the tree when they have faded out.
- **Hub frame** (`VideoApp.kt`): `hub_root`, `hub_header`, `hub_menu`, `hub_header_title`, `hub_search`, `hub_pane`,
  `hub_pane:<myvideos|browse|mediaserver|settings>` (the current one `selected="true"`), `hub_pane_label:<id>`,
  `hub_pane_bar`, `hub_pane_rule`; each page's root is `hub_page:<myvideos|browse|mediaserver|settings|about|tmdbkey|server|title>`.
  `hub_title` is the TITLE PAGE's title, as the doc uses it; the header's own text is `hub_header_title`.
- **My videos**: `video_group:<folder>`, `video_tile:<id>` (the 112-epx square), `video_caption:<id>`, `video_empty`,
  `video_grant`.
- **Browse**: `hub_search_box`, `hub_browse_notice`, `hub_key_link`, `hub_result:<id>`, `hub_result_title:<id>`,
  `hub_result_year:<id>`, `hub_result_image:<id>` or `hub_result_placeholder:<id>`, `hub_browse_empty`,
  `hub_section:<trending|movies|tv>`, `hub_section_all:<id>`, `hub_poster:<section>:<id>`, `hub_attribution`. A film's id
  is its TMDB number; a series' is `tv-<number>` (the two kinds can share a number).
- **Title page**: `hub_title`, `hub_title_facts`, `hub_overview`, `hub_overview_more`, `hub_title_poster`,
  `hub_watch:<service>`, `hub_watch_label:<service>`, `hub_watch_none`, `hub_watch_gone`, `hub_justwatch`,
  `hub_title_notice`. QA-Flix's service id is `qa-flix`.
- **Settings**: `hub_settings:<tmdbkey|server|about>`; `tmdb_key_status`, `tmdb_key_field`, `tmdb_key_save`,
  `tmdb_key_remove`; `hub_about_name`, `hub_about_logo`, `hub_about_tmdb`, `hub_about_justwatch`.
- **Media server**: `server_host`, `server_user`, `server_password`, `server_connect`, `server_error`,
  `server_insecure`, `server_insecure_continue`, `server_insecure_cancel`, `server_current`, `server_remove`,
  `server_notice`, `server_group`, `server_item:<id>`, `server_caption:<id>`.

The two credential fields (`tmdb_key_field`, `server_password`) are secret fields: a UI dump reads dots, never the value.

## Lines beyond the doc's list (all `[video]` unless tagged)

`player ready (session id video)`, `player released`, `subtitle file beside <id>: found|none`,
`tracks: video=<n> audio=<n> text=<n>`, `captions on|off`, `autoplay next <id>`, `cast: …`, `access=DENIED`,
`videos permission request: granted|denied`, `TMDB key saved|removed`, `server stream <address without its query>`,
`wikidata: busy, not asked again for <n> s`, `wikidata: error <code|connect>`, and the credential store's
`[cred] <name>: saved | removed | nothing to remove | unreadable (<class>) | not saved (<class>)`.
