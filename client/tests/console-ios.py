#!/usr/bin/env python3
"""Pointer/layout checks for the isolated Console app; never operates retained previews."""
import json,socket,subprocess,time,os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'client/target/console-native'
SIM=os.environ.get('CONSOLE_SIM','269D0AC3-1FB6-439E-87D8-07FC8E76A2A6')
def tool(name,args):
    with socket.create_connection(('127.0.0.1',18890),timeout=15) as s:
        f=s.makefile('rw')
        def send(value):f.write(json.dumps(value)+'\n');f.flush()
        send({'jsonrpc':'2.0','id':1,'method':'initialize','params':{'protocolVersion':'2024-11-05','capabilities':{},'clientInfo':{'name':'console-check','version':'1'}}});f.readline()
        send({'jsonrpc':'2.0','method':'notifications/initialized'})
        send({'jsonrpc':'2.0','id':2,'method':'tools/call','params':{'name':name,'arguments':args}})
        r=json.loads(f.readline())
        if 'error' in r or r.get('result',{}).get('isError'):raise RuntimeError(r)
        return json.loads(r['result']['content'][0]['text'])
def probe(op='state',**args):return tool('auth_client_probe',dict(op=op,**args))
def tap(name):probe('tap',name=name);time.sleep(.5)
def find(name):return [n for n in tool('ui_find',{'label':name}) if n.get('label')==name]
def until(check,label):
    end=time.monotonic()+30
    while time.monotonic()<end:
        try:
            if check():return
        except (OSError,ValueError):pass
        time.sleep(.2)
    raise RuntimeError('Timeout: '+label)
def shot(name):subprocess.run(['xcrun','simctl','io',SIM,'screenshot',str(OUT/(name+'.png'))],check=True,capture_output=True)
def grid(expected):
    until(lambda:len(find('Open 00 Album'))>0,'album tile')
    nodes=[n for n in tool('ui_snapshot',{})['nodes'] if n.get('role')=='BUTTON' and n.get('label','').startswith('Open ') and ' Album' in n.get('label','')]
    row=[n for n in nodes if abs(n['bounds'][1]-nodes[0]['bounds'][1])<2]
    assert len(row)==expected,(expected,row)
    assert row[0]['bounds'][3]>row[0]['bounds'][2],row[0]
def console():
    assert not find('Open Now Playing'),'no duplicate mini-player'
    assert len(find('Previous'))==1 and len(find('Next'))==1
    for name in ['Player information','Queue','Restart track','Shuffle queue']:assert find(name),name
    a=find('Previous')[0]['bounds'];b=find('Queue')[0]['bounds'];assert b[1]<a[1]

def run():
    checks=[]
    def passed(s):checks.append(s);print('PASS '+s,flush=True)
    until(lambda:probe().get('library'),'Library')
    if os.environ.get('CONSOLE_PHONE'):
        if any(n.get('role')=='BUTTON' for n in find('Library')):tap('Library')
        assert find('Now Playing') and find('Open Now Playing') and not find('Queue')
        tap('Albums');until(lambda:len(find('Open 00 Album'))>0,'compact album row')
        b=find('Open 00 Album')[0]['bounds'];assert b[2]>b[3],'compact list'
        if not os.environ.get('CONSOLE_PHONE_LANDSCAPE'):
            tap('Now Playing');assert find('Queue');tap('Queue');assert find('Back to Now Playing')
            shot('native-phone-queue');tap('Back to Now Playing');shot('native-phone-player')
        else:shot('native-phone-landscape')
        passed('compact landscape phone retains album rows and mini-player' if os.environ.get('CONSOLE_PHONE_LANDSCAPE') else 'compact portrait phone retains album rows, mini-player and nested Queue/player')
    else:
        orientation=os.environ.get('CONSOLE_ORIENTATION','landscape')
        tap('Albums')
        if orientation=='landscape':
            grid(5);console();shot('native-landscape')
            passed('landscape five-column tiles and intact top-console controls')
            tap('Open 00 Album');until(lambda:len(find('Queue album'))>0,'album contents');tap('Queue album')
            until(lambda:probe().get('queue')==1 and not probe().get('loading'),'paused preparation')
            current=probe()['currentTrack'];assert not probe()['playing']
            tap('Close album');until(lambda:len(find('Open 00 Album'))>0,'parent tiles')
            tap('Queue');until(lambda:len(find('Close queue'))>0,'open Queue');assert not find('Albums');shot('native-queue')
            tap('Close queue');grid(5);assert probe()['currentTrack']==current
            tap('Player information');assert find('Show artwork');tap('Show artwork')
            passed('album preparation, Queue Close/restore and information retain selection')
        else:
            grid(4);console();shot('native-portrait')
            tap('Queue');until(lambda:len(find('Close queue'))>0,'portrait Queue');shot('native-portrait-queue');tap('Queue');grid(4)
            tap('Artists');until(lambda:len(find('Open 00 Artist'))>0,'artist tiles');tap('Open 00 Artist')
            assert find('Open 00 Album');tap('Library views');tap('Albums');grid(4)
            passed('portrait four-column tiles, Queue toggle and artist-album navigation')
            tap('Queue');tap('Clear queue');assert probe()['queue']==0 and find('Close queue');tap('Close queue');grid(4)
            passed('Clear stays in Queue and Close restores the Library grid')
    (OUT/(('phone-landscape-results.json' if os.environ.get('CONSOLE_PHONE_LANDSCAPE') else 'phone-results.json') if os.environ.get('CONSOLE_PHONE') else os.environ.get('CONSOLE_ORIENTATION','landscape')+'-results.json')).write_text(json.dumps({'checks':checks,'fixture':'30 synthetic tracks; loopback HTTPS demo','audio':'paused preparation only; no native acoustic claim'},indent=2))
if __name__=='__main__':run()
