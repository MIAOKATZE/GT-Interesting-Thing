"""Manual pixel animation and lossless reuse of existing GTSWN/GTMB pixels.

No image-generation service. GTSWN Device_Info_Terminal.png supplies the
first-frame shell; GTMB supplies the unchanged transparent cat cutout.
"""
from pathlib import Path
import hashlib
import json
import math
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUTPUT = ROOT / 'src/main/resources/assets/gtit/textures/items/neko_vending_terminal.png'
PREVIEW = ROOT / 'build/v207-animation'
FRAME_COUNT = 16
FRAME_TIME = 3
SCREEN = (12, 20, 43, 51)
STATUS = (44, 24, 51, 47)


def mix(start, end, amount):
    return tuple(round(a + (b - a) * amount) for a, b in zip(start, end))


def animate_screen(draw, frame):
    """Slate grid, sparse pixels and a slow scan, all behind the unchanged cat."""
    phase = frame / FRAME_COUNT
    for y in range(SCREEN[1], SCREEN[3] + 1):
        distance = ((y - SCREEN[1]) / 32 + phase) % 1
        scan = max(0, 1 - min(distance, 1 - distance) / 0.16)
        for x in range(SCREEN[0], SCREEN[2] + 1):
            grid = (x - SCREEN[0]) % 8 == 0 or (y - SCREEN[1]) % 8 == 0
            base = (43, 61, 69) if grid else (33, 48, 57)
            draw.point((x, y), fill=(*mix(base, (74, 101, 108), scan * 0.32), 255))
    # Dots remain in the exposed screen margin; sinusoidal brightness wraps without a flash.
    for index, (x, y) in enumerate(((15, 23), (37, 22), (41, 27), (14, 46), (36, 49), (24, 50))):
        glow = (1 + math.sin(math.tau * (phase + index / 6))) / 2
        draw.rectangle((x, y, x + 1, y + 1), fill=(*mix((61, 80, 85), (128, 151, 150), glow * 0.6), 255))


def animate_status(draw, frame):
    """A champagne charge flows upward across the existing three status segments."""
    head = (23 - frame * 24 / FRAME_COUNT) % 24
    for y in range(24, 48):
        offset = (y - 24 - head) % 24
        strength = max(0, 1 - min(offset, 24 - offset) / 5)
        segment = (y - 24) // 8
        base = ((126, 131, 119), (111, 122, 120), (95, 111, 115))[segment]
        color = mix(base, (247, 226, 161), strength)
        draw.line((44, y, 51, y), fill=(*color, 255))
        # Preserve the three-cell read with a quiet dark lower edge, never a blinking gap.
        if y in (31, 39):
            draw.line((44, y, 51, y), fill=(*mix(color, (75, 90, 93), 0.3), 255))


def make_terminal(frame=0):
    shell = Image.open(HERE / 'gtswn_device_shell_reference.png').convert('RGBA')
    pixels = shell.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = pixels[x, y]
            if not a:
                continue
            if max(r, g, b) - min(r, g, b) < 16:
                light = (r + g + b) // 3
                color = ('#f4ebcf' if light >= 185 else '#c8bfa7' if light >= 145
                         else '#92958d' if light >= 100 else '#555f65' if light >= 65
                         else '#303b42')
            else:
                color = '#d7bd70'
            pixels[x, y] = (*tuple(bytes.fromhex(color[1:])), 255)

    shell = shell.resize((64, 64), Image.Resampling.NEAREST)
    # Ivory casing, cool slate screen/bevel, restrained champagne status light.
    draw = ImageDraw.Draw(shell)
    animate_screen(draw, frame)
    animate_status(draw, frame)

    # Exact existing GTMB frame 0. No redrawn facial features or recoloring.
    cat = Image.open(HERE / 'gtmb_cat_head_reference.png').convert('RGBA')
    assert cat.size == (16, 16)
    scaled_cat = cat.resize((32, 32), Image.Resampling.NEAREST)
    shell.alpha_composite(scaled_cat, (12, 20))
    result = shell
    for y in range(32):
        for x in range(32):
            if scaled_cat.getpixel((x, y))[3] == 255:
                assert result.getpixel((x + 12, y + 20)) == scaled_cat.getpixel((x, y))
    reference = Image.open(HERE / 'gtswn_device_shell_reference.png').convert('RGBA')
    assert result.getchannel('A').tobytes() == reference.resize((64, 64), Image.Resampling.NEAREST).getchannel('A').tobytes()
    return result


def write_preview(frames):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    previews = []
    for frame in frames:
        canvas = Image.new('RGB', (64, 64), '#e4e2dc')
        draw = ImageDraw.Draw(canvas)
        for y in range(0, 64, 8):
            for x in range(0, 64, 8):
                if (x // 8 + y // 8) % 2:
                    draw.rectangle((x, y, x + 7, y + 7), fill='#d8d7d1')
        canvas.paste(frame, (0, 0), frame)
        previews.append(canvas.resize((256, 256), Image.Resampling.NEAREST))
    contact = Image.new('RGB', (1024, 1024), '#e4e2dc')
    for index, frame in enumerate(previews):
        contact.paste(frame, ((index % 4) * 256, (index // 4) * 256))
    contact.save(PREVIEW / 'contactsheet.png')
    palette = contact.quantize(colors=256)
    gif_frames = [frame.quantize(palette=palette, dither=Image.Dither.NONE) for frame in previews]
    gif_frames[0].save(PREVIEW / 'preview.gif', save_all=True, append_images=gif_frames[1:],
                       duration=FRAME_TIME * 50, loop=0, optimize=False, disposal=2)
    previews[0].save(PREVIEW / 'frame-00.png')


def verify(frames):
    source = Image.open(HERE / 'gtswn_device_shell_reference.png').convert('RGBA')
    alpha = source.resize((64, 64), Image.Resampling.NEAREST).getchannel('A').tobytes()
    cat = Image.open(HERE / 'gtmb_cat_head_reference.png').convert('RGBA').resize((32, 32), Image.Resampling.NEAREST)
    opaque_cat = [(x + 12, y + 20, cat.getpixel((x, y)))
                  for y in range(32) for x in range(32) if cat.getpixel((x, y))[3] == 255]
    differences = []
    for index, frame in enumerate(frames):
        assert frame.size == (64, 64)
        assert frame.getchannel('A').tobytes() == alpha
        assert all(frame.getpixel((x, y)) == color for x, y, color in opaque_cat)
        following = frames[(index + 1) % len(frames)]
        changed = [(x, y) for y in range(64) for x in range(64)
                   if frame.getpixel((x, y)) != following.getpixel((x, y))]
        assert all((12 <= x <= 43 and 20 <= y <= 51) or
                   (44 <= x <= 51 and 24 <= y <= 47) for x, y in changed)
        differences.append(len(changed))
    return {'frame_size': [64, 64], 'sheet_size': [64, 64 * FRAME_COUNT],
            'frames': FRAME_COUNT, 'frametime_ticks': FRAME_TIME, 'loop_seconds': FRAME_COUNT * FRAME_TIME / 20,
            'alpha_matches_source_every_frame': True, 'opaque_cat_pixels_preserved_every_frame': len(opaque_cat),
            'changes_confined_to_screen_and_status': True, 'adjacent_changed_pixels_including_wrap': differences,
            'source_sha256': {name: hashlib.sha256((HERE / name).read_bytes()).hexdigest()
                              for name in ('gtswn_device_shell_reference.png', 'gtmb_cat_head_reference.png')}}


if __name__ == '__main__':
    frames = [make_terminal(index) for index in range(FRAME_COUNT)]
    report = verify(frames)
    sheet = Image.new('RGBA', (64, 64 * FRAME_COUNT))
    for index, frame in enumerate(frames):
        sheet.paste(frame, (0, index * 64))
    sheet.save(OUTPUT)
    metadata = {'animation': {'frametime': FRAME_TIME}}
    OUTPUT.with_name(OUTPUT.name + '.mcmeta').write_text(json.dumps(metadata) + '\n', encoding='utf-8', newline='\n')
    write_preview(frames)
    report['png_sha256'] = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    (PREVIEW / 'verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(OUTPUT)
    print(PREVIEW / 'preview.gif')
