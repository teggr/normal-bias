#!/usr/bin/env python3
"""Actual pointer/semantic checks against the isolated scroll-preview simulator build.

Requires the production-source test copy with ClientProbe on port 18888, the
synthetic HTTPS BrowseFixtureApplication and dev.normalbias.scrollpreview.
No retained service, personal music or physical device is used.
"""
import json, socket, subprocess, time, os, signal
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'client/target/scroll-native'
SIM='78BD8653-8300-4EC1-A2C8-84AC2503352A'
recorder=None
def tool(name,args):
    with socket.create_connection(('127.0.0.1',18888),timeout=15) as s:
        f=s.makefile('rw')
        def send(value): f.write(json.dumps(value)+'\n'); f.flush()
        send({'jsonrpc':'2.0','id':1,'method':'initialize','params':{'protocolVersion':'2024-11-05','capabilities':{},'clientInfo':{'name':'scroll-native','version':'1'}}});f.readline()
        send({'jsonrpc':'2.0','method':'notifications/initialized'})
        send({'jsonrpc':'2.0','id':2,'method':'tools/call','params':{'name':name,'arguments':args}})
        r=json.loads(f.readline())
        if 'error' in r or r.get('result',{}).get('isError'):raise RuntimeError(r)
        return json.loads(r['result']['content'][0]['text'])
def probe(op='state',**args):return tool('auth_client_probe',dict(op=op,**args))
def tap(name):probe('tap',name=name);time.sleep(.4)
def until(check,label):
    end=time.monotonic()+25
    while time.monotonic()<end:
        try:
            state=probe()
            if check(state):return state
        except (OSError, ValueError):pass
        time.sleep(.2)
    raise RuntimeError('Timeout: '+label)
def jump(letter):
    end=time.monotonic()+20
    while time.monotonic()<end:
        ready=any(n.get('label')=='Jump to '+letter and n.get('enabled') for n in tool('ui_snapshot',{})['nodes'])
        if ready:tap('Jump to '+letter);return
        time.sleep(.2)
    raise RuntimeError('Alphabet not ready: '+letter)
def shot(name):subprocess.run(['xcrun','simctl','io',SIM,'screenshot',str(OUT/(name+'.png'))],check=True,capture_output=True)
def mini_pixels(image):
    button=tool('ui_find',{'label':'Open Now Playing'})[0]['bounds']
    y=button[1]-round(button[3]*.6);height=round(button[3]*3.5)
    # Inspect the actual fixed mini-player, including its artwork, beneath the list edge.
    result=subprocess.run(['ffmpeg','-v','error','-i',str(OUT/(image+'.png')),'-vf',f'crop=1206:{height}:0:{y}','-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','pipe:1'],check=True,capture_output=True,timeout=15)
    return result.stdout
def assert_sleeve_visible(image):
    # The first row must contain the coloured sleeve, not a white mask/empty frame.
    button=tool('ui_find',{'label':'Play M track 06000'})[0]['bounds']
    x=max(0,button[0]-180);y=max(0,button[1]-35)
    result=subprocess.run(['ffmpeg','-v','error','-i',str(OUT/(image+'.png')),'-vf',f'crop=170:150:{x}:{y}','-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','pipe:1'],check=True,capture_output=True,timeout=15)
    rgb=result.stdout
    palette=[(0x79,0x91,0x9c),(0xee,0xbd,0x40),(0xdf,0x75,0x4c),(0xa3,0xa8,0x78)]
    colored=sum(any(abs(rgb[i]-r)<5 and abs(rgb[i+1]-g)<5 and abs(rgb[i+2]-b)<5 for r,g,b in palette) for i in range(0,len(rgb),3))
    assert colored>200,'native first-row sleeve must retain its colours rather than a missing/white mask: '+str(colored)
def run():
    global recorder
    checks=[]
    def passed(message):checks.append(message);print('PASS '+message,flush=True)
    until(lambda s:s.get('library'), 'preview ready')
    if os.environ.get('RECORD_NATIVE'):
        recorder=subprocess.Popen(['xcrun','simctl','io',SIM,'recordVideo','--codec=h264','--force',str(OUT/'scroll-boundary-source.mp4')],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        time.sleep(1)
    tap('All songs')
    state=until(lambda s:'A track 00000' in s['labels'],'initial native collection')
    assert all(c in state['labels'] for c in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ#')
    time.sleep(1);shot('native-initial');passed('native full alphabet and initial collection')
    if tool('ui_find',{'label':'Show search'}):tap('Show search')
    probe('text',name='Library search',value='absent-only-clipping-baseline');tap('Search')
    until(lambda s:'No tracks match your search.' in s['labels'],'empty clipping baseline')
    tap('Hide search');time.sleep(1);shot('native-empty-baseline');baseline=mini_pixels('native-empty-baseline')
    tap('All songs');until(lambda s:'A track 00000' in s['labels'],'reset clipping baseline')
    jump('M');until(lambda s:'M track 06000' in s['labels'],'native M jump');time.sleep(1);shot('native-middle')
    assert_sleeve_visible('native-middle')
    passed('native first-row sleeve retains its coloured raster')
    actual=mini_pixels('native-middle');difference=sum(abs(a-b) for a,b in zip(baseline,actual))/len(baseline)
    assert len(actual)==len(baseline) and difference<1,'list artwork must not change the fixed mini-player: '+str(difference)
    passed('native mini-player pixels remain unchanged across list-artwork boundaries')
    current=[n['label'] for n in tool('ui_snapshot',{})['nodes'] if n.get('description')=='Current section']
    assert current==['Jump to M'],current
    jump('Z');until(lambda s:'Z track 12500' in s['labels'],'native Z jump')
    jump('#');until(lambda s:'7 track 13000' in s['labels'],'native # jump');shot('native-nonletters')
    passed('native pointer letter jumps reach M, Z and # directly')
    tap('Albums');jump('M');until(lambda s:'M Album 0' in s['labels'],'native album M jump')
    tap('M Album 0');until(lambda s:'M track 06000' in s['labels'],'native album contents');shot('native-album')
    assert not tool('ui_find',{'label':'Jump to A'}),'album track list omits alphabet controls'
    tap('Library views');until(lambda s:'M Album 0' in s['labels'],'native parent restored');shot('native-parent')
    passed('native album contents omit rail and Back restores parent position')
    tap('Artists');jump('Z');until(lambda s:'Z Artist 0' in s['labels'],'native artist Z jump')
    tap('Z Artist 0');until(lambda s:'Z Album 0' in s['labels'],'native artist albums')
    tap('Library views');until(lambda s:'Z Artist 0' in s['labels'],'native artist parent')
    passed('native artist albums retain alphabet and parent restoration')
    tap('Now Playing');tap('Library');until(lambda s:'Z Artist 0' in s['labels'],'native navigation retained');shot('native-restored')
    passed('native top-level navigation retains browsed collection')
    (OUT/'results.json').write_text(json.dumps({'checks':checks,'platform':'iPhone 17 Pro / iOS 26 simulator','bundle':'dev.normalbias.scrollpreview','audio':'not exercised in native checks'},indent=2))
if __name__=='__main__':
    try:run()
    finally:
        if recorder:
            recorder.send_signal(signal.SIGINT);recorder.wait(timeout=20)
            subprocess.run(['ffmpeg','-v','error','-i',str(OUT/'scroll-boundary-source.mp4'),'-vf','scale=402:874','-c:v','libx264','-pix_fmt','yuv420p','-movflags','+faststart','-y',str(OUT/'scroll-boundary.mp4')],check=True)
