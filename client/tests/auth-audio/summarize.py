#!/usr/bin/env python3
"""Assert captured native outcomes; emits a credential-free evidence summary."""
from datetime import datetime
import json
from pathlib import Path
import sys
import subprocess

trial, recording = map(Path, sys.argv[1:3])
decoder = json.JSONDecoder()
snapshot = recording / 'native-events.jsonl'
text = (snapshot if snapshot.exists() else trial / 'events.jsonl').read_text()
events = []
while text.strip():
    text = text.lstrip()
    value, end = decoder.raw_decode(text)
    events.append(value)
    text = text[end:]
start = json.loads((recording / 'timing.json').read_text())['videoReadyAt']
duration = float(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'default=nw=1:nk=1', str(recording / 'demo.mp4')], text=True))
background = json.loads((recording / 'background.json').read_text())['settingsForegroundAt']
def timestamp(event):
    return event['atMs']/1000 if 'atMs' in event else datetime.fromisoformat(event['at']).timestamp()
# Include the ending snapshot requested immediately after stopping the capture;
# exclude later app-relaunch state, which must not stand in for the recorded ending.
states = [e for e in events if e.get('kind') == 'state' and start < timestamp(e) < start + duration + 2]
playing_seek = next(e for e in states if e['op'] == 'seek90' and e['playing'] and e['time'] == 90_000)
paused_seek = next(e for e in states if e['op'] == 'seek10' and not e['playing'] and e['time'] == 10_000)
assert any(e['op'] == 'snapshot' and not e['playing'] and e['time'] == 10_000 and timestamp(e) > timestamp(paused_seek) for e in states)
grants = [e for e in events if e.get('kind') == 'grant' and timestamp(e) > background]
assert grants, 'No new grant after Settings was foregrounded'
assert any(e.get('kind') == 'audio' and e['code'] == 206 and timestamp(e) > timestamp(grants[0]) for e in events)
assert any(e['index'] == 1 and e['playing'] and e['time'] > 5000 and timestamp(e) > timestamp(grants[0]) for e in states)
assert any(e.get('kind') == 'audio' and e['code'] == 206 and e.get('detail', '').startswith('bytes=5586944-') and timestamp(e) > start for e in events), 'No late fresh Range observed'
assert any(e.get('kind') == 'keychain' and e.get('roundtrip') and e.get('restored') for e in events), 'Keychain restoration not observed'
assert any(e.get('kind') == 'headers' and not e['supported'] for e in events)
assert states[-1]['playing'] is False and states[-1]['index'] == 1
summary = {
    'nativeChecks': ['playing seek jumps to 90 seconds', 'paused seek jumps to 10 seconds and stays paused',
                     'fresh authenticated late Range', 'new grant and next-track audio after backgrounding',
                     'next track advances beyond five seconds', 'ending paused', 'Keychain credential restored after relaunch',
                     'native stream headers unsupported'],
    'playingSampleSeconds': timestamp(playing_seek)-start+1,
    'pausedSampleSeconds': timestamp(paused_seek)-start+1,
    'backgroundAtSeconds': background-start,
    'backgroundGrantAtSeconds': timestamp(grants[0])-start,
    'limits': 'iOS simulator only; synthetic trial authentication; no production login or physical-device acceptance'
}
(recording / 'native-checks.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary, indent=2))
