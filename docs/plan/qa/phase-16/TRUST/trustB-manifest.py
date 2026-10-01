import re,sys
lines=open(sys.argv[1]).read().splitlines()
comp=None; out=[]
cur=None
stack=[]
for ln in lines:
    m=re.match(r'^(\s*)E: (\S+)',ln)
    if m:
        ind=len(m.group(1)); tag=m.group(2)
        if tag in('activity','service','receiver','provider','activity-alias'):
            cur={'tag':tag,'attrs':{},'filters':[],'ind':ind}; out.append(cur); mode='comp'
        elif tag=='intent-filter' and cur: cur['filters'].append([]); mode='filter'
        elif tag in('action','category','data') and cur and cur['filters']: mode=tag; cur['filters'][-1].append([tag,{}])
        elif tag in ('meta-data','uses-permission','permission','application','manifest','uses-sdk','queries','package','intent','property','uses-feature','grant-uri-permission','path-permission'):
            mode='other:'+tag
            if tag in('uses-permission','permission','application','uses-sdk'): out.append({'tag':tag,'attrs':{},'filters':[]}); curo=out[-1]
        else: mode='other:'+tag
        continue
    m=re.match(r'^\s*A: (?:http://schemas.android.com/apk/res/android:)?(\w+)(?:\(0x[0-9a-f]+\))?=(.*)$',ln)
    if m:
        k,v=m.group(1),m.group(2)
        v=re.sub(r' \(Raw: .*\)$','',v)
        if mode=='comp': cur['attrs'][k]=v
        elif mode in('action','category','data'): cur['filters'][-1][-1][1][k]=v
        elif mode in('other:uses-permission','other:permission','other:application','other:uses-sdk'): curo['attrs'][k]=v
for c in out:
    if c['tag'] in('uses-permission','permission','uses-sdk'):
        print(c['tag'],c['attrs']); continue
    if c['tag']=='application':
        print('application',c['attrs']); continue
    a=c['attrs']
    print(f"{c['tag']:9} {a.get('name')}  exported={a.get('exported')} permission={a.get('permission')} launchMode={a.get('launchMode')} taskAffinity={a.get('taskAffinity')} process={a.get('process')} extra={ {k:v for k,v in a.items() if k in('grantUriPermissions','authorities','showWhenLocked','directBootAware','readPermission','writePermission','enabled')} }")
    for f in c['filters']:
        print('     filter:', '; '.join(t+':'+','.join(f'{k}={v}' for k,v in d.items()) for t,d in f))
