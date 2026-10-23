"""Preserve the original shell/cat with a beige screen and coarse warm sheen."""
from pathlib import Path
import hashlib
import json
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUTPUT = ROOT / 'src/main/resources/assets/gtit/textures/items/neko_vending_terminal.png'
PREVIEW = ROOT / 'build/terminal-preview'
FRAME_COUNT = 16
FRAME_TIME = 3
FRAME_SIZE = 64
PIXEL_UNIT = 4
SCREEN = (12, 20, 43, 51)
STATUS = (44, 24, 51, 47)
CAT_POSITION = (12, 20)
SCREEN_PALETTE = ((186, 173, 142), (191, 179, 150), (197, 186, 159), (202, 192, 168))
STATUS_BASE = ((126, 131, 119), (111, 122, 120), (95, 111, 115))
STATUS_FLOW = ((247, 226, 161), (201, 192, 147), (158, 163, 139))
SOURCE_SHA256 = {
    'gtswn_device_shell_reference.png': 'a3905dab76175578a83fbec3e1670154988a86403044b764f872d84c6e86fe09',
    'gtmb_cat_head_reference.png': 'd4d9379aeee34ea2834a8acc9c22e823209067a8b575227ec7ee41aeb33d2ae3',
}
SHEEN_COLOR = (255, 244, 202)
SHEEN_STRENGTHS = (0.38, 0.15)
PREVIEW_FRAME = 7


def source_cat():
    cat = Image.open(HERE / 'gtmb_cat_head_reference.png').convert('RGBA')
    assert cat.size == (16, 16)
    return cat.resize((32, 32), Image.Resampling.NEAREST)


def static_shell():
    shell = Image.open(HERE / 'gtswn_device_shell_reference.png').convert('RGBA')
    assert shell.size == (16, 16)
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
    return shell.resize((FRAME_SIZE, FRAME_SIZE), Image.Resampling.NEAREST)


def block(draw, x, y, color):
    draw.rectangle((x, y, x + PIXEL_UNIT - 1, y + PIXEL_UNIT - 1), fill=(*color, 255))


def animate_screen(draw, frame):
    phase = frame // 2
    scan_row = (-phase) % 8
    for row in range(8):
        distance = (row - scan_row) % 8
        color = SCREEN_PALETTE[2 if distance == 0 else 1 if distance == 1 else 0]
        for col in range(8):
            block(draw, 12 + col * PIXEL_UNIT, 20 + row * PIXEL_UNIT, color)
    for index, (x, y) in enumerate(((12, 20), (36, 20), (40, 24), (12, 44), (36, 48), (24, 48))):
        brightness = (phase + index) % 8
        level = 3 if brightness == 0 else 2 if brightness in (1, 7) else 1
        block(draw, x, y, SCREEN_PALETTE[level])


def animate_status(draw, frame):
    head = (5 - frame * 6 // FRAME_COUNT) % 6
    for row in range(6):
        distance = (row - head) % 6
        color = STATUS_FLOW[distance] if distance < 3 else STATUS_BASE[row // 2]
        for col in range(2):
            block(draw, 44 + col * PIXEL_UNIT, 24 + row * PIXEL_UNIT, color)


def animated_shell(frame):
    shell = static_shell()
    draw = ImageDraw.Draw(shell)
    animate_screen(draw, frame)
    animate_status(draw, frame)
    return shell


def sheen_cell_strength(frame, col):
    # Rest at both ends of the loop; the 4px column advances only left to right.
    if not 3 <= frame <= 12:
        return 0
    distance = abs(col - (frame - 4))
    return SHEEN_STRENGTHS[distance] if distance < len(SHEEN_STRENGTHS) else 0


def sheen_cat(frame):
    cat = source_cat()
    pixels = cat.load()
    for y in range(32):
        for x in range(32):
            r, g, b, a = pixels[x, y]
            strength = sheen_cell_strength(frame, x // PIXEL_UNIT)
            # Preserve the dark facial features and outline exactly.
            if not a or r < 200 or not strength:
                continue
            rgb = tuple(round(value + (target - value) * strength)
                        for value, target in zip((r, g, b), SHEEN_COLOR))
            pixels[x, y] = (*rgb, a)
    return cat


def make_terminal(frame=0):
    shell = animated_shell(frame)
    shell.alpha_composite(sheen_cat(frame), CAT_POSITION)
    return shell


def backdrop(frame):
    canvas = Image.new('RGB', frame.size, '#e4e2dc')
    draw = ImageDraw.Draw(canvas)
    for y in range(0, FRAME_SIZE, 8):
        for x in range(0, FRAME_SIZE, 8):
            if (x // 8 + y // 8) % 2:
                draw.rectangle((x, y, x + 7, y + 7), fill='#d8d7d1')
    canvas.paste(frame, (0, 0), frame)
    return canvas


def write_preview(frames):
    previews = [backdrop(frame).resize((384, 384), Image.Resampling.NEAREST) for frame in frames]
    # Independent local palettes include each frame's sheen colors.
    gif_frames = [frame.quantize(colors=256, dither=Image.Dither.NONE) for frame in previews]
    gif_frames[0].save(PREVIEW / 'preview.gif', save_all=True, append_images=gif_frames[1:],
                       duration=FRAME_TIME * 50, loop=0, optimize=False, disposal=2)
    previews[PREVIEW_FRAME].save(PREVIEW / 'frame.png')


def in_animation(x, y):
    return (12 <= x <= 43 and 20 <= y <= 51) or (44 <= x <= 51 and 24 <= y <= 47)


def verify(frames):
    source_hashes = {name: hashlib.sha256((HERE / name).read_bytes()).hexdigest()
                     for name in SOURCE_SHA256}
    assert source_hashes == SOURCE_SHA256
    source = Image.open(HERE / 'gtswn_device_shell_reference.png').convert('RGBA')
    alpha = source.resize((64, 64), Image.Resampling.NEAREST).getchannel('A').tobytes()
    cat = source_cat()
    opaque_cat = [(x + 12, y + 20, cat.getpixel((x, y)))
                  for y in range(32) for x in range(32) if cat.getpixel((x, y))[3] == 255]
    assert len(opaque_cat) == 568
    static_pixels = [(x, y) for y in range(64) for x in range(64) if not in_animation(x, y)]
    differences = []
    sheen_pixels = []
    sheen_centers = []
    maximum_channel_lift = 0
    for index, frame in enumerate(frames):
        assert frame.size == (64, 64)
        assert frame.getchannel('A').tobytes() == alpha
        assert all(frame.getpixel((x, y)) == frames[0].getpixel((x, y)) for x, y in static_pixels)
        underlay = animated_shell(index)
        lit_cat = sheen_cat(index)
        assert lit_cat.getchannel('A').tobytes() == cat.getchannel('A').tobytes()
        lit_positions = []
        for y in range(32):
            for x in range(32):
                original = cat.getpixel((x, y))
                lit = lit_cat.getpixel((x, y))
                strength = sheen_cell_strength(index, x // PIXEL_UNIT)
                if not original[3] or original[0] < 200 or not strength:
                    assert lit == original
                if lit != original:
                    assert original[3] == 255 and original[0] >= 200 and strength > 0
                    lifts = [after - before for before, after in zip(original[:3], lit[:3])]
                    assert all(0 <= lift <= 75 for lift in lifts)
                    maximum_channel_lift = max(maximum_channel_lift, *lifts)
                    lit_positions.append(x + CAT_POSITION[0])
                actual = frame.getpixel((x + CAT_POSITION[0], y + CAT_POSITION[1]))
                expected = lit if original[3] else underlay.getpixel((x + CAT_POSITION[0], y + CAT_POSITION[1]))
                assert actual == expected
        sheen_pixels.append(len(lit_positions))
        if lit_positions:
            sheen_centers.append(round(sum(lit_positions) / len(lit_positions), 3))
        for left, top, right, bottom in (SCREEN, STATUS):
            for y in range(top, bottom + 1, PIXEL_UNIT):
                for x in range(left, right + 1, PIXEL_UNIT):
                    assert underlay.crop((x, y, x + PIXEL_UNIT, y + PIXEL_UNIT)).getcolors(maxcolors=1) is not None
        following = frames[(index + 1) % len(frames)]
        changed = [(x, y) for y in range(64) for x in range(64)
                   if frame.getpixel((x, y)) != following.getpixel((x, y))]
        assert all(in_animation(x, y) for x, y in changed)
        differences.append(len(changed))
    assert all(left < right for left, right in zip(sheen_centers, sheen_centers[1:]))
    assert sheen_pixels[:3] == [0, 0, 0] and sheen_pixels[-3:] == [0, 0, 0]
    assert sheen_cat(0).tobytes() == cat.tobytes() == sheen_cat(FRAME_COUNT - 1).tobytes()
    return {'frame_size': [64, 64], 'sheet_size': [64, 64 * FRAME_COUNT],
            'frames': FRAME_COUNT, 'frametime_ticks': FRAME_TIME,
            'loop_seconds': FRAME_COUNT * FRAME_TIME / 20,
            'background_and_status_cell_size': [PIXEL_UNIT, PIXEL_UNIT],
            'all_animated_cells_solid_before_cat_overlay': True,
            'background_palette_size': len(SCREEN_PALETTE),
            'status_palette_size': len(STATUS_BASE) + len(STATUS_FLOW),
            'cat_source_size': [16, 16], 'cat_size': [32, 32], 'cat_position': list(CAT_POSITION),
            'cat_scaling': 'nearest 2x', 'cat_base_original_colors': True,
            'cat_opaque_shape_pixels_every_frame': len(opaque_cat),
            'cat_highlight_only_inside_alpha': True,
            'cat_dark_features_original_every_frame': True,
            'cat_unlit_pixels_original_every_frame': True,
            'cat_sheen_cell_size': [PIXEL_UNIT, PIXEL_UNIT],
            'cat_sheen_color': list(SHEEN_COLOR),
            'cat_sheen_strengths': list(SHEEN_STRENGTHS),
            'cat_sheen_max_channel_lift': maximum_channel_lift,
            'cat_sheen_pixels_by_frame': sheen_pixels,
            'cat_sheen_centers_left_to_right': sheen_centers,
            'cat_loop_first_last_original': True,
            'representative_preview_frame': PREVIEW_FRAME,
            'static_shell_pixels_identical_every_frame': len(static_pixels),
            'alpha_matches_original_shell_nearest_4x_every_frame': True,
            'changes_confined_to_screen_and_status': True,
            'adjacent_changed_pixels_including_wrap': differences,
            'screen_region': list(SCREEN), 'status_region': list(STATUS),
            'source_sha256': source_hashes,
            'source_sha256_unchanged': True}


if __name__ == '__main__':
    PREVIEW.mkdir(parents=True, exist_ok=True)
    frames = [make_terminal(index) for index in range(FRAME_COUNT)]
    report = verify(frames)
    sheet = Image.new('RGBA', (64, 64 * FRAME_COUNT))
    for index, frame in enumerate(frames):
        sheet.paste(frame, (0, index * 64))
    sheet.save(OUTPUT)
    report['png_sha256'] = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    metadata = {'animation': {'frametime': FRAME_TIME}}
    OUTPUT.with_name(OUTPUT.name + '.mcmeta').write_text(json.dumps(metadata) + '\n', encoding='utf-8', newline='\n')
    saved = Image.open(OUTPUT)
    assert saved.size == (64, 1024)
    assert json.loads(OUTPUT.with_name(OUTPUT.name + '.mcmeta').read_text(encoding='utf-8')) == metadata
    write_preview(frames)
    with Image.open(PREVIEW / 'preview.gif') as preview:
        durations = []
        elapsed = 0
        for index in range(preview.n_frames):
            preview.seek(index)
            durations.append(preview.info['duration'])
            expected_frame = frames[elapsed // (FRAME_TIME * 50)]
            expected = backdrop(expected_frame).resize((384, 384), Image.Resampling.NEAREST)
            assert preview.convert('RGB').tobytes() == expected.tobytes()
            elapsed += preview.info['duration']
        assert sum(durations) == FRAME_COUNT * FRAME_TIME * 50
    report['gif_frame_durations_ms'] = durations
    report['metadata_verified'] = True
    report['gif_all_frames_exact_rgb_including_sheen'] = True
    (PREVIEW / 'verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(OUTPUT)
    print(PREVIEW / 'preview.gif')
    print(json.dumps(report))
