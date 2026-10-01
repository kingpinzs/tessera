# Scratch probe: does streamAudio deliver packets for a given AudioFormat? Counts packets for N seconds.
import sys, time, glob, os
sys.argv_saved = list(sys.argv)
here = "/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-03/scripts"
sys.path.insert(0, here)
import emu_audio as ea
_, fields = ea.discovery()
pb, pbg = ea.stubs()
import grpc
rate, ch, fmtn, secs = int(sys.argv[1]), sys.argv[2], sys.argv[3], float(sys.argv[4])
fmt = pb.AudioFormat(samplingRate=rate, channels=getattr(pb.AudioFormat, ch), format=getattr(pb.AudioFormat, fmtn))
chan, md = ea.channel_and_metadata(fields)
n = 0; nbytes = 0; t0 = time.time(); first = None
try:
    for pkt in pbg.EmulatorControllerStub(chan).streamAudio(fmt, metadata=md, timeout=secs):
        n += 1; nbytes += len(pkt.audio)
        if first is None: first = time.time() - t0; ff = pkt.format
except grpc.RpcError as e:
    print("end:", e.code().name, e.details())
print(f"fmt={rate}/{ch}/{fmtn} packets={n} bytes={nbytes} first_at={first}")
