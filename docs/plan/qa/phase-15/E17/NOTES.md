# E17 on the busy-card fix's build (emulator-5560): 25 passed, 1 failed

- The busy card: its title is now the sentence alone ("The voice recorder is using the microphone right now."). The
  holder token ("held by recorder") stays in the log. PASS (the new exact assertion).
- FAIL "the take's RMS over Tess's window is still > -40 dBFS": the take is exact digital silence (-inf dBFS) from
  6710 to 14558 ms, the window Tess's session was up; overall -35.7 dBFS. The :speech ring shows the listen REFUSED
  without opening the microphone ("startListening from pid=17432 refused: held by recorder"), and the :recorder ring
  shows no silencing event. The recorder pass's probes of the same kind of window (E17 runs 2-3, after an app switch)
  found AudioFlinger reporting the capture "not silenced" (Sil=n), with zeros coming from the emulator's audio HAL.
  The same assertion PASSED in the recorder pass's final E17 run (25/0). Read as an intermittent emulator audio-HAL
  artifact, not yet proven. If the final gate run repeats it, it goes to the phone rows (NEEDS-HUMAN) rather than
  being treated as the product.
