#!/usr/bin/env python3
"""The mode list E7 derives from `dumpsys media.camera` (stdin), printed as ids in the viewfinder's order.

Photo and Video always; Pro dial when any r3 D10 gate admits a control; HDR only when CameraX Extensions report it
(argument 1: yes / no — the dump cannot say); Slow motion iff CONSTRAINED_HIGH_SPEED_VIDEO; Panorama only on arm64-v8a
(argument 2: the device's ABI); Living Images always. With --pro it prints the admitted dial controls instead.
"""
import re, sys

text = sys.stdin.read()
# The first camera's static block.
def key(name):
    m = re.search(r'android\.%s \([0-9a-f]+\): \w+\[(\d+)\]\s*\n\s*(?:\[)?([^\n\]]*)' % re.escape(name), text)
    return m.group(2).strip() if m else ''
def ints(name): return [int(x) for x in re.findall(r'-?\d+', key(name))]
caps = key('request.availableCapabilities')
ae_modes = ints('control.aeAvailableModes')
ev = ints('control.aeCompensationRange')
focus = re.findall(r'[\d.]+', key('lens.info.minimumFocusDistance'))
awb = ints('control.awbAvailableModes')
controls = []
if ev and (ev[0] != 0 or ev[-1] != 0): controls.append('exposure')
manual = (0 in ae_modes) and ('MANUAL_SENSOR' in caps)
if manual: controls += ['shutter', 'iso']
if focus and float(focus[0]) > 0: controls.append('focus')
if any(m in awb for m in (2, 3, 4, 5, 6, 7, 8)): controls.append('wb')
if '--pro' in sys.argv:
    print(' '.join(controls)); sys.exit(0)
hdr = len(sys.argv) > 1 and sys.argv[1] == 'yes'
abi = sys.argv[2] if len(sys.argv) > 2 else ''
modes = ['photo', 'video']
if controls: modes.append('pro')
if hdr: modes.append('hdr')
if 'CONSTRAINED_HIGH_SPEED_VIDEO' in caps: modes.append('slowmo')
if abi == 'arm64-v8a': modes.append('panorama')
modes.append('livingimages')
print(' '.join(modes))
