#!/usr/bin/env python3
"""Run only against the disposable two-track CarPlayProbe demo.

Calls the actual template callbacks; it does not synthesize OS mouse input.
The framework renderer/recorder supplies the real head-unit frames and app PCM.
"""
import socket,json,time,os
from pathlib import Path
PORT=int(os.environ.get('NORMALBIAS_CARPLAY_MCP_PORT','18866'))
OUTPUT=Path(__file__).resolve().parent.parent/'target/recordings/carplay'
log=[]
def call(op,title=None):
 with socket.create_connection(('127.0.0.1',PORT)) as s:
  s.settimeout(60)
  f=s.makefile('rw')
  def send(x):f.write(json.dumps(x)+'\n');f.flush()
  send({'jsonrpc':'2.0','id':1,'method':'initialize','params':{'protocolVersion':'2024-11-05','capabilities':{},'clientInfo':{'name':'carplay-smoke','version':'1'}}});f.readline()
  send({'jsonrpc':'2.0','method':'notifications/initialized'})
  args={'op':op}
  if title:args['title']=title
  send({'jsonrpc':'2.0','id':2,'method':'tools/call','params':{'name':'car_probe','arguments':args}})
  r=json.loads(f.readline())
  assert 'error' not in r,r
  assert not r['result'].get('isError'),r
  out=r['result']['content'][0]['text']
  print(op,title or '',out,flush=True);log.append([time.time(),op,title,out])
  return out

if __name__ == '__main__':
    call('connect');time.sleep(1)
    root=call('state')
    assert 'Library | Now Playing' in root and 'Playback controls' not in root
    call('record');time.sleep(2)
    call('tap','Now Playing');call('tap','Queue');time.sleep(2)
    assert 'Queue is empty' in call('state')
    call('back');call('back');call('tap','Library');time.sleep(2)
    call('tap','All songs');time.sleep(2)
    songs=call('state')
    assert songs.index('Evening Signal') < songs.index('Morning Circuit')
    call('screenshot','all-songs')
    call('back');call('tap','Artists');time.sleep(2)
    artists=call('state')
    assert 'Normal Bias Demo' in artists
    if os.environ.get('CARPLAY_EXPECT_ARTWORK') == '1': assert '[artwork]' in artists
    call('screenshot','artists')
    call('tap','Normal Bias Demo');time.sleep(2)
    assert 'Simulator Sessions' in call('state')
    call('tap','Simulator Sessions');time.sleep(2)
    assert 'Evening Signal' in call('state')
    call('tap','Back to Normal Bias Demo');time.sleep(1)
    call('tap','Back to Artists');time.sleep(1)
    call('back');call('tap','Albums');time.sleep(2)
    call('tap','Simulator Sessions');time.sleep(2)
    call('screenshot','album')
    call('tap','Evening Signal');time.sleep(4)
    assert 'playing=true' in call('state')
    call('screenshot','now-playing')
    call('tap','Pause');time.sleep(2)
    assert 'playing=false' in call('state')
    call('tap','Play');time.sleep(2)
    call('back');call('tap','Morning Circuit');time.sleep(3)
    assert 'queue=2 index=1 playing=true' in call('state')
    call('tap','More controls');time.sleep(1)
    call('tap','Previous track');time.sleep(2)
    assert 'index=0 playing=true' in call('state')
    call('tap','Next track');time.sleep(2)
    assert 'index=1 playing=true' in call('state')
    call('tap','Back to Now Playing');call('tap','Queue');time.sleep(2)
    call('tap','Shuffle queue');time.sleep(2)
    call('screenshot','queue')
    assert 'playing=true' in call('state')
    call('tap','Evening Signal');time.sleep(2)
    assert 'title=Queue' in call('state') and 'playing=true' in call('state')
    call('back');call('tap','Pause');time.sleep(2)
    call('tap','More controls');call('tap','System player');time.sleep(2)
    assert 'CarNowPlayingTemplate' in call('state')
    call('back');call('tap','Back to Now Playing');call('tap','Queue');call('tap','Clear queue');time.sleep(2)
    assert 'queue=0 index=-1 playing=false' in call('state')
    call('back');call('back');call('back');time.sleep(1)
    call('stop')
    call('disconnect');call('connect');time.sleep(1)
    assert 'Library | Now Playing' in call('state')
    call('screenshot')
    (OUTPUT/'smoke.json').write_text(json.dumps(log,indent=2))
