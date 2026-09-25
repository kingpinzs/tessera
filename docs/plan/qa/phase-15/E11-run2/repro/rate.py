import re, subprocess, time
def adb(*a): return subprocess.run(["adb",*a],capture_output=True,text=True).stdout
def dump():
    adb("shell","uiautomator","dump","/sdcard/k.xml"); return adb("shell","cat","/sdcard/k.xml")
x=dump()
pts={m.group(1):((int(m.group(2))+int(m.group(4)))//2,(int(m.group(3))+int(m.group(5)))//2) for m in re.finditer(r'resource-id="calc_key:([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x)}
def disp():
    x=dump(); m=re.search(r'<node [^>]*resource-id="calc_display"[^>]*>',x); return re.search(r'text="([^"]*)"',m.group(0)).group(1)
def batch(keys): adb("shell","; ".join(f"input tap {pts[k][0]} {pts[k][1]}" for k in keys))
bad=[]
for i in range(30):
    for keys, want in (("mc 5 mplus mplus mr", "10"), ("mc 5 mminus mr", "-5")):
        batch(["clear"]); batch(keys.split()); time.sleep(0.3); d=disp()
        if d != want: bad.append((i, keys, want, d))
print("failures:", len(bad), bad)
