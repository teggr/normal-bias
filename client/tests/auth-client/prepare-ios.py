#!/usr/bin/env python3
"""Copy production sources and add test-only inspection; all actual UI/session/audio code is preserved."""
from pathlib import Path
import shutil, os
root=Path(__file__).resolve().parents[3]
out=Path(os.environ.get('NORMALBIAS_AUTH_FIXTURE_DIR',root/'client/target/auth-client')).resolve()
if not out.is_relative_to((root/'client/target').resolve()): raise SystemExit('Fixture must be beneath ignored client/target')
port=int(os.environ.get('NORMALBIAS_AUTH_PROBE_PORT','18868'))
if not 1024 <= port <= 65535: raise SystemExit('Invalid probe port')
copy=out/'client'
if copy.exists(): raise SystemExit('Ignored client copy already exists')
shutil.copytree(root/'client',copy,ignore=shutil.ignore_patterns('target','node_modules'))
common=copy/'common/src/main/java/dev/normalbias/client'
probe=Path(__file__).with_name('ClientProbe.java').read_text()
(common/'ClientProbe.java').write_text(probe.replace('MCP.startSocketServer(18868)', 'MCP.startSocketServer('+str(port)+')'))
p=common/'NormalBias.java';s=p.read_text();assert s.count('    @Override public void runApp() {')==1
p.write_text(s.replace('    @Override public void runApp() {','    @Override public void runApp() {\n        ClientProbe.start();'))
p=common/'ui/LibraryForm.java';s=p.read_text();assert s.count('    public LibraryForm(LibraryApi api) {')==1
p.write_text(s.replace('    public LibraryForm(LibraryApi api) {','    public Player probePlayer() { return player; }\n    public LibraryForm(LibraryApi api) {'))
print('Prepared production client with test-only UI inspection in ignored output')
