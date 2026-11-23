#!/usr/bin/env python3
"""Drive the actual simulator UI/player through its test-only MCP build. Never returns credentials."""
import json, socket, subprocess, time
from pathlib import Path
root=Path(__file__).resolve().parents[3];out=root/'client/target/auth-client';fixture=json.loads((out/'fixture.json').read_text())
SIM='78BD8653-8300-4EC1-A2C8-84AC2503352A'
def tool(name,args):
    with socket.create_connection(('127.0.0.1',18868),timeout=10) as s:
        stream=s.makefile('rw')
        def send(value):stream.write(json.dumps(value)+'\n');stream.flush()
        send({'jsonrpc':'2.0','id':1,'method':'initialize','params':{'protocolVersion':'2024-11-05','capabilities':{},'clientInfo':{'name':'normal-bias-fixture','version':'1'}}});stream.readline()
        send({'jsonrpc':'2.0','method':'notifications/initialized'})
        send({'jsonrpc':'2.0','id':2,'method':'tools/call','params':{'name':name,'arguments':args}})
        result=json.loads(stream.readline())
        if 'error' in result:raise RuntimeError('Simulator tool failed')
        text=result['result']['content'][0]['text']
        return json.loads(text)
def probe(op='state',**args):return tool('auth_client_probe',dict(op=op,**args))
def until(check,label,timeout=30):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        try:
            state=probe()
            if check(state):return state
        except (OSError,ValueError):pass
        time.sleep(.2)
    raise RuntimeError('Timeout: '+label)
def login(password=None):
    probe('text',name='Username',value=fixture['username'])
    probe('text',name='Password',value=fixture['password'] if password is None else password)
    probe('tap',name='Sign in')
def sim(*args):return subprocess.run(['xcrun','simctl',*args],check=True,capture_output=True,text=True)
def run():
    checks=[];events=[]
    def evidence(label,state=None):
        checks.append(label);events.append({'check':label,'state':state or probe(),'at':time.time()});print('PASS '+label,flush=True)
    state=until(lambda s:not s['library'] and 'Sign in' in s['labels'],'login screen')
    login('wrong synthetic password');time.sleep(1)
    assert not probe()['library'];evidence('invalid password keeps login screen')
    login();until(lambda s:s['library'] and 'Morning Circuit' in s['labels'],'authenticated library')
    evidence('native device-session login and authenticated catalogue')
    sim('terminate',SIM,'dev.normalbias.client');sim('launch',SIM,'dev.normalbias.client')
    until(lambda s:s['library'] and 'Morning Circuit' in s['labels'],'Keychain restoration')
    evidence('app termination and relaunch restores Keychain-backed sign-in')
    probe('play-first');state=until(lambda s:s.get('playing') and s.get('time',0)>1500,'scoped playback')
    evidence('native scoped playback progresses',state)
    probe('pause');before=probe()['time'];time.sleep(1);assert abs(probe()['time']-before)<500
    evidence('native pause stops progress')
    print('Waiting 121 seconds for real grant expiry',flush=True)
    time.sleep(121)
    probe('seek',value='62000');state=until(lambda s:abs(s.get('time',0)-62000)<1500 and not s.get('playing') and not s.get('loading'),'paused expired seek')
    time.sleep(1);assert not probe()['playing'];evidence('expired native seek renews URL and remains paused',state)
    probe('resume');state=until(lambda s:s.get('playing') and s.get('time',0)>63500,'resume renewed audio')
    evidence('native renewal resumes at retained seek position',state)
    # Add the other track through its real UI row, then select the first again.
    probe('tap',name='Evening Signal');until(lambda s:s.get('queue')==2,'two-track queue')
    probe('tap',name='Morning Circuit');until(lambda s:s.get('playing'),'first track selected')
    probe('seek',value='175000');sim('launch',SIM,'com.apple.Preferences')
    time.sleep(9);state=probe();assert state.get('queue')==2 and state.get('playing') and 1000<state.get('time',0)<20000
    evidence('background completion obtains next scoped URL without form polling',state)
    sim('launch',SIM,'dev.normalbias.client');until(lambda s:s.get('playing'),'foreground return')
    probe('tap',name='Sign out');until(lambda s:not s['library'],'native logout')
    evidence('native logout clears library, player and queue')
    sim('terminate',SIM,'dev.normalbias.client');sim('launch',SIM,'dev.normalbias.client');until(lambda s:not s['library'] and 'Sign in' in s['labels'],'logout persists')
    evidence('relaunch after logout does not restore revoked credential')
    (out/'ios-result.json').write_text(json.dumps({'checks':checks,'events':events,'device':'iPhone 17 Pro simulator; iOS 26.0'},indent=2))
if __name__=='__main__':run()
