"""Verify signed distribution contains the exact committed assets and app metadata."""
from pathlib import Path
import sys,zipfile
apk,root=Path(sys.argv[1]),Path(sys.argv[2])
with zipfile.ZipFile(apk) as z:
 for p in (root/'app/src/main/assets').iterdir():
  assert z.read('assets/'+p.name)==p.read_bytes(), f'Asset mismatch: {p}'
 assert len(z.read('classes.dex'))>10000
 assert 'AndroidManifest.xml' in z.namelist()
print('PASS: all distribution assets match source; Android manifest and DEX present.')
