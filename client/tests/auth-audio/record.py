#!/usr/bin/env python3
"""Capture native simulator video and process-isolated audio on one wall clock.
Usage: record.py TRIAL_DIRECTORY OUTPUT_DIRECTORY SIMULATOR_UUID AUDIO_PROCESS_ID
Touch OUTPUT_DIRECTORY/stop-recording to finalize both captures.
"""
import json
from pathlib import Path
import signal
import subprocess
import sys
import time

trial, output = map(Path, sys.argv[1:3])
device, process = sys.argv[3:5]
output.mkdir(parents=True, exist_ok=True)
stop = output / 'stop-recording'
audio_stop = output / 'stop-audio'
if any((output / name).exists() for name in ['native.mov', 'audio.wav', 'stop-recording', 'stop-audio']):
    raise SystemExit('Use a fresh recording directory')
audio = subprocess.Popen([str(trial / 'capture-audio'), process, str(output / 'audio.wav'), str(audio_stop)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
video = None
try:
    line = audio.stdout.readline().strip()
    if not line.startswith('Recording actual simulator process audio at '):
        raise RuntimeError('Audio capture did not start: ' + line)
    audio_start = float(line.rsplit(' ', 1)[1])
    video = subprocess.Popen(['xcrun', 'simctl', 'io', device, 'recordVideo', '--codec=h264', str(output / 'native.mov')], stderr=subprocess.PIPE, stdout=subprocess.DEVNULL, text=True)
    for line in video.stderr:
        if 'Recording started' in line:
            video_start = time.time()
            break
    else:
        raise RuntimeError('Native video capture did not start')
    (output / 'timing.json').write_text(json.dumps({'audioStartedAt': audio_start, 'videoReadyAt': video_start,
                                                 'audioLeadSeconds': video_start-audio_start,
                                                 'method': 'Recorder start notifications on the same host clock; approximate frame alignment'}, indent=2))
    print('Capture ready; native screen plus actual simulator-process audio', flush=True)
    while not stop.exists():
        if audio.poll() is not None or video.poll() is not None:
            raise RuntimeError('A capture process stopped unexpectedly')
        time.sleep(.1)
finally:
    if video is not None and video.poll() is None:
        video.send_signal(signal.SIGINT)
        video.wait(timeout=15)
    audio_stop.touch()
    audio.wait(timeout=10)
print('Finalized native video and audio', flush=True)
