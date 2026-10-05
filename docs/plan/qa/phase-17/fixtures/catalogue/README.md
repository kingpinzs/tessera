# Phase 17 — the catalogue fixture's recorded answers

Hand-written JSON in TMDB's v3 shape and in the Wikidata Query Service's shape, served by
`../../scripts/catalogue_server.py` on port 8090 (the AVD reaches it at `10.0.2.2:8090`). Nothing here was fetched with
a key: the values are fixtures, the SHAPES are TMDB's (`BUILDSTART/README.md` BS-5) and Wikidata's (BS-4).

| File | Route | What it is |
|---|---|---|
| `configuration.json` | `/3/configuration` | `images.secure_base_url` = `http://10.0.2.2:8090/img/` (T17-20); the server answers with its OWN port in it, so a fixture run on another port never sends the app to 8090 |
| `search_blade_runner.json` | `/3/search/multi?query=Blade Runner` | exactly three titles: "Blade Runner" 1982 (movie 78), "Blade Runner 2049" 2017 (movie 335984), "Blade Runner: Black Lotus" 2021 (tv 117884), plus a person row the app must drop |
| `search_no_artwork.json` | `/3/search/multi?query=No Artwork` | one title whose `poster_path` is null (the poster placeholder) |
| `search_empty.json` | any other query | no result (the empty line) |
| `sections.json` | `/3/trending/all/week`, `/3/movie/popular`, `/3/tv/popular` | Browse's three strips |
| `movie_78.json`, `movie_335984.json`, `tv_117884.json`, `movie_900001.json` | `/3/movie/<id>`, `/3/tv/<id>` | title pages |
| `providers.json` | `/3/<movie|tv>/<id>/watch/providers` | 78 and 335984 are on QA-Flix (provider 9001); 117884 is on no service; 900001 is on Netflix only |
| `wikidata.json` | `/wikidata/sparql?query=…` | the answer per TMDB id: 335984 has a QA-Flix id (`qa-2049`), 78 has none |
| `posters.json` | `/img/<size>/<name>.png` | each poster's flat colour; the server draws the PNG (no image file is kept) |

The fixture's Wikidata property for QA-Flix is `P99990001` (not a real property), bound to the variable `qaflix`.
The token the fixture accepts is `qa-dummy-token`.
