#!/usr/bin/env python3
"""phase 13's dumpq.py on the pod bay: the same commands, with the page the checker rows are placed in being pod_bay
(dumpq.py names app_list; the pod bay is the pager page beside it, with the same rectangle). E14's strip, literally
phase 13 E2's method on the pod bay.

  pod_dumpq.py checker_rows <dump.xml> <fixW> <fixH> | clear_rows ... | text_nodes ... | bounds_of ...
"""
import importlib.util
import os
import sys

here = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("dumpq", os.path.join(here, "..", "..", "phase-13", "scripts", "dumpq.py"))
dumpq = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dumpq)
_page = dumpq.page
dumpq.page = lambda path, rid: _page(path, "pod_bay" if rid == "app_list" else rid)
dumpq.main()
