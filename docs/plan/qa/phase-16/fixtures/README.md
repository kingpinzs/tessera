# Phase 16 QA fixtures (build task 8 (b))

QA-only files. The binaries here are NOT committed (`.gitignore`) and are never put in `~/android-fixtures`, which
`provision.sh` installs into user 0 for every phase (r3 V17). A fresh checkout rebuilds them as below.

| File | What | How it is made | sha256 |
|---|---|---|---|
| `solid-red.jpg`, `solid-green.jpg`, `solid-blue.jpg` | E11's and E13's contact photos: a bubble's centre pixel is matched to the contact's colour ± 8 levels (r3 V14) | `python3 -c 'from PIL import Image; [Image.new("RGB",(800,800),c).save(f"solid-{n}.jpg",quality=95,subsampling=0) for n,c in (("red",(255,0,0)),("green",(0,255,0)),("blue",(0,0,255)))]'` — decoded centre pixels (254,0,0), (0,255,1), (0,0,254) | red `4c9a35b3…`, green `5aca60f5…`, blue `468f2ca3…` |
| `TestDPC_9.0.12.apk` | E16's policy fixture (T16-18): TestDPC, googlesamples/android-testdpc, Apache-2.0, no Play services. Installed inside E16 only, into the managed profile (`adb install --user <id>`), made its owner, removed with the profile. Never shipped (P5) | `curl -L -o TestDPC_9.0.12.apk https://github.com/googlesamples/android-testdpc/releases/download/v9.0.12/TestDPC_9.0.12.apk` (release v9.0.12, 2024-09-09) | `aad38c1f608b1c7289405c4e866977f5d231502643ce2e2401a7c305808f9c00` |
