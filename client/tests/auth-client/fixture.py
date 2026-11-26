#!/usr/bin/env python3
"""Run a disposable real HTTPS API with original synthetic music. No retained library."""
import argparse, ipaddress, json, os, secrets, shutil, ssl, subprocess, time, urllib.request
from pathlib import Path
root = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--http', action='store_true', help='Explicit authenticated HTTP fixture on loopback; TLS enforcement stays on by default')
parser.add_argument('--private-host', help='Explicit RFC1918 IPv4 bind for the authorised disposable physical-device trial')
parser.add_argument('--port', type=int, help='Use a separate disposable service port when the default is occupied')
parser.add_argument('--output-directory', type=Path, help='Fresh ignored directory beneath client/target; preserves existing fixtures')
args = parser.parse_args()
host, port = '127.0.0.1', 18444
if args.http and args.private_host: parser.error('--http fixture is loopback-only')
if args.private_host:
    address = ipaddress.IPv4Address(args.private_host)
    if not any(address in ipaddress.ip_network(net) for net in ['10.0.0.0/8','172.16.0.0/12','192.168.0.0/16']):
        raise SystemExit('Physical trial requires an explicit RFC1918 IPv4 address')
    host, port = str(address), 18445
if args.port is not None:
    if not 1024 <= args.port <= 65535: parser.error('--port must be between 1024 and 65535')
    port = args.port
origin = f'{"http" if args.http else "https"}://{host}:{port}'
out = root / ('client/target/auth-device' if args.private_host else 'client/target/auth-client')
if args.output_directory:
    out = args.output_directory.resolve()
    if not out.is_relative_to((root/'client/target').resolve()):
        parser.error('--output-directory must be beneath ignored client/target')
    if out.exists() and any(out.iterdir()):
        parser.error('--output-directory must be fresh and empty')
out.mkdir(parents=True, exist_ok=True, mode=0o700)
if (out / 'data').exists():
    raise SystemExit('Fixture exists; select a fresh ignored output directory or explicitly archive it first.')
password = secrets.token_hex(16)
java = shutil.which('java')
if not java or 'version "21.' not in subprocess.run([java,'-version'],capture_output=True,text=True).stderr:
    raise SystemExit('Select the Java 21 JDK before preparing the fixture')
subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-sha256','-days','2','-nodes','-keyout',str(out/'tls.key'),'-out',str(out/'tls.crt'),'-subj','/CN=Normal Bias synthetic authentication trial','-addext',f'subjectAltName=IP:{host},IP:127.0.0.1,DNS:localhost','-addext','basicConstraints=critical,CA:TRUE','-addext','extendedKeyUsage=serverAuth'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
os.chmod(out/'tls.key',0o600)
subprocess.run(['openssl','pkcs12','-export','-inkey',str(out/'tls.key'),'-in',str(out/'tls.crt'),'-out',str(out/'tls.p12'),'-passout','stdin'],input=password+'\n',text=True,check=True)
os.chmod(out/'tls.p12',0o600)
config = out/'server.properties'
config.write_text(f'server.address={host}\nserver.port={port}\nserver.ssl.enabled=true\nnormalbias.auth.require-https=true\nserver.ssl.key-store={out}/tls.p12\nserver.ssl.key-store-password={password}\nnormalbias.data-dir={out}/data\nnormalbias.artwork.background-enabled=false\nspring.web.resources.static-locations=file:{root}/client/target/web/\n')
if args.http:
    config.write_text(f'server.address={host}\nserver.port={port}\nserver.ssl.enabled=false\nnormalbias.auth.require-https=false\nnormalbias.data-dir={out}/data\nnormalbias.artwork.background-enabled=false\nspring.web.resources.static-locations=file:{root}/client/target/web/\n')
os.chmod(config,0o600)
# These are disposable fixture credentials, stored only in ignored owner-readable files.
credentials = out/'fixture.json';credentials.write_text(json.dumps({'username':'listener','password':'Synthetic music only 2468','origin':origin}));os.chmod(credentials,0o600)
api = root/'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'
trust = ssl.create_default_context(cafile=str(out/'tls.crt'))
def call(path, body=None, bearer=None, raw=False):
    headers={}
    if bearer: headers['Authorization']='Bearer '+bearer
    if body is not None:
        headers['Content-Type']='audio/mpeg' if raw else 'application/json'
        if not raw: body=json.dumps(body).encode()
    with urllib.request.urlopen(urllib.request.Request(origin+path,data=body,headers=headers),context=trust,timeout=10) as response:
        return json.load(response)
log=(out/'api.log').open('a')
process=subprocess.Popen([java,'-jar',str(api),'--spring.config.additional-location=file:'+str(config)],stdout=log,stderr=subprocess.STDOUT)
(out/'api.pid').write_text(str(process.pid))
try:
    for _ in range(150):
        if process.poll() is not None: raise RuntimeError('Fixture API exited')
        try: call('/api'); break
        except OSError: time.sleep(.1)
    def admin(op,*args,stdin=None):
        result=subprocess.run([java,'-jar',str(api),'admin',op,*args,'--data-dir='+str(out/'data')],input=stdin,text=True,capture_output=True,check=True)
        return json.loads(result.stdout)
    admin('bootstrap','owner','--password-stdin',stdin='Synthetic owner only 8642\n')
    invite=admin('invite')
    call('/api/auth/invitations/accept',{'token':invite['token'],'username':'listener','password':'Synthetic music only 2468'})
    owner=call('/api/auth/device-sessions',{'username':'owner','password':'Synthetic owner only 8642'})['token']
    music=out/'music';music.mkdir()
    for index,title in enumerate(['Morning Circuit','Evening Signal']):
        base=220 if index==0 else 165
        source=f'aevalsrc=0.07*sin(2*PI*({base}+55*mod(floor(t*2)\\,4))*t)+0.04*sin(2*PI*{base*1.5}*t):s=44100:d=180'
        song=music/f'{index+1}.mp3'
        subprocess.run(['ffmpeg','-v','error','-f','lavfi','-i',source,'-ac','2','-c:a','libmp3lame','-b:a','256k','-metadata',f'title={title}','-metadata','artist=Normal Bias synthetic trial','-metadata','album=Signed in sessions',str(song)],check=True)
        call('/api/tracks?filename='+song.name,song.read_bytes(),owner,True)
    print(f'Disposable authentication fixture ready on {origin}',flush=True)
    process.wait()
finally:
    if process.poll() is None: process.terminate(); process.wait(timeout=15)
    log.close()
