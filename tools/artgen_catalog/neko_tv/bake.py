"""Bake the approved 96x64 Cat TV canvas into six synchronized native block sprites.

Run: python tools/artgen_catalog/neko_tv/bake.py
Requires Pillow, Playwright and installed Microsoft Edge. No network or plan/ inputs.
Runtime PNGs/mcmeta go to resources; previews and verification go to build/neko-tv-preview.
"""
import base64
import hashlib
import json
from pathlib import Path

from PIL import Image
from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
SOURCE = ROOT / 'src/main/resources/assets/gtit/textures/items/miao_coin.png'
OUTPUT = ROOT / 'src/main/resources/assets/gtit/textures/blocks'
PREVIEW = ROOT / 'build/neko-tv-preview'
FRAMES, FPS, FRAME_TIME = 120, 10, 2
COIN_SHA256 = '65c73db9baf2c76c8dbf7397dd26f523369bb6e343f13d1abe66b67f40696612'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def data_url(path, mime):
    return f'data:{mime};base64,' + base64.b64encode(path.read_bytes()).decode('ascii')


def main():
    assert digest(SOURCE.read_bytes()) == COIN_SHA256, 'Original coin source changed'
    coin = Image.open(SOURCE).convert('RGBA')
    assert coin.size == (32, 128)
    alpha_boxes = [coin.crop((0, n * 32, 32, (n + 1) * 32)).getchannel('A').getbbox()
                   for n in range(4)]
    assert min(min(box[2] - box[0], box[3] - box[1]) for box in alpha_boxes) * 1.5 > 32
    errors = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, channel='msedge')
        page = browser.new_page()
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.on('console', lambda m: errors.append(m.text) if m.type == 'error' else None)
        page.set_content('<!doctype html><meta charset="utf-8"><title>Cat TV bake</title>')
        page.add_script_tag(content=(HERE / 'source/render.js').read_text(encoding='utf-8'))
        page.evaluate('sources => loadSources(sources)', {
            'coin': data_url(SOURCE, 'image/png'),
            'background': data_url(HERE / 'source/assets/coin-vault.svg', 'image/svg+xml'),
        })
        frames = [Image.frombytes('RGBA', (96, 64), bytes(page.evaluate('t => pixelsAt(t)', n / FPS)))
                  for n in range(FRAMES)]
        assert bytes(page.evaluate('pixelsAt(12)')) == frames[0].tobytes()
        assert frames[-1].tobytes() != frames[0].tobytes(), 'Do not append duplicate endpoint'
        assert not errors, errors
        browser_version = browser.version
        browser.close()

    PREVIEW.mkdir(parents=True, exist_ok=True)
    metadata = {'animation': {'frametime': FRAME_TIME}}
    assets = []
    strips = []
    for tile in range(6):
        x, y = tile % 3 * 32, tile // 3 * 32
        strip = Image.new('RGBA', (32, 32 * FRAMES))
        for n, frame in enumerate(frames):
            strip.paste(frame.crop((x, y, x + 32, y + 32)), (0, n * 32))
        still_path = OUTPUT / f'nekotv_{tile + 1}.png'
        active_path = OUTPUT / f'nekotv_active_{tile + 1}.png'
        strip.crop((0, 0, 32, 32)).save(still_path, optimize=True)
        strip.save(active_path, optimize=True)
        active_path.with_suffix('.png.mcmeta').write_text(json.dumps(metadata) + '\n', encoding='utf-8')
        # Read the actual runtime files back before verifying their shared image.
        loaded = Image.open(active_path).convert('RGBA')
        assert loaded.size == (32, 3840)
        assert loaded.getchannel('A').getextrema() == (255, 255)
        assert Image.open(still_path).tobytes() == loaded.crop((0, 0, 32, 32)).tobytes()
        assert json.loads(active_path.with_suffix('.png.mcmeta').read_text()) == metadata
        strips.append(loaded)
        assets.append({'texture': f'gtit:nekotv_active_{tile + 1}', 'size': list(loaded.size),
                       'png_sha256': digest(active_path.read_bytes()),
                       'frame_sha256': [digest(loaded.crop((0, n * 32, 32, (n + 1) * 32)).tobytes())
                                        for n in range(FRAMES)]})
    for n, frame in enumerate(frames):
        reassembled = Image.new('RGBA', (96, 64))
        for tile, strip in enumerate(strips):
            reassembled.paste(strip.crop((0, n * 32, 32, (n + 1) * 32)),
                              (tile % 3 * 32, tile // 3 * 32))
        assert reassembled.tobytes() == frame.tobytes(), f'Frame {n} has a tile seam'

    frames[25].resize((768, 512), Image.Resampling.NEAREST).save(PREVIEW / 'screen.png')
    contact = Image.new('RGBA', (96 * 4, 64 * 3))
    for i, n in enumerate(range(0, FRAMES, 10)):
        contact.paste(frames[n], (i % 4 * 96, i // 4 * 64))
    contact.resize((1152, 576), Image.Resampling.NEAREST).save(PREVIEW / 'contact-sheet.png')
    previews = [frame.resize((384, 256), Image.Resampling.NEAREST).convert('RGB') for frame in frames]
    previews[0].save(PREVIEW / 'screen.gif', save_all=True, append_images=previews[1:],
                     duration=100, loop=0, optimize=False)
    report = {'composition': 'landscape-bigcoin-5', 'browser': browser_version,
              'scope': 'Native resource pixels only; not an in-game rendering or structure test',
              'source_coin_sha256': COIN_SHA256, 'coin_alpha_boxes': alpha_boxes,
              'frames': FRAMES, 'fps': FPS, 'frame_time_ticks': FRAME_TIME, 'period_seconds': 12,
              'tile_order': 'top to bottom, left to right', 'rgba': 'all opaque',
              'reassembled_frames_equal_canvas': FRAMES, 'endpoint_12s_equals_0s': True,
              'duplicate_endpoint_omitted': True, 'browser_errors': errors, 'assets': assets}
    (PREVIEW / 'verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'frames': FRAMES, 'tiles': len(strips), 'period_seconds': 12,
                      'runtime_bytes': sum(p.stat().st_size for p in OUTPUT.glob('nekotv*')),
                      'preview': str(PREVIEW), 'verification': 'PASS'}))


if __name__ == '__main__':
    main()
