#!/usr/bin/env python3
"""Verify subsequent Range/HEAD and renewal rejection after revoking a disposable loopback session.
Tokens stay in memory; this complements native UI checks, not physical playback acceptance.
"""
import json, os, ssl, subprocess, urllib.error, urllib.parse, urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[3]
out=Path(os.environ.get('NORMALBIAS_AUTH_FIXTURE_DIR',root/'client/target/auth-client')).resolve()
assert out.is_relative_to((root/'client/target').resolve()), 'Use an ignored synthetic fixture'
fixture=json.loads((out/'fixture.json').read_text());origin=fixture['origin']
u=urllib.parse.urlsplit(origin)
assert u.scheme=='https' and u.hostname=='127.0.0.1', 'Loopback synthetic HTTPS only'
context=ssl.create_default_context(cafile=str(out/'tls.crt'))
def request(path,method='GET',body=None,token=None,headers=None):
    assert path.startswith('/') and not path.startswith('//'), 'Same-origin rooted links only'
    headers=dict(headers or {})
    if token:headers['Authorization']='Bearer '+token
    if body is not None:headers['Content-Type']='application/json'
    req=urllib.request.Request(origin+path,method=method,headers=headers,data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req,context=context,timeout=10) as response:
            raw=response.read();return response.status,json.loads(raw) if response.headers.get('Content-Type','').startswith('application/json') else None
    except urllib.error.HTTPError as error:return error.code,None

def link(resource,relation,method):
    action=resource['_links'][relation];assert action['method']==method;return action['href']
status,anonymous=request('/api');assert status==200
status,issued=request(link(anonymous,'device-login','POST'),'POST',{'username':fixture['username'],'password':fixture['password']});assert status==200
token=issued['token']
status,session=request('/api',token=token);assert status==200
status,tracks=request(link(session,'songs','GET'),token=token);assert status==200
track=tracks['items'][0];action=link(track,'playback-grant','POST')
status,grant=request(action,'POST',token=token);assert status==200
audio=link(grant,'audio','GET')
before,_=request(audio,headers={'Range':'bytes=0-1023'});assert before==206
command=subprocess.run(['java','-jar',str(root/'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),'admin','revoke',session['session']['id'],'--data-dir='+str(out/'data')],capture_output=True,text=True,check=True)
after,_=request(audio,headers={'Range':'bytes=1024-2047'});assert after==401
head,_=request(audio,'HEAD');assert head==401
renewal,_=request(action,'POST',token=token);assert renewal==401
result={'rangeBeforeRevocation':before,'laterRangeAfterParentRevocation':after,'headAfterParentRevocation':head,'renewalAfterParentRevocation':renewal,'scope':'separate synthetic loopback session; no tokens or audio URLs in evidence','alreadyOpenResponse':'not required to retract buffered audio'}
(out/'grant-revocation-result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
