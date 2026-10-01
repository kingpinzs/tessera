#!/usr/bin/env python3
"""The AVD's microphone and speaker through the emulator's OWN gRPC controller — never the host's audio.

Phase 14 Q-R3-1a (a), 2026-09-29: the spoken rows feed the synthesised utterance into the AVD's microphone with
the emulator's `injectAudio` and capture Tess's reply with its `streamAudio`
(~/Android/Sdk/emulator/lib/emulator_controller.proto, emulator 37.1.11). Neither goes through PipeWire: no null
sink, no default-microphone change, no `hostmicon`. This client has no call that changes the microphone state —
`setMicrophoneState` is deliberately not exposed.

    emu_audio.py endpoint              print the discovery file this client talks to, and its gRPC port
    emu_audio.py mic-state             print realAudioEnabled=<true|false> (getMicrophoneState)
    emu_audio.py say <wav>             inject <wav> (16-bit PCM, mono, <= 48 kHz) into the AVD's microphone;
                                       returns when the emulator has taken every sample
    emu_audio.py record <out> <secs>   capture the AVD's audio output for <secs> seconds into a 16 kHz mono WAV

The endpoint comes ONLY from the discovery file whose `port.serial=5554` (tileshell_fhd). Other AVDs on this host
belong to other projects; a second match, no match, or ANDROID_SERIAL naming another device is a refusal, never a
guess. Exit codes: 0 ok; 2 usage / refused endpoint; 9 FAILED_PRECONDITION (another microphone is active);
1 any other gRPC error (its code and details printed).

The Python stubs are compiled from the SDK's own proto into a cache on first use. grpcio-tools is not in the host
python, so the script re-runs itself under `uv run` with pinned grpcio / grpcio-tools / protobuf when it is missing.
"""
import glob
import hashlib
import os
import sys
import time
import wave

SERIAL_PORT = "5554"
DEVICE = f"emulator-{SERIAL_PORT}"
PROTO_DIR = os.path.expanduser("~/Android/Sdk/emulator/lib")
PROTO = "emulator_controller.proto"
RUNNING = os.path.join(os.environ.get("XDG_RUNTIME_DIR", f"/run/user/{os.getuid()}"), "avd", "running")
CACHE = os.path.join(os.environ.get("XDG_CACHE_HOME", os.path.expanduser("~/.cache")), "tileshell-emu-grpc")
REEXEC = "TILESHELL_EMU_AUDIO_REEXEC"
DEPS = ["grpcio==1.76.0", "grpcio-tools==1.76.0", "protobuf>=6.31,<7"]

# injectAudio's buffer holds about 300 ms and rejects an oversized packet, so the utterance goes in 100 ms pieces.
CHUNK_S = 0.1
RECORD_RATE = 16000


def die(msg, code=2):
    print(f"emu_audio: {msg}", file=sys.stderr)
    sys.exit(code)


def ensure_grpc_tools():
    try:
        import grpc_tools  # noqa: F401
        import grpc  # noqa: F401
    except ImportError:
        if os.environ.get(REEXEC):
            die("grpc_tools still missing after the uv re-run")
        env = dict(os.environ, **{REEXEC: "1"})
        args = ["uv", "run", "--no-project", "--quiet"]
        for dep in DEPS:
            args += ["--with", dep]
        args += ["python3", os.path.abspath(__file__), *sys.argv[1:]]
        os.execvpe("uv", args, env)


def stubs():
    """The generated modules, compiled once per proto content into CACHE/<sha>."""
    src = os.path.join(PROTO_DIR, PROTO)
    digest = hashlib.sha256(open(src, "rb").read()).hexdigest()[:16]
    out = os.path.join(CACHE, digest)
    if not os.path.exists(os.path.join(out, "emulator_controller_pb2_grpc.py")):
        import grpc_tools
        from grpc_tools import protoc

        os.makedirs(out, exist_ok=True)
        include = os.path.join(os.path.dirname(grpc_tools.__file__), "_proto")
        rc = protoc.main(["protoc", f"-I{PROTO_DIR}", f"-I{include}", f"--python_out={out}",
                          f"--grpc_python_out={out}", src])
        if rc != 0:
            die(f"protoc failed on {src} (rc {rc})")
    sys.path.insert(0, out)
    import emulator_controller_pb2 as pb
    import emulator_controller_pb2_grpc as pbg
    return pb, pbg


def discovery():
    """The one discovery file whose port.serial is 5554, parsed; anything else is a refusal."""
    serial = os.environ.get("ANDROID_SERIAL")
    if serial and serial != DEVICE:
        die(f"ANDROID_SERIAL={serial}; this client only ever talks to {DEVICE}")
    matches = []
    for path in sorted(glob.glob(os.path.join(RUNNING, "pid_*.ini"))):
        fields = {}
        with open(path, encoding="utf-8", errors="replace") as f:
            for line in f:
                key, sep, value = line.rstrip("\n").partition("=")
                if sep:
                    fields[key.strip()] = value.strip()
        if fields.get("port.serial") == SERIAL_PORT:
            matches.append((path, fields))
    if len(matches) != 1:
        die(f"want exactly one discovery file with port.serial={SERIAL_PORT} in {RUNNING}, found {len(matches)}")
    path, fields = matches[0]
    pid = os.path.basename(path)[len("pid_"):-len(".ini")]
    if not os.path.exists(f"/proc/{pid}"):
        die(f"{path}: emulator pid {pid} is not running")
    if not fields.get("grpc.port"):
        die(f"{path}: no grpc.port (the emulator was started without gRPC)")
    return path, fields


def channel_and_metadata(fields):
    import grpc

    target = f"localhost:{fields['grpc.port']}"
    chan = grpc.insecure_channel(target)
    token = fields.get("grpc.token")
    metadata = [("authorization", f"Bearer {token}")] if token else []
    return chan, metadata


def grpc_fail(err):
    import grpc

    code = err.code()
    print(f"emu_audio: gRPC {code.name}: {err.details()}", file=sys.stderr)
    sys.exit(9 if code == grpc.StatusCode.FAILED_PRECONDITION else 1)


def cmd_endpoint():
    path, fields = discovery()
    print(f"discovery {path}")
    print(f"avd.name={fields.get('avd.name')} port.serial={fields.get('port.serial')} grpc.port={fields.get('grpc.port')} "
          f"token={'yes' if fields.get('grpc.token') else 'no'}")


def cmd_mic_state():
    import grpc
    from google.protobuf import empty_pb2

    _, fields = discovery()
    pb, pbg = stubs()
    chan, md = channel_and_metadata(fields)
    try:
        state = pbg.EmulatorControllerStub(chan).getMicrophoneState(empty_pb2.Empty(), metadata=md, timeout=10)
    except grpc.RpcError as err:
        grpc_fail(err)
    print(f"realAudioEnabled={'true' if state.realAudioEnabled else 'false'}")


def cmd_say(wav_path):
    import grpc

    _, fields = discovery()
    pb, pbg = stubs()
    with wave.open(wav_path, "rb") as w:
        rate, width, channels = w.getframerate(), w.getsampwidth(), w.getnchannels()
        frames = w.readframes(w.getnframes())
    if width != 2 or channels != 1:
        die(f"{wav_path}: want 16-bit mono PCM, got {8 * width}-bit x{channels}")
    if rate > 48000:
        die(f"{wav_path}: {rate} Hz is above injectAudio's 48 kHz limit")
    fmt = pb.AudioFormat(samplingRate=rate, channels=pb.AudioFormat.Mono, format=pb.AudioFormat.AUD_FMT_S16,
                         mode=pb.AudioFormat.MODE_UNSPECIFIED)
    step = int(rate * CHUNK_S) * 2

    def packets():
        for i in range(0, len(frames), step):
            yield pb.AudioPacket(format=fmt, timestamp=int(time.time() * 1e6), audio=frames[i:i + step])

    chan, md = channel_and_metadata(fields)
    t0 = time.monotonic()
    seconds = len(frames) / 2 / rate
    try:
        pbg.EmulatorControllerStub(chan).injectAudio(packets(), metadata=md, timeout=seconds + 30)
    except grpc.RpcError as err:
        grpc_fail(err)
    print(f"injected {os.path.basename(wav_path)}: {seconds:.2f} s at {rate} Hz in {time.monotonic() - t0:.2f} s")


def cmd_record(out_path, secs):
    """Everything the AVD plays for `secs` seconds. Packets arrive only while the device produces audio, so each is
    placed at its own capture time and the gaps are silence: the file is the wall-clock window, as a capture of the
    speaker would be, and its RMS is over the whole window."""
    import grpc

    _, fields = discovery()
    pb, pbg = stubs()
    fmt = pb.AudioFormat(samplingRate=RECORD_RATE, channels=pb.AudioFormat.Mono, format=pb.AudioFormat.AUD_FMT_S16)
    chan, md = channel_and_metadata(fields)
    start_us = int(time.time() * 1e6)
    total = int(secs * RECORD_RATE)
    pcm = bytearray(total * 2)
    packets = 0
    audio_samples = 0
    stream = pbg.EmulatorControllerStub(chan).streamAudio(fmt, metadata=md, timeout=secs)
    try:
        for pkt in stream:
            if pkt.format.samplingRate and pkt.format.samplingRate != RECORD_RATE:
                die(f"streamAudio answered at {pkt.format.samplingRate} Hz, asked {RECORD_RATE}", 1)
            offset = max(0, int((pkt.timestamp - start_us) * RECORD_RATE / 1e6)) if pkt.timestamp else audio_samples
            data = pkt.audio[: max(0, (total - offset) * 2)]
            pcm[offset * 2: offset * 2 + len(data)] = data
            packets += 1
            audio_samples += len(pkt.audio) // 2
    except grpc.RpcError as err:
        if err.code() != grpc.StatusCode.DEADLINE_EXCEEDED:  # the deadline is the intended end of the capture
            grpc_fail(err)
    with wave.open(out_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RECORD_RATE)
        w.writeframes(bytes(pcm))
    print(f"recorded {out_path}: {secs:g} s window, {packets} packets, {audio_samples / RECORD_RATE:.2f} s of device audio")


def main():
    argv = sys.argv[1:]
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__)
        sys.exit(0 if argv else 2)
    command = argv[0]
    if command == "endpoint":
        cmd_endpoint()
        return
    ensure_grpc_tools()
    if command == "mic-state":
        cmd_mic_state()
    elif command == "say" and len(argv) == 2:
        cmd_say(argv[1])
    elif command == "record" and len(argv) == 3:
        cmd_record(argv[1], float(argv[2]))
    else:
        die(f"unknown or incomplete command: {' '.join(argv)}\n{__doc__}")


if __name__ == "__main__":
    main()
