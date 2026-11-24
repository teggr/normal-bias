#!/usr/bin/env python3
"""Connection acceptance for an isolated no-bundled-origin iPhone simulator build.

Prepare the ignored production-source copy using prepare-ios.py, change only its
probe port to 18878, and generate ios-source without the native-trial profile.
Install the ad-hoc-signed app as dev.normalbias.connectiontrial (change the built
Info.plist and re-sign), preserving the retained dev.normalbias.client app.
The fixture certificate must be trusted in this simulator. Never use a phone.
"""
import json, socket, subprocess, time
from pathlib import Path
root = Path(__file__).resolve().parents[3]
out = root / 'client/target/auth-client'
fixture = json.loads((out / 'fixture.json').read_text())
SIM = '78BD8653-8300-4EC1-A2C8-84AC2503352A'
APP = 'dev.normalbias.connectiontrial'

def probe(op='state', **args):
    with socket.create_connection(('127.0.0.1', 18878), timeout=15) as connection:
        stream = connection.makefile('rw')
        def send(value):
            stream.write(json.dumps(value) + '\n'); stream.flush()
        send({'jsonrpc':'2.0','id':1,'method':'initialize','params':{'protocolVersion':'2024-11-05','capabilities':{},'clientInfo':{'name':'connection-check','version':'1'}}})
        stream.readline()
        send({'jsonrpc':'2.0','method':'notifications/initialized'})
        send({'jsonrpc':'2.0','id':2,'method':'tools/call','params':{'name':'auth_client_probe','arguments':dict(op=op,**args)}})
        result = json.loads(stream.readline())
        if 'error' in result or result.get('result',{}).get('isError'): raise RuntimeError('Probe failed')
        return json.loads(result['result']['content'][0]['text'])

def until(check, label):
    end = time.monotonic() + 35
    while time.monotonic() < end:
        try:
            state = probe()
            if check(state): return state
        except (OSError, ValueError): pass
        time.sleep(.2)
    raise RuntimeError('Timeout: ' + label)

def has(text):
    return lambda state: any(text in label for label in state['labels'])

def tap(name):
    probe('tap',name=name); time.sleep(.35)

def sign_in(origin=None, password=None):
    if origin is not None: probe('text',name='Service address',value=origin)
    probe('text',name='Username',value=fixture['username'])
    probe('text',name='Password',value=fixture['password'] if password is None else password)
    tap('Sign in')

def sim(*args):
    subprocess.run(['xcrun','simctl',*args],check=True,capture_output=True)

def relaunch():
    sim('terminate',SIM,APP);sim('launch',SIM,APP)

def run():
    checks=[]
    def passed(text): checks.append(text); print('PASS '+text,flush=True)
    until(has('Service address'),'first-use form')
    tap('Sign in');until(has('Enter an HTTPS service address'),'empty origin validation')
    passed('no bundled address opens unified first-use form')
    sign_in('https://127.0.0.1:19449');until(has('Cannot reach the service.'),'unreachable endpoint')
    sim('io',SIM,'screenshot',str(out/'connection-ios-unreachable.png'))
    passed('unreachable endpoint reports inline failure with retry')
    relaunch();until(has('Service address'),'first-use after failed selection')
    tap('Sign in');until(has('Enter an HTTPS service address'),'failed address not persisted')
    passed('failed discovery does not save a service')
    sign_in(fixture['origin']);until(lambda s:s['library'] and 'Morning Circuit' in s['labels'],'native login')
    passed('runtime HTTPS selection discovers login and opens catalogue without bundled origin')
    relaunch();until(lambda s:s['library'] and 'Morning Circuit' in s['labels'],'remembered origin and Keychain')
    passed('relaunch restores locally saved service and secure native session')
    tap('Profile');tap('Sign out');until(has('You’ll stay signed in'),'logout form')
    relaunch();until(has('You’ll stay signed in'),'remembered origin after logout')
    sign_in(password='incorrect synthetic password');until(has('Username or password not recognised.'),'saved endpoint login')
    passed('address remains saved after logout; password is not restored')
    other=fixture['origin'].replace('127.0.0.1','localhost')
    sign_in(other,password='incorrect synthetic password');until(has('Username or password not recognised.'),'alternate hostname')
    passed('runtime endpoint changes use fresh discovered sign-in')
    sign_in();until(lambda s:s['library'],'alternate endpoint login')
    relaunch();until(lambda s:s['library'],'alternate endpoint persisted')
    passed('new selected endpoint and its secure session survive relaunch')
    tap('Profile');tap('Sign out');until(has('You’ll stay signed in'),'final sign-in form')
    sim('io',SIM,'screenshot',str(out/'connection-ios-sign-in.png'))
    (out/'connection-ios-result.json').write_text(json.dumps({'checks':checks,'platform':'iPhone 17 Pro simulator iOS 26.0','bundle':APP,'bundledOrigin':False},indent=2))
    sim('terminate',SIM,APP)

if __name__ == '__main__': run()
