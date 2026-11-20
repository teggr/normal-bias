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
    call('connect')
    time.sleep(1)
    call('record')
    call('tap','Queue (0)');time.sleep(2)
    call('back');call('tap','Library');time.sleep(1)
    assert 'Evening Signal' in call('state')
    call('tap','Evening Signal');time.sleep(4)
    assert 'playing=true' in call('state')
    call('tap','Pause');time.sleep(2)
    assert 'playing=false' in call('state')
    call('tap','Play');time.sleep(2)
    call('back');call('tap','Morning Circuit');time.sleep(3)
    assert 'queue=2 index=1 playing=true' in call('state')
    call('tap','Previous');time.sleep(3)
    assert 'index=0 playing=true' in call('state')
    call('tap','Next');time.sleep(3)
    assert 'index=1 playing=true' in call('state')
    call('tap','Now Playing');time.sleep(2)
    call('screenshot')
    call('back');call('tap','Pause');time.sleep(2)
    call('back');call('back');call('tap','Queue (2)');time.sleep(2)
    call('tap','Clear queue');time.sleep(2)
    assert 'queue=0 index=-1 playing=false' in call('state')
    call('back');time.sleep(1)
    call('stop')
    call('disconnect');call('connect');time.sleep(1)
    assert 'Queue (0)' in call('state')
    call('screenshot')
    (OUTPUT/'smoke.json').write_text(json.dumps(log,indent=2))
