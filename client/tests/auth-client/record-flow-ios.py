#!/usr/bin/env python3
"""Drive actual native controls in the signed synthetic test build. Run from repo root.
Start the process-isolated recorder first; pass its output directory.
The app must already show paused Now Playing. This never starts personal music.
"""
import importlib.util,json,time,sys
from pathlib import Path
spec=importlib.util.spec_from_file_location('ios','client/tests/auth-client/ios.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
out=Path(sys.argv[1]);events=[]
assert (out/'timing.json').exists(), 'Start record.py first'
assert m.probe().get('library') and not m.probe().get('playing'), 'Open Now Playing and pause first'
assert any(n['bounds'][2] > 0 for n in m.tool('ui_find', {'label':'Seek'})), 'Select the visible Now Playing tab before recording'
def event(name):events.append({'event':name,'at':time.time(),'state':m.probe()})
def activate(label):
 nodes=m.tool('ui_find',{'label':label});node=next(n for n in nodes if n.get('label')==label and n['role']=='BUTTON' and n['bounds'][2]>0)
 m.tool('ui_activate',{'nodeId':node['id']})
try:
    event('start-paused');time.sleep(1)
    activate('Play');m.until(lambda s:s.get('playing'),'recorded native play');event('playing');time.sleep(3)
    activate('Pause');time.sleep(2);event('paused')
    before=m.probe()['time']
    m.probe('tap',name='Seek') # Real pointer press/release at the slider centre.
    time.sleep(2);after=m.probe();assert not after['playing'] and after['time']>before+5000
    event('paused-seek');activate('Play');m.until(lambda s:s.get('playing'),'recorded native resume');event('resumed');time.sleep(4)
    activate('Back to library');time.sleep(1);activate('Sign out');m.until(lambda s:not s['library'] and 'Sign in' in s['labels'],'recorded native logout');event('logged-out');time.sleep(3)
finally:
    (out/'events.json').write_text(json.dumps(events,indent=2));(out/'stop-recording').touch()
print('Recorded actual native controls, paused seek, resume and logout')
