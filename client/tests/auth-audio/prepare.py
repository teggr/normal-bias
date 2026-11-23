#!/usr/bin/env python3
"""Prepare an ignored copy of the actual client and disposable TLS/audio fixtures.

No tracked app source, real library, physical device or system trust is changed.
The bootstrap value is a disposable fixture secret, never a real account credential.
"""
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess

client = Path(__file__).resolve().parents[2]
out = client / 'target/auth-audio'
if out.exists():
    raise SystemExit('Trial output already exists; use the existing run or explicitly archive it before preparing a new one.')
out.mkdir(parents=True, mode=0o700)
fixtures = out / 'fixtures'
fixtures.mkdir()
for index, title in enumerate(['Morning Circuit', 'Evening Signal']):
    base = 220 if index == 0 else 165
    source = f'aevalsrc=0.07*sin(2*PI*({base}+55*mod(floor(t*2)\\,4))*t)+0.04*sin(2*PI*{base * 1.5}*t):s=44100:d=180'
    subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', source, '-ac', '2', '-c:a', 'libmp3lame', '-b:a', '256k',
                    '-metadata', f'title={title}', '-metadata', 'artist=Normal Bias synthetic trial',
                    '-metadata', 'album=Authenticated Audio', str(fixtures / f'{index + 1}.mp3')], check=True)
bootstrap, password = secrets.token_hex(32), secrets.token_hex(24)
(out / 'server.properties').write_text(f'bootstrap={bootstrap}\npassword={password}\n')
os.chmod(out / 'server.properties', 0o600)
subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-sha256', '-days', '2', '-nodes',
                '-keyout', str(out / 'tls.key'), '-out', str(out / 'tls.crt'), '-subj', '/CN=Normal Bias disposable audio trial',
                '-addext', 'subjectAltName=IP:127.0.0.1,DNS:localhost',
                '-addext', 'basicConstraints=critical,CA:TRUE', '-addext', 'extendedKeyUsage=serverAuth'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
os.chmod(out / 'tls.key', 0o600)
# Supply the ephemeral export password over stdin, never in process arguments.
subprocess.run(['openssl', 'pkcs12', '-export', '-inkey', str(out / 'tls.key'), '-in', str(out / 'tls.crt'),
                '-out', str(out / 'tls.p12'), '-passout', 'stdin'], input=password + '\n', text=True, check=True)
os.chmod(out / 'tls.p12', 0o600)
copy = out / 'client'
shutil.copytree(client, copy, ignore=shutil.ignore_patterns('target', 'node_modules'))
common = copy / 'common/src/main/java/dev/normalbias/client'
(common / 'NormalBias.java').write_text('''package dev.normalbias.client;
import com.codename1.system.Lifecycle;
import com.codename1.io.NetworkEvent;
import dev.normalbias.client.player.AuthAudioProbe;
public class NormalBias extends Lifecycle {
    @Override protected void bindCrashProtection() { }
    @Override protected void handleNetworkError(NetworkEvent event) { event.consume(); }
    @Override public void runApp() { AuthAudioProbe.boot(); }
    @Override public void destroy() { AuthAudioProbe.dispose(); }
}
''')
shutil.copyfile(Path(__file__).with_name('AuthAudioProbe.java'), common / 'player/AuthAudioProbe.java')
def replace(path, before, after, count=1):
    text = path.read_text()
    if text.count(before) != count:
        raise SystemExit(f'Pinned source shape changed: {path.name}; review trial injection')
    path.write_text(text.replace(before, after))
replace(common / 'player/Player.java', 'new FrameworkAudio(this::poll)', 'AuthAudioProbe.audio(this::poll)')
replace(common / 'ui/LibraryForm.java', '    public LibraryForm(LibraryApi api) {',
        '    public Player probePlayer() { return player; }\n    public LibraryForm(LibraryApi api) {')
api = common / 'net/LibraryApi.java'
text = api.read_text()
if text.count('request.setUrl(') != 3:
    raise SystemExit('LibraryApi transport shape changed; review trial injection')
api.write_text(text.replace('request.setUrl(', 'dev.normalbias.client.player.AuthAudioProbe.authorize(request);\n        request.setUrl('))
resource = copy / 'common/src/main/resources/auth-audio-trial.json'
resource.parent.mkdir(exist_ok=True)
resource.write_text(json.dumps({'bootstrap': bootstrap}))
os.chmod(resource, 0o600)
print(f'Prepared synthetic trial at {out}; no secrets printed. Build only this copied client.')
