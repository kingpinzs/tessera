#!/usr/bin/env python3
"""Phase 03 QA: the spoken commands, as WAV files played into the AVD's microphone.

Every acceptance row that speaks to Cortana needs real audio on the real microphone path — a text
injection would not exercise the engine, and the phase doc forbids one existing at all (E5). So the
utterances are synthesised on the HOST with the same Kokoro voice the shell ships, written to 16 kHz
mono WAVs, and played into the null sink `audio.sh setup` makes the AVD's microphone.

Synthesising the prompts with the same model the app speaks with is deliberate and is not circular for
what this gate measures: the spoken-reply pass rule grades Cortana's REPLY TEXT from the diagnostics
dump and the RMS of the captured audio, never the recogniser's accuracy (Decisions, review T-m8). The
audio only has to be real speech on a real microphone, which it is.

    utterances.py list                 print the utterance ids
    utterances.py build [id ...]       synthesise all, or just the named ones
    utterances.py path <id>            print a file path

Needs the host venv built by the gate's README: sherpa-onnx + soundfile + numpy, and the Kokoro model
under app/src/main/assets/speech/tts with espeak-ng-data unpacked beside it.
"""
import os
import subprocess
import sys
import wave
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", "..", ".."))
TTS = os.path.join(ROOT, "app", "src", "main", "assets", "speech", "tts")
OUT = os.path.join(HERE, "..", "utterances")
ESPEAK = os.path.join(OUT, "espeak-ng-data")

# The voice the prompts are spoken IN is deliberately not the shell's default (id 2): a different
# speaker keeps the prompt audio from being an exact copy of what the shell itself would produce.
PROMPT_SPEAKER = 5

UTTERANCES = {
    # E2, the per-command observable table
    "open_clock": "Open Clock.",
    "alarm": "Set an alarm for seven twenty AM.",
    "timer": "Set a timer for five minutes.",
    "reminder_time": "Remind me to take out the bins at eight PM.",
    "calendar_add": "Add a meeting called standup to my calendar at ten AM.",
    "calendar_query": "What's on my calendar?",
    "text_contact": "Text Mom I'm on my way.",
    "call_contact": "Call Mom.",
    "play_music": "Play music.",
    "directions": "Directions to the airport.",
    "take_photo": "Take a photo.",
    "take_note": "Take a note.",
    "time_query": "What time is it?",
    "date_query": "What day is it?",
    "weather": "What's the weather?",
    # E3, an utterance outside the list
    "unmatched": "What is the capital of Peru?",
    # E7, the confirmation flow
    "send_it": "Send it.",
    "add_more": "Add more.",
    "and_bring_milk": "And bring milk.",
    "try_again": "Try again.",
    "cancel": "Cancel.",
    "yes": "Yes.",
    "no": "No.",
    "whenever": "Whenever.",
    "reminder_no_time": "Remind me to call the dentist.",
    "delete_reminder": "Delete the reminder to take out the bins.",
    "calendar_delete": "Delete the event standup.",
    # E10, the locked commands
    "play_song": "Play Bohemian Rhapsody.",
    # E13 / E14, place and person reminders
    "reminder_place": "Remind me to take out the trash when I get home.",
    "reminder_person": "Remind me to ask about dinner next time I talk to Mom.",
    "save_home": "This is home.",
    # Edge cases
    "silence": "",
    "long": (
        "Remind me to call the dentist about the appointment that was moved from Tuesday to Thursday "
        "because the surgery is closed for the bank holiday and I need to ask whether the hygienist is "
        "in that week as well, at four PM tomorrow."
    ),
    "unknown_app": "Open Photoshop.",
    "unknown_contact": "Call Rumpelstiltskin.",
}


def ensure_espeak():
    """espeak-ng ships as one zip in the APK; the host needs it unpacked to synthesise."""
    if os.path.isdir(ESPEAK) and os.listdir(ESPEAK):
        return
    os.makedirs(OUT, exist_ok=True)
    with zipfile.ZipFile(os.path.join(TTS, "espeak-ng-data.zip")) as z:
        z.extractall(OUT)


def make_tts():
    import sherpa_onnx

    ensure_espeak()
    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
                model=os.path.join(TTS, "model.int8.onnx"),
                voices=os.path.join(TTS, "voices.bin"),
                tokens=os.path.join(TTS, "tokens.txt"),
                data_dir=ESPEAK,
            ),
            num_threads=4,
            provider="cpu",
        ),
        max_num_sentences=1,
    )
    if not config.validate():
        raise SystemExit("the Kokoro config did not validate; run tools/fetch-speech.sh")
    return sherpa_onnx.OfflineTts(config)


# The recogniser endpoints on trailing silence and needs a moment of room tone before the first
# phoneme; without them the first word arrives clipped and the last word runs into the endpoint. The
# first run without padding read "What time is it?" as "BUT TIME IS IT NOT".
LEAD_SILENCE_S = 0.4
# Long enough to still be FEEDING the microphone when the recogniser endpoints (its rules want 1.4 s of
# trailing silence after speech, 2.4 s without). When playback stopped first, the AVD's virtual
# microphone looped its last buffer and the recogniser heard the utterance twice
# ("WHAT TIME IS IT WHAT TIME IS IT", audioMs 5600 for a 2.6 s file).
TAIL_SILENCE_S = 3.0

# Peak-normalised so every utterance reaches the microphone at the same level, whatever the voice did.
PEAK = 0.9


def write_wav(path, samples, rate, pad=False):
    import numpy as np

    pcm = np.asarray(samples, dtype="float32")
    if pad and pcm.size:
        peak = float(np.max(np.abs(pcm)))
        if peak > 0:
            pcm = pcm * (PEAK / peak)
        pcm = np.concatenate([
            np.zeros(int(LEAD_SILENCE_S * rate), dtype="float32"),
            pcm,
            np.zeros(int(TAIL_SILENCE_S * rate), dtype="float32"),
        ])
    pcm = np.clip(pcm, -1.0, 1.0)
    pcm = (pcm * 32767.0).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(pcm.tobytes())


def resample_to_16k(path):
    """The AVD's microphone path is 16 kHz; ffmpeg is used when the model's rate differs."""
    tmp = path + ".16k.wav"
    result = subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-ar", "16000", "-ac", "1", tmp],
        capture_output=True,
    )
    if result.returncode != 0:
        print(f"  ffmpeg failed, leaving the model's own rate: {result.stderr.decode()[:200]}")
        return
    os.replace(tmp, path)


def build(ids):
    import numpy as np

    os.makedirs(OUT, exist_ok=True)
    tts = make_tts()
    rate = tts.sample_rate
    for key in ids:
        text = UTTERANCES[key]
        path = os.path.join(OUT, f"{key}.wav")
        if not text:
            # "Silence" is a real capture of nothing, two seconds of it, so the silence edge case runs
            # through the same microphone path as every other row.
            write_wav(path, np.zeros(2 * 16000, dtype="float32"), 16000)
            print(f"  {key}.wav (2.0 s of silence)")
            continue
        audio = tts.generate(text, sid=PROMPT_SPEAKER, speed=0.95)
        write_wav(path, audio.samples, rate, pad=True)
        if rate != 16000:
            resample_to_16k(path)
        with wave.open(path) as w:
            seconds = w.getnframes() / w.getframerate()
        print(f"  {key}.wav ({seconds:.2f} s) — “{text}”")


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else "list"
    if command == "list":
        for key, text in UTTERANCES.items():
            print(f"{key}\t{text}")
    elif command == "path":
        print(os.path.join(OUT, f"{sys.argv[2]}.wav"))
    elif command == "build":
        wanted = sys.argv[2:] or list(UTTERANCES)
        unknown = [k for k in wanted if k not in UTTERANCES]
        if unknown:
            raise SystemExit(f"unknown utterance(s): {unknown}")
        print(f"building {len(wanted)} utterance(s) into {OUT}")
        build(wanted)
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
