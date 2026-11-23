#!/usr/bin/env python3
"""Export actual simulator video/audio on their shared clock, including static lock frames."""
import json, subprocess, sys
from pathlib import Path
out=Path(sys.argv[1])
def duration(path):
    return float(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','default=nw=1:nk=1',str(path)],text=True))
lead=json.loads((out/'timing.json').read_text())['audioLeadSeconds']
if lead<0: raise SystemExit('Unexpected negative capture lead; inspect timing before export')
length=min(duration(out/'native.mov'),duration(out/'audio.wav')-lead)
if length<=0: raise SystemExit('Empty capture')
# A sparse lock screen can have long frame gaps. Explicit duration avoids FFmpeg's
# shortest-buffer cutoff; CFR repeats the real captured static frames, never audio.
subprocess.run(['ffmpeg','-v','error','-y','-i',str(out/'native.mov'),'-ss',str(lead),'-i',str(out/'audio.wav'),
    '-map','0:v:0','-map','1:a:0','-vf','fps=30,tpad=stop_mode=clone:stop_duration='+str(length),'-t',str(length),
    '-c:v','libx264','-preset','fast','-crf','23','-pix_fmt','yuv420p','-c:a','aac','-b:a','128k',
    '-movflags','+faststart',str(out/'demo.mp4')],check=True)
print(f'Exported actual native capture: {length:.3f} seconds; audio lead {lead:.3f} seconds')
