import re, subprocess, time
exec(open('rate.py').read().split('bad=[]')[0])
def mem():
    # the memory flyout's items (M˅), then close it
    adb("shell", f"input tap {pts['mlist'][0]} {pts['mlist'][1]}"); time.sleep(0.8)
    x=dump(); items=re.findall(r'resource-id="calc_memory:(\d+)"[^>]*', x)
    texts=[re.search(r'text="([^"]*)"',m.group(0)).group(1) for m in re.finditer(r'<node [^>]*resource-id="calc_memory:\d+"[^>]*>',x)]
    adb("shell", f"input tap {pts['mlist'][0]} {pts['mlist'][1]}"); time.sleep(0.8)
    return texts
print("keys:", sorted(k for k in pts if k.startswith('m')))
n=0
for i in range(16):
    batch(["clear"]); batch("mc 5 mplus mplus mr".split()); time.sleep(0.3)
    batch(["clear"]); batch("mc 5 mminus mr".split()); time.sleep(0.3); d=disp()
    if d != "-5":
        n+=1; print(f"trial {i}: display {d}; memory now {mem()}")
print("failures", n)
