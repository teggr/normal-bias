#!/usr/bin/env python3
"""Exercise ONLY the disposable gate; consumes its pairing and revokes its session.
Run on a fresh gate with grant-ms=1000, then restart with the default lifetime.
Never prints fixture credentials or playback URLs.
"""
import json
from pathlib import Path
import ssl
import sys
import time
import urllib.error
import urllib.request

out = Path(sys.argv[1])
settings = dict(line.split('=', 1) for line in (out / 'server.properties').read_text().splitlines())
assert settings.get('grant-ms') == '1000', 'Use a fresh disposable gate with grant-ms=1000'
context = ssl.create_default_context(cafile=str(out / 'tls.crt'))
origin = 'https://127.0.0.1:18443'
passed = []
def call(path, expected, method='GET', body=None, headers=None):
    request = urllib.request.Request(origin + path, data=body, headers=headers or {}, method=method)
    try:
        with urllib.request.urlopen(request, context=context, timeout=10) as response:
            status, data = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, data = error.code, error.read()
    assert status == expected, f'Expected HTTP {expected}; received {status}'
    return data
def check(name, fn):
    fn(); passed.append(name)
tracks = json.load(urllib.request.urlopen('http://127.0.0.1:18444/api/tracks'))['items']
first, second = tracks[0]['id'], tracks[1]['id']
check('anonymous catalogue denied', lambda: call('/api/tracks', 401))
check('anonymous HEAD audio denied', lambda: call(f'/api/tracks/{first}/audio', 401, 'HEAD'))
check('anonymous Range audio denied', lambda: call(f'/api/tracks/{first}/audio', 401, headers={'Range': 'bytes=0-1023'}))
check('wrong pairing denied', lambda: call('/trial/session', 401, 'POST', b'', {'X-Trial-Pairing': 'invalid'}))
session = json.loads(call('/trial/session', 200, 'POST', b'', {'X-Trial-Pairing': settings['bootstrap']}))
auth = {'Authorization': 'Bearer ' + session['token']}
check('pairing cannot be replayed', lambda: call('/trial/session', 401, 'POST', b'', {'X-Trial-Pairing': settings['bootstrap']}))
check('authenticated catalogue allowed', lambda: call('/api/tracks', 200, headers=auth))
grant_action = session['_links']['grant']
grant = json.loads(call(grant_action['href'], 200, grant_action['method'], first.encode(), auth))['_links']['audio']['href']
check('grant allows HEAD', lambda: call(grant, 200, 'HEAD'))
check('grant allows Range', lambda: call(grant, 206, headers={'Range': 'bytes=0-1023'}))
check('grant allows repeated Range', lambda: call(grant, 206, headers={'Range': 'bytes=65536-66559'}))
check('grant cannot read another track', lambda: call(grant.replace(first, second), 401))
check('grant cannot mutate audio', lambda: call(grant, 405, 'POST', b''))
check('grant cannot read catalogue', lambda: call('/api/tracks?' + grant.split('?', 1)[1], 401))
time.sleep(1.1)
check('expired grant denied', lambda: call(grant, 401, headers={'Range': 'bytes=0-1023'}))
grant = json.loads(call(grant_action['href'], 200, grant_action['method'], first.encode(), auth))['_links']['audio']['href']
urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:18445/revoke', data=b'', method='POST')).read()
check('revoked session cannot mint grant', lambda: call(grant_action['href'], 401, 'POST', first.encode(), auth))
check('revoked parent blocks existing grant', lambda: call(grant, 401, headers={'Range': 'bytes=0-1023'}))
(out / 'gate-checks.json').write_text(json.dumps({'passed': passed}, indent=2) + '\n')
print(f'PASS: {len(passed)} disposable TLS/auth/grant checks; no secrets printed')
