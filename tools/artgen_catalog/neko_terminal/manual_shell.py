"""Hand-edited pixels: GTSWN's compact casing, warm gold palette, GTIT cat mark.

No image-generation service. The source shell is the first 16x16 frame of
GTSWN Device_Info_Terminal.png; only its opaque pixels are recolored.
"""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUTPUT = ROOT / 'src/main/resources/assets/gtit/textures/items/neko_vending_terminal.png'


def make_terminal():
    shell = Image.open(HERE / 'gtswn_device_shell_reference.png').convert('RGBA')
    pixels = shell.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = pixels[x, y]
            if not a:
                continue
            if max(r, g, b) - min(r, g, b) < 16:
                light = (r + g + b) // 3
                color = ('#fff0ac' if light >= 185 else '#dcb767' if light >= 145
                         else '#a48243' if light >= 100 else '#735d36' if light >= 65
                         else '#3f3526')
            else:
                color = '#f7d064'
            pixels[x, y] = (*tuple(bytes.fromhex(color[1:])), 255)

    # Original screen and status bar stay in their original locations.
    draw = ImageDraw.Draw(shell)
    draw.rectangle((3, 5, 9, 12), fill='#343326')
    draw.rectangle((11, 6, 12, 8), fill='#ffe596')
    draw.rectangle((11, 9, 12, 10), fill='#e8bd55')
    draw.rectangle((11, 11, 12, 11), fill='#ac8134')
    shell = shell.resize((32, 32), Image.Resampling.NEAREST)

    # Hand-placed 14x14 cat pixels inside the original seven-pixel screen.
    cat = Image.new('RGBA', (14, 14))
    face = ImageDraw.Draw(cat)
    face.polygon([(0, 0), (5, 4), (8, 4), (13, 0), (13, 11),
                  (11, 13), (2, 13), (0, 11)], fill='#fff1b2')
    face.polygon([(1, 2), (1, 4), (4, 4)], fill='#dba351')
    face.polygon([(12, 2), (12, 4), (9, 4)], fill='#dba351')
    face.rectangle((3, 6, 4, 7), fill='#594224')
    face.rectangle((9, 6, 10, 7), fill='#594224')
    face.rectangle((6, 8, 7, 9), fill='#594224')
    face.line([(3, 10), (4, 11), (5, 11), (6, 10),
               (7, 10), (8, 11), (9, 11), (10, 10)], fill='#594224')
    shell.alpha_composite(cat, (6, 11))
    result = shell.resize((64, 64), Image.Resampling.NEAREST)
    reference = Image.open(HERE / 'gtswn_device_shell_reference.png').convert('RGBA')
    assert result.getchannel('A').tobytes() == reference.resize((64, 64), Image.Resampling.NEAREST).getchannel('A').tobytes()
    return result


if __name__ == '__main__':
    make_terminal().save(OUTPUT)
    print(OUTPUT)
