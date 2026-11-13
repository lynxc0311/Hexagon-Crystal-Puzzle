#!/usr/bin/env python3
"""Check that the compiled APK actually contains the complete offline game."""
from pathlib import Path
import sys
import zipfile

from prepare_assets import ASSETS, DEPENDENCIES, sha256, verify_html

apk_path = Path(sys.argv[1])
with zipfile.ZipFile(apk_path) as apk:
    assert apk.testzip() is None, 'APK has a corrupt ZIP entry'
    for required in ('classes.dex', 'AndroidManifest.xml', 'resources.arsc',
                     'assets/index.html', 'assets/THREE-LICENSE.txt', 'assets/KRONA-OFL.txt'):
        assert required in apk.namelist(), required
    for relative, (_, expected) in DEPENDENCIES.items():
        assert sha256(apk.read('assets/' + relative)) == expected, relative
    html = apk.read('assets/index.html').decode('utf-8')
    verify_html(html)
    assert apk.read('assets/index.html') == (ASSETS / 'index.html').read_bytes()
print(f'APK content verified: {apk_path.name} ({apk_path.stat().st_size:,} bytes)')
