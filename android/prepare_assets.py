#!/usr/bin/env python3
"""Bundle the authoritative web game and pinned dependencies for offline Android."""
from __future__ import annotations

import argparse
import hashlib
from html.parser import HTMLParser
from pathlib import Path
import shutil
import urllib.request

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / 'app/src/main/assets'
THREE_URL = 'https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.min.js'
FONT_CSS = 'https://fonts.googleapis.com/css2?family=Krona+One&display=swap'
DEPENDENCIES = {
    'vendor/three.min.js': (THREE_URL, '170c6789f43217c96b3170f4b42fafe135de7f7cd48497a4218f9757ee1d49fa'),
    'fonts/KronaOne-Regular.ttf': (
        'https://fonts.gstatic.com/s/kronaone/v15/jAnEgHdjHcjgfIb1ZcUCMQ.ttf',
        '9418a4b349a204d52a83487471d2c41a3bccb25e13367c1f270d0061c4fa2683'),
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class ResourceCheck(HTMLParser):
    def __init__(self):
        super().__init__()
        self.paths = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        for key in ('src', 'href'):
            if tag in ('script', 'link', 'img') and attrs.get(key):
                self.paths.append(attrs[key])


def verify_html(html: str) -> None:
    parser = ResourceCheck()
    parser.feed(html)
    assert 'vendor/three.min.js' in parser.paths, 'Bundled Three.js is missing'
    for resource in parser.paths:
        assert not resource.startswith(('http:', 'https:', '//')), resource
        assert (ASSETS / resource).is_file(), resource
    assert 'https://fonts.googleapis.com/' not in html
    assert 'fonts/KronaOne-Regular.ttf' in html
    assert 'id="difficulty"' in html


def prepare(offline=False) -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    for relative, (url, expected) in DEPENDENCIES.items():
        path = ASSETS / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.is_file() or sha256(path.read_bytes()) != expected:
            if offline:
                raise RuntimeError(f'Missing verified offline dependency: {relative}')
            with urllib.request.urlopen(url, timeout=90) as response:
                data = response.read()
            if sha256(data) != expected:
                raise RuntimeError(f'Dependency checksum changed: {relative}')
            path.write_bytes(data)

    source = (ROOT.parent / 'index.html').read_text(encoding='utf-8')
    script = f'<script src="{THREE_URL}"></script>'
    font = f'<link href="{FONT_CSS}" rel="stylesheet">'
    assert source.count(script) == 1 and source.count(font) == 1
    html = source.replace(script, '<script src="vendor/three.min.js"></script>').replace(font, '')
    local_font = '''<style>@font-face{font-family:'Krona One';font-style:normal;font-weight:400;font-display:swap;src:url('fonts/KronaOne-Regular.ttf') format('truetype');}</style>'''
    html = html.replace('</head>', local_font + '\n</head>')
    html = html.replace('Unable to load Three.js (internet required to load it from the CDN).',
                        'Unable to load bundled graphics. Please reinstall the app.')
    (ASSETS / 'index.html').write_text(html, encoding='utf-8')
    for name in ('THREE-LICENSE.txt', 'KRONA-OFL.txt'):
        shutil.copyfile(ROOT / 'licenses' / name, ASSETS / name)
    verify_html(html)
    print('Offline assets verified: game, Three.js 0.160.0, Krona One, licenses.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--offline', action='store_true')
    prepare(parser.parse_args().offline)
