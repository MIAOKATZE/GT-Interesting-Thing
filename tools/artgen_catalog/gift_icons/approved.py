"""Install exact user-approved R4 pixels from the native sprite master."""
import hashlib
import io
import json

from PIL import Image, ImageDraw


def install(catalog, root, here, assets):
    master = Image.open(here / 'approved_r4.png').convert('RGBA')
    assert master.size == (192, 96) and len(catalog) == 32
    assets.mkdir(parents=True, exist_ok=True)
    preview_dir = root / 'build/ui-v1.8.97/icons'
    preview_dir.mkdir(parents=True, exist_ok=True)
    preview = Image.new('RGB', (8*128, 4*152), '#ded6c5')
    draw = ImageDraw.Draw(preview)
    manifest = []
    for index, (key, english, chinese, _, _) in enumerate(catalog):
        x, y = index % 8 * 24, index // 8 * 24
        icon = master.crop((x, y, x+24, y+24))
        assert icon.size == (24, 24) and icon.getbbox()
        buffer = io.BytesIO()
        icon.save(buffer, format='PNG')
        data = buffer.getvalue()
        destination = assets / (key + '.png')
        destination.write_bytes(data)
        # The approved sprites are static: stale eight-frame declarations are invalid.
        metadata = assets / (key + '.png.mcmeta')
        if metadata.exists():
            metadata.unlink()
        frame = icon.resize((96, 96), Image.Resampling.NEAREST)
        px, py = index % 8 * 128 + 16, index // 8 * 152 + 5
        preview.paste(frame, (px, py), frame)
        draw.text((px, py+105), key, fill='#352f28')
        manifest.append({'key':key, 'label_en':english, 'label_zh':chinese,
                         'width':24, 'frame_height':24, 'frames':1, 'frametime':0,
                         'resource':'gtit:gifts/'+key,
                         'png':str(destination.relative_to(root)).replace('\\','/'),
                         'motion':'static', 'material':'user-approved R4 pixel gift box',
                         'sha256':hashlib.sha256(data).hexdigest()})
    # Retain the old fallback file path with the same approved first-steps art.
    (assets.parent / 'starter_gift.png').write_bytes((assets/'starter.png').read_bytes())
    (here/'manifest.json').write_text(json.dumps({'generator':'approved R4 native 24px master extraction',
        'provenance':'ImageGen concept followed by user-reviewed native pixel outline revisions R1-R4',
        'master':'approved_r4.png', 'outline_width':0.5,
        'outline_colors':['#2c2e30','#434547'], 'icons':manifest}, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    preview.save(preview_dir/'gift-icons-contactsheet.png')
    summary={'icons':len(manifest), 'native_size':[24,24], 'static':32, 'animated':0,
             'rgba':True, 'allDistinct':len({m['sha256'] for m in manifest})==32,
             'source':'approved_r4.png', 'partial_alpha':'half-pixel outline coverage'}
    (preview_dir/'validation.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary))
