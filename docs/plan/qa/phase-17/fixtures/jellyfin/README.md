# Phase 17 — the media-server fixture (Jellyfin 12.1)

`jellyfin_fixture.sh` starts the image BS-5 pins,
`jellyfin/jellyfin:12.1@sha256:78d3ea1207d1322471fcac39a614f004f2ccf7e878f95ab2977d752f07e4dd7e`, on
`127.0.0.1:8096` (the AVD's `10.0.2.2:8096`) with two fresh docker volumes for `/config` and `/cache` and a read-only
media folder holding one video, `movies/qa-steps.mp4`; seeds it over REST; and removes the container and both volumes
by the ids it recorded. Nothing of the server's is kept in the repo. The fixture account is `qa` / `qa-password`.

## The seeding sequence as run (2026-10-05, the first run of BS-5's sequence)

| Step | Answer |
|---|---|
| `GET /System/Info/Public` (see "ready" below) | 200, `Version` 12.1.0, `StartupWizardCompleted` false |
| `POST /Startup/Configuration` `{"UICulture","MetadataCountryCode","PreferredMetadataLanguage"}` | 204 |
| `GET /Startup/User` | 200 |
| `POST /Startup/User` `{"Name","Password"}` | 204 |
| `POST /Library/VirtualFolders?name=Movies&collectionType=movies&paths=/media/movies&refreshLibrary=true`, body `{"LibraryOptions":{}}` | 204 |
| `POST /Startup/Complete` | 204 |
| `POST /Users/AuthenticateByName` `{"Username","Pw"}` with `Authorization: MediaBrowser Client=…, Device=…, DeviceId=…, Version=…` | 200, `AccessToken`, `User.Id` |
| poll `GET /Items?userId=<id>&recursive=true&includeItemTypes=Movie,Episode,Video` | `qa-steps` listed (type `Movie`) within about 3 s |

The whole `up` takes about 10 s.

## What differs from BS-5's text (found by running it)

1. **"Wait for `/System/Info/Public`" is not enough.** On a first start 12.1 answers 200 for a moment from its start-up
   host, stops answering, and comes back as the real server. A script that seeds after the first 200 gets no answer
   (status 000) from every call. Ready is: the server's own JSON with a `Version`, three times in a row a second apart.
2. **`GET /Devices` carries no `AccessToken`.** Its items hold `AppName`, `AppVersion`, `Capabilities`,
   `DateLastActivity`, `Id`, `LastUserId`, `LastUserName`, `Name` — so E22's "the item whose `AppName` is the shell's
   client, its `AccessToken`" (r3 V8) cannot be read there. The token is in the server's database
   (`/config/data/jellyfin.db`, table `Devices`, column `AccessToken`): `jellyfin_fixture.sh token-of <work> Tessera`
   copies the database out of the container and prints it. The shell's client names itself `Tessera`.
3. **A direct-play stream answers without any key.** `GET /Videos/<id>/stream?static=true` returned 200 with
   `ApiKey=<token>`, with the legacy `api_key=<token>`, and with no key at all. The shell still sends `ApiKey` (BS-5);
   it is not what makes the stream play on this image.
4. An item id the server does not have answers **400** on the stream path (the player's line is `cannot decode 400`).
5. `DELETE /Devices?id=<id>` (204) makes that device's token stop working: the next `GET /Items` with it is 401. That
   is how the "token invalid after a password change" edge case is produced (`jellyfin_fixture.sh revoke`).
