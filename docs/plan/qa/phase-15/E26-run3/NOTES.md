# E26 run 3: 106 passed, 13 failed — the first run with the audio route up

- calc_percent (unlocked and locked, 4 FAILs): the recogniser wrote "WHAT'S FIFTEEN PER CENT OF EIGHTY" (per cent as
  two words), and ArithmeticWords only knew "percent", so the request reached NotUnderstood ("Sorry, I can't do that
  yet."). PRODUCT fix: the matcher reads "per cent" as "percent" (ArithmeticWordsTest red on the recogniser's own text
  -> green).
- Every "reply was audible" (9 FAILs): RMS about -115 dBFS, digital silence. The AVD's media volume (stream 3) was 0,
  and Tess's USAGE_ASSISTANT (stream 11) follows it. The driver now raises the media volume to 10/15 for the spoken
  steps and restores it after.
- Passed with the route up: calc_divzero, calc_convert and calc_root reply texts and ring lines; weather_like reaches
  Weather; unmatched and calc_life reach the not-understood handler; no [calc] tess line in any negative's slice.
