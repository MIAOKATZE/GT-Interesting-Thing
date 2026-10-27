"""Bake the wood cabinet / vanilla-model cats / coins into 19 native block sprites.

Run: python tools/artgen_catalog/neko_tv/bake.py
Requires Pillow, NumPy, Playwright, Microsoft Edge and Gradle's Minecraft resource jar.
Cat geometry is the extracted source/cat-model.json; original textures come from that jar.
Runtime PNGs/mcmeta go to resources; source/provenance/visual QA go to build/neko-tv-preview.
"""
import base64
import hashlib
import io
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image
from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE / 'source'))
# User-requested standalone renderer, kept outside the mod project.
sys.path.insert(0, str(ROOT.parent / '贴图复合渲染/src'))
from cat_scene import MODEL, STORY, render_frames, verify_story

SOURCE = ROOT / 'src/main/resources/assets/gtit/textures/items/miao_coin.png'
OUTPUT = ROOT / 'src/main/resources/assets/gtit/textures/blocks'
CASING = OUTPUT / 'neko_vending_casing_side.png'
CONTROLLER = OUTPUT / 'neko_vending_controller_on.png'
PREVIEW = ROOT / 'build/neko-tv-preview'
WIDTH, HEIGHT = 160, 128
TILES = [(col * 32, row * 32) for row in range(4) for col in range(5)
         if (row, col) != (3, 2)]
FRAMES, FPS, FRAME_TIME, PERIOD = 240, 10, 2, 24
COIN_SHA256 = '65c73db9baf2c76c8dbf7397dd26f523369bb6e343f13d1abe66b67f40696612'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def data_url(path, mime):
    return f'data:{mime};base64,' + base64.b64encode(path.read_bytes()).decode('ascii')


def main():
    assert digest(SOURCE.read_bytes()) == COIN_SHA256, 'Original coin source changed'
    PREVIEW.mkdir(parents=True, exist_ok=True)
    story_checks = verify_story()
    coin_flow_quality = json.loads(subprocess.check_output(
        [sys.executable,'-B',str(HERE / 'source/coin_flow_quality.py')],text=True,encoding='utf-8'))
    coin = Image.open(SOURCE).convert('RGBA')
    assert coin.size == (32, 128)
    alpha_boxes = [coin.crop((0, n * 32, 32, (n + 1) * 32)).getchannel('A').getbbox()
                   for n in range(4)]
    print('Rendering two vanilla ModelOcelot models into offline frames...', flush=True)
    cat_frames, poses = render_frames(FRAMES, FPS)
    assert cat_frames[0].getbbox() is None and cat_frames[-1].getbbox() is None
    assert all(cat_frames[n].getbbox() for n in (40,60,80,120,140,160,200))
    cat_sheet = Image.new('RGBA', (WIDTH, HEIGHT*FRAMES))
    for n, frame in enumerate(cat_frames):
        cat_sheet.paste(frame, (0,n*HEIGHT))
    cat_sheet.save(PREVIEW / 'cats-source.png', optimize=True)
    errors = []
    print('Compositing coins, drawer occlusion and synchronized 19 sprites...', flush=True)
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, channel='msedge')
        page = browser.new_page()
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.on('console', lambda m: errors.append(m.text) if m.type == 'error' else None)
        page.set_content('<!doctype html><meta charset="utf-8"><title>Cat cabinet bake</title>')
        page.add_script_tag(content=(HERE / 'source/coin_flow.js').read_text(encoding='utf-8'))
        page.add_script_tag(content=(HERE / 'source/render.js').read_text(encoding='utf-8'))
        page.evaluate('sources => loadSources(sources)', {
            'coin': data_url(SOURCE, 'image/png'),
            'background': data_url(HERE / 'source/assets/coin-vault.svg', 'image/svg+xml'),
            'casing': data_url(CASING, 'image/png'),
            'cats': data_url(PREVIEW / 'cats-source.png', 'image/png'),
        })
        composition = page.evaluate('nekoTV')
        rendered_alpha_boxes = page.evaluate('''async ({src,size}) => {
            const image = new Image(); image.src = src; await image.decode();
            const canvas = document.createElement('canvas'); canvas.width = canvas.height = size;
            const ctx = canvas.getContext('2d'); ctx.imageSmoothingEnabled = false;
            return [0,1,2,3].map(frame => {
                ctx.clearRect(0,0,size,size);
                ctx.drawImage(image,0,frame*32,32,32,0,0,size,size);
                const data = ctx.getImageData(0,0,size,size).data;
                let x0=size,y0=size,x1=0,y1=0;
                for (let y=0; y<size; y++) for (let x=0; x<size; x++) if (data[(y*size+x)*4+3]) {
                    x0=Math.min(x0,x); y0=Math.min(y0,y); x1=Math.max(x1,x+1); y1=Math.max(y1,y+1);
                }
                return [x0,y0,x1,y1];
            });
        }''', {'src': data_url(SOURCE,'image/png'), 'size': composition['coinSize']})
        assert min(min(b[2] - b[0], b[3] - b[1]) for b in rendered_alpha_boxes) >= 37.5
        assert len(composition['particles']) == 8
        assert len({c['speed'] for c in composition['particles']}) == 8
        assert all(c['cycles'] == int(c['cycles']) and c['speed'] > 0
                   for c in composition['particles'])
        # Lossless PNG avoids serializing 19 million individual channel integers over Playwright.
        frames = [Image.open(io.BytesIO(base64.b64decode(page.evaluate(
            't => { pixelsAt(t); return canvas.toDataURL("image/png").split(",")[1]; }',n/FPS)))).convert('RGBA')
            for n in range(FRAMES)]
        assert bytes(page.evaluate('pixelsAt(24)')) == frames[0].tobytes()
        for n in (1, 37, 79, 119, 140, 239):
            assert bytes(page.evaluate('t => pixelsAt(t)', 24+n/FPS)) == frames[n].tobytes()
        assert frames[-1].tobytes() != frames[0].tobytes(), 'Do not append duplicate endpoint'
        # Compare full versus coin-disabled rendering: bottom row must have zero coin contribution.
        bottom_coin_checks = page.evaluate('''() => {
            let checked = 0;
            for (let n = 0; n < 240; n++) {
                const full = pixelsAt(n/10), noCoins = pixelsAt(n/10, false);
                for (let i = 96*160*4; i < full.length; i++) {
                    if (full[i] !== noCoins[i]) throw new Error(`Coin leaked into bottom row at ${n}`);
                }
                const noCats = pixelsAt(n/10, true, false);
                // An empty counter is solid warm wood, with no old near-black opening.
                for (let y = 96; y < 104; y++) for (let x = 2; x < 158; x++) {
                    if (x >= 64 && x < 96) continue;
                    const i = (y*160+x)*4;
                    if (noCats[i] < 100 || noCats[i+1] < 65 || noCats[i+2] < 30)
                        throw new Error(`Dark opening remains in solid counter at ${n}`);
                }
                for (let y = 107; y < 126; y++) for (let x = 2; x < 158; x++) {
                    if (x >= 62 && x < 98) continue;
                    for (let c = 0; c < 4; c++) {
                        const i = (y*160+x)*4+c;
                        if (full[i] !== noCats[i]) throw new Error(`Cat escaped solid counter front at ${n}`);
                    }
                }
                checked++;
            }
            return checked;
        }''')
        assert bottom_coin_checks == FRAMES
        assert not errors, errors
        browser_version = browser.version
        browser.close()

    metadata = {'animation': {'frametime': FRAME_TIME}}
    assets, strips = [], []
    for tile, (x,y) in enumerate(TILES):
        strip = Image.new('RGBA', (32,32*FRAMES))
        for n, frame in enumerate(frames):
            strip.paste(frame.crop((x,y,x+32,y+32)), (0,n*32))
        still_path = OUTPUT / f'nekotv_full_{tile+1}.png'
        active_path = OUTPUT / f'nekotv_full_active_{tile+1}.png'
        strip.crop((0,0,32,32)).save(still_path, optimize=True)
        strip.save(active_path, optimize=True)
        active_path.with_suffix('.png.mcmeta').write_text(json.dumps(metadata)+'\n', encoding='utf-8')
        loaded = Image.open(active_path).convert('RGBA')
        assert loaded.size == (32,7680)
        assert loaded.getchannel('A').getextrema() == (255,255)
        assert Image.open(still_path).tobytes() == loaded.crop((0,0,32,32)).tobytes()
        assert json.loads(active_path.with_suffix('.png.mcmeta').read_text()) == metadata
        strips.append(loaded)
        assets.append({'texture': f'gtit:nekotv_full_active_{tile+1}', 'origin': [x,y],
                       'size': list(loaded.size), 'png_sha256': digest(active_path.read_bytes()),
                       'frame_sha256': [digest(loaded.crop((0,n*32,32,(n+1)*32)).tobytes())
                                        for n in range(FRAMES)]})
    for n, frame in enumerate(frames):
        assert frame.crop((64,96,96,128)).getchannel('A').getextrema() == (0,0)
        reassembled = Image.new('RGBA', (WIDTH,HEIGHT))
        for tile, strip in enumerate(strips):
            reassembled.paste(strip.crop((0,n*32,32,(n+1)*32)), TILES[tile])
        assert reassembled.tobytes() == frame.tobytes(), f'Frame {n} has a tile seam'

    casing = Image.open(CASING).convert('RGBA').resize((32,32), Image.Resampling.NEAREST)
    for frame in frames:
        for x in range(WIDTH):
            for y in (0,1,126,127):
                if not (64 <= x < 96 and y >= 96):
                    assert frame.getpixel((x,y)) == casing.getpixel((x%32,y%32)), (x,y)
        for y in range(HEIGHT):
            for x in (0,1,158,159):
                assert frame.getpixel((x,y)) == casing.getpixel((x%32,y%32)), (x,y)
    controller = Image.open(CONTROLLER).convert('RGBA').resize((32,32), Image.Resampling.NEAREST)
    display = []
    for frame in frames:
        image = frame.copy(); image.paste(controller, (64,96)); display.append(image)
    for n, label in ((0,'hidden'),(20,'red-peek-back'),(36,'red-jump-up'),(75,'red-reach-coin'),
                     (121,'black-peek-later'),(117,'red-startled'),(127,'red-jump-back'),
                     (145,'black-watch-exit'),(175,'black-look-outside'),(205,'black-jump-back')):
        display[n].resize((800,640), Image.Resampling.NEAREST).save(PREVIEW / f'key-{label}.png')
    frames[75].resize((800,640), Image.Resampling.NEAREST).save(PREVIEW / 'screen-mask.png')
    display[75].resize((800,640), Image.Resampling.NEAREST).save(PREVIEW / 'screen.png')
    contact = Image.new('RGBA', (WIDTH*4,HEIGHT*3))
    for i,n in enumerate((0,20,36,55,75,100,112,127,145,175,203,230)):
        contact.paste(display[n], (i%4*WIDTH,i//4*HEIGHT))
    contact.resize((1280,768), Image.Resampling.NEAREST).save(PREVIEW / 'contact-sheet.png')
    previews = [frame.resize((480,384), Image.Resampling.NEAREST).convert('RGB') for frame in display]
    previews[0].save(PREVIEW / 'screen.gif', save_all=True, append_images=previews[1:],
                     duration=100, loop=0, optimize=False)
    report = {'composition': 'wood-cabinet-vanilla-model-cats', 'browser': browser_version,
              'scope': 'Offline rendered native resource pixels; not an in-game screenshot or structure test',
              'cat_model': MODEL['provenance'], 'model_parts': 8, 'model_boxes': 11,
              'cat_stages_seconds': STORY, 'cat_story_checks':story_checks,
              'source_coin_sha256': COIN_SHA256, 'coin_alpha_boxes': alpha_boxes,
              'frames': FRAMES, 'fps': FPS, 'frame_time_ticks': FRAME_TIME, 'period_seconds': PERIOD,
              'canvas': [WIDTH,HEIGHT], 'columns': 5, 'rows': 4,
              'tile_order': 'top to bottom, left to right; omit row3 col2 controller',
              'controller_mask': [64,96,32,32], 'controller_mask_empty_all_frames': True,
              'bottom_row_zero_coin_contribution_frames': bottom_coin_checks,
              'solid_counter_front_occludes_cat_bodies_frames': FRAMES,
              'solid_counter_no_dark_opening_frames': FRAMES,
              'rgba': '19 runtime tiles opaque; controller mask transparent',
              'wood_casing_outer_edges_pixel_identical': True,
              'particles': composition['particles'], 'coin_render_size': composition['coinSize'],
              'coin_flow_quality':coin_flow_quality,
              'coin_flow_source_sha256':digest((HERE / 'source/coin_flow.js').read_bytes()),
              'coin_alpha_rendered_boxes': rendered_alpha_boxes,
              'coin_alpha_min_diameter_px': min(min(b[2]-b[0],b[3]-b[1]) for b in rendered_alpha_boxes),
              'reassembled_frames_equal_canvas': FRAMES, 'endpoint_24s_equals_0s': True,
              'duplicate_endpoint_omitted': True, 'browser_errors': errors, 'assets': assets}
    (PREVIEW / 'verification.json').write_text(json.dumps(report,indent=2)+'\n', encoding='utf-8')
    (PREVIEW / 'cat-poses.json').write_text(json.dumps(poses,indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'frames': FRAMES, 'tiles': len(strips), 'period_seconds': PERIOD,
                      'runtime_bytes': sum(p.stat().st_size for p in OUTPUT.glob('nekotv_full*')),
                      'preview': str(PREVIEW), 'verification': 'PASS'}))


if __name__ == '__main__':
    main()
