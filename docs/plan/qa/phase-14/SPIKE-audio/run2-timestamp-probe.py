import sys, time, math, array
sys.path.insert(0, "/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-03/scripts")
import emu_audio as ea, grpc
_, fields = ea.discovery(); pb, pbg = ea.stubs()
fmt = pb.AudioFormat(samplingRate=16000, channels=pb.AudioFormat.Mono, format=pb.AudioFormat.AUD_FMT_S16)
chan, md = ea.channel_and_metadata(fields)
t0 = time.time(); rows = []; raw = bytearray()
try:
    for p in pbg.EmulatorControllerStub(chan).streamAudio(fmt, metadata=md, timeout=float(sys.argv[1])):
        a = array.array('h'); a.frombytes(p.audio); raw += p.audio
        r = math.sqrt(sum(x*x for x in a)/len(a))/32768 if a else 0
        rows.append((time.time()-t0, p.timestamp, len(p.audio), 20*math.log10(r) if r>0 else -999, p.format.samplingRate, p.format.channels, p.format.format))
except grpc.RpcError as e: pass
print("now_us", int(time.time()*1e6), "t0_us", int(t0*1e6))
for r in rows[:5]+rows[len(rows)//2:len(rows)//2+5]+rows[-5:]: print("recv=%.3f ts=%d bytes=%d db=%.1f fmt=%s/%s/%s" % r)
loud = [r for r in rows if r[3] > -50]; print("packets", len(rows), "loud(>-50dB)", len(loud), "first loud recv", loud[0][0] if loud else None, "bytes total", len(raw))
open(sys.argv[2], "wb").write(raw)
