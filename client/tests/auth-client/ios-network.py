#!/usr/bin/env python3
"""Real disposable API outage/restart and native revocation. Simulator probe only."""
import json, os, signal, ssl, subprocess, time, urllib.request
from ios import probe, until, login, root, out, fixture, tool
api=root/'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar';config=out/'server.properties'
def admin(op,*args):
    run=subprocess.run(['java','-jar',str(api),'admin',op,*args,'--data-dir='+str(out/'data')],capture_output=True,text=True,check=True)
    return json.loads(run.stdout)
def visible(label):
    return any(node.get('label')==label and node.get('bounds',[0,0,0,0])[2]>0 for node in tool('ui_find',{'label':label}))
checks=[];replacement=None
def login_ready():
    until(lambda s:not s['library'] and visible('Username') and visible('Sign in'),'presented login')
    time.sleep(.5) # Let the native editor finish its presentation before setting its text.
    login()
if not probe()['library']: login_ready()
until(lambda s:s['library'] and 'Morning Circuit' in s['labels'],'loaded native catalogue')
probe('play-first');until(lambda s:s.get('playing') and s.get('time',0)>1000,'native playback')
original=int((out/'api.pid').read_text())
command=subprocess.check_output(['ps','-p',str(original),'-o','command='],text=True)
assert '--spring.config.additional-location=file:'+str(config) in command,'Refuse to stop unrelated process'
log=(out/'native-restart.log').open('a')
try:
    os.kill(original,signal.SIGTERM)
    for _ in range(150):
        try: os.kill(original,0)
        except ProcessLookupError: break
        time.sleep(.1)
    else: raise RuntimeError('Fixture did not stop')
    probe('tap',name='Albums');state=until(lambda s:s['library'] and visible('Retry library'),'offline catalogue error')
    assert state['library'] and state['queue']==1
    checks.append('real API outage retains native identity and queue with retry UI')
    replacement=subprocess.Popen(['java','-jar',str(api),'--spring.config.additional-location=file:'+str(config)],stdout=log,stderr=subprocess.STDOUT)
    (out/'api.pid').write_text(str(replacement.pid))
    context=ssl.create_default_context(cafile=str(out/'tls.crt'))
    for _ in range(150):
        if replacement.poll() is not None: raise RuntimeError('Restarted fixture exited')
        try:
            with urllib.request.urlopen(fixture['origin']+'/api',context=context,timeout=1): break
        except OSError: time.sleep(.1)
    else: raise RuntimeError('Restarted fixture not healthy')
    probe('tap',name='Retry library');state=until(lambda s:s['library'] and not visible('Retry library'),'reconnect')
    assert state['queue']==1
    probe('tap',name='All songs');until(lambda s:s['library'] and not visible('Retry library'),'songs')
    checks.append('real service restart/reconnect restores access without password or queue loss')
    sessions=admin('sessions',fixture['username'],'--limit=200')['items']
    active=[]
    for raw in sessions:
        item={k.lower():v for k,v in raw.items()}
        if item['kind']=='BEARER' and not item['revoked']:active.append(item)
    assert len(active)==1,'Expected exactly the current simulator device session'
    admin('revoke',active[0]['id'])
    probe('tap',name='Albums');until(lambda s:not s['library'] and 'Sign in' in s['labels'],'revoked native session')
    checks.append('operator revocation triggers native 401, releases player and returns to login')
    login_ready();state=until(lambda s:s['library'] and 'Morning Circuit' in s['labels'],'fresh login')
    assert state.get('queue')==0 and not state.get('playing')
    checks.append('reauthentication starts with empty queue and no autoplay')
    probe('tap',name='Sign out');until(lambda s:not s['library'],'logout')
    (out/'ios-network-result.json').write_text(json.dumps({'checks':checks},indent=2));print(json.dumps({'checks':checks},indent=2),flush=True)
    # Keep this owned replacement available for the remaining simulator/installed tests.
    replacement.wait()
finally:
    if replacement is not None and replacement.poll() is None:replacement.terminate();replacement.wait(timeout=15)
    log.close()
