#!/usr/bin/env python3
"""Prepare an ignored production-source copy with test-only MCP; no service or app is started."""
from pathlib import Path
import shutil,subprocess,os,json
root=Path(__file__).resolve().parents[2];out=root/'client/target/scroll-native';out.mkdir(parents=True,exist_ok=True)
copy=out/'client'
if copy.exists(): raise SystemExit('Isolated client already exists; use a fresh ignored output directory rather than overwriting a fixture')
shutil.copytree(root/'client',copy,ignore=shutil.ignore_patterns('target','node_modules'))
common=copy/'common/src/main/java/dev/normalbias/client'
probe=(root/'client/tests/auth-client/ClientProbe.java').read_text().replace('18868','18888')
(common/'ClientProbe.java').write_text(probe)
p=common/'NormalBias.java';s=p.read_text().replace('    @Override public void runApp() {','    @Override public void runApp() {\n        ClientProbe.start();');p.write_text(s)
p=common/'ui/LibraryForm.java';s=p.read_text().replace('    public LibraryForm(LibraryApi api) {','    public Player probePlayer() { return player; }\n    public LibraryForm(LibraryApi api) {');p.write_text(s)
subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-sha256','-days','2','-nodes','-keyout',str(out/'tls.key'),'-out',str(out/'tls.crt'),'-subj','/CN=Normal Bias synthetic scroll trial','-addext','subjectAltName=IP:127.0.0.1,DNS:localhost','-addext','basicConstraints=critical,CA:TRUE','-addext','extendedKeyUsage=serverAuth'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
subprocess.run(['openssl','pkcs12','-export','-inkey',str(out/'tls.key'),'-in',str(out/'tls.crt'),'-out',str(out/'tls.p12'),'-passout','pass:synthetic-scroll-only'],check=True)
(out/'server.properties').write_text(f'server.address=127.0.0.1\nserver.port=19544\nserver.ssl.enabled=true\nserver.ssl.key-store={out}/tls.p12\nserver.ssl.key-store-password=synthetic-scroll-only\nnormalbias.data-dir={out}/data\nnormalbias.auth.mode=demo\nnormalbias.artwork.background-enabled=false\n')
cp=str(root/'api/target/test-classes')+os.pathsep+str(root/'api/target/classes')+os.pathsep+(root/'api/target/test-classpath.txt').read_text().strip()
(out/'classpath.txt').write_text(cp)
print('Prepared isolated simulator sources and synthetic TLS configuration')
