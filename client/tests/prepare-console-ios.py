#!/usr/bin/env python3
"""Prepare an isolated production-source copy with a test-only MCP probe."""
from pathlib import Path
import shutil, subprocess
root=Path(__file__).resolve().parents[2]
out=root/'client/target/console-native'
out.mkdir(parents=True,exist_ok=True)
copy=out/'client'
if copy.exists(): raise SystemExit('Use a fresh ignored native output directory')
shutil.copytree(root/'client',copy,ignore=shutil.ignore_patterns('target','node_modules'))
common=copy/'common/src/main/java/dev/normalbias/client'
probe=(root/'client/tests/auth-client/ClientProbe.java').read_text().replace('18868','18890')
probe=probe.replace('if (name.equals(component.getName())', 'if (name.equals(component.getSemantics().getLabel()) || name.equals(component.getName())')
(common/'ClientProbe.java').write_text(probe)
p=common/'NormalBias.java';p.write_text(p.read_text().replace('    @Override public void runApp() {','    @Override public void runApp() {\n        ClientProbe.start();'))
p=common/'ui/LibraryForm.java';p.write_text(p.read_text().replace('    public LibraryForm(LibraryApi api) {','    public Player probePlayer() { return player; }\n    public LibraryForm(LibraryApi api) {'))
subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-sha256','-days','2','-nodes','-keyout',str(out/'tls.key'),'-out',str(out/'tls.crt'),'-subj','/CN=Normal Bias console test','-addext','subjectAltName=IP:127.0.0.1,DNS:localhost','-addext','basicConstraints=critical,CA:TRUE','-addext','extendedKeyUsage=serverAuth'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
subprocess.run(['openssl','pkcs12','-export','-inkey',str(out/'tls.key'),'-in',str(out/'tls.crt'),'-out',str(out/'tls.p12'),'-passout','pass:synthetic-console-only'],check=True)
(out/'server.properties').write_text(f'server.address=127.0.0.1\nserver.port=19645\nserver.ssl.enabled=true\nserver.ssl.key-store={out}/tls.p12\nserver.ssl.key-store-password=synthetic-console-only\nnormalbias.data-dir={out}/data\nnormalbias.auth.mode=demo\nnormalbias.artwork.background-enabled=false\n')
print('Prepared isolated console sources, MCP port 18890 and TLS fixture port 19645')
