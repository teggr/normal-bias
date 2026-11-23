#!/usr/bin/env python3
"""Copy production sources and add test-only inspection; all actual UI/session/audio code is preserved."""
from pathlib import Path
import shutil
root=Path(__file__).resolve().parents[3]
copy=root/'client/target/auth-client/client'
if copy.exists(): raise SystemExit('Ignored client copy already exists')
shutil.copytree(root/'client',copy,ignore=shutil.ignore_patterns('target','node_modules'))
common=copy/'common/src/main/java/dev/normalbias/client'
shutil.copyfile(Path(__file__).with_name('ClientProbe.java'),common/'ClientProbe.java')
p=common/'NormalBias.java';s=p.read_text();assert s.count('    @Override public void runApp() {')==1
p.write_text(s.replace('    @Override public void runApp() {','    @Override public void runApp() {\n        ClientProbe.start();'))
p=common/'ui/LibraryForm.java';s=p.read_text();assert s.count('    public LibraryForm(LibraryApi api) {')==1
p.write_text(s.replace('    public LibraryForm(LibraryApi api) {','    public Player probePlayer() { return player; }\n    public LibraryForm(LibraryApi api) {'))
print('Prepared production client with test-only UI inspection in ignored output')
