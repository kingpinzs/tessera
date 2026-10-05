#!/usr/bin/env python3
"""Phase 17 E7 — the static keys the row reads from `dumpsys media.camera` (stdin), one `name=value` per line.

  cam_facts.py            every key below
  cam_facts.py <name>     that key's value alone

Keys (the row's list): hardwareLevel (INFO_SUPPORTED_HARDWARE_LEVEL), capabilities (REQUEST_AVAILABLE_CAPABILITIES),
aeModes (CONTROL_AE_AVAILABLE_MODES), aeCompensationRange (CONTROL_AE_COMPENSATION_RANGE), minimumFocusDistance
(LENS_INFO_MINIMUM_FOCUS_DISTANCE), awbModes (CONTROL_AWB_AVAILABLE_MODES); and for the focus and zoom sub-rows afModes
(CONTROL_AF_AVAILABLE_MODES), maxDigitalZoom (SCALER_AVAILABLE_MAX_DIGITAL_ZOOM), plus two derived words:
autofocus=<yes|no> (an AF mode other than OFF is listed) and zoom=<yes|no> (max digital zoom above 1.0).
The values are the dump's own text for the FIRST camera's static block, whitespace collapsed.
"""
import re, sys

text = sys.stdin.read()


def key(name):
    m = re.search(r'android\.%s \([0-9a-f]+\): \w+\[(\d+)\]\s*\n\s*(?:\[)?([^\n\]]*)' % re.escape(name), text)
    return ' '.join(m.group(2).split()) if m else ''


facts = {
    'hardwareLevel': key('info.supportedHardwareLevel'),
    'capabilities': key('request.availableCapabilities'),
    'aeModes': key('control.aeAvailableModes'),
    'aeCompensationRange': key('control.aeCompensationRange'),
    'minimumFocusDistance': key('lens.info.minimumFocusDistance'),
    'awbModes': key('control.awbAvailableModes'),
    'afModes': key('control.afAvailableModes'),
    'maxDigitalZoom': key('scaler.availableMaxDigitalZoom'),
}
af = [int(x) for x in re.findall(r'-?\d+', facts['afModes'])]
facts['autofocus'] = 'yes' if any(m != 0 for m in af) else 'no'
zoom = re.findall(r'[\d.]+', facts['maxDigitalZoom'])
facts['zoom'] = 'yes' if zoom and float(zoom[0]) > 1.0 else 'no'
if len(sys.argv) > 1:
    print(facts.get(sys.argv[1], ''))
else:
    for k, v in facts.items():
        print('%s=%s' % (k, v))
