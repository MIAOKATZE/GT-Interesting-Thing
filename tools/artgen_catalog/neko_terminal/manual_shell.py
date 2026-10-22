"""Manual palette edit and lossless reuse of existing GTSWN/GTMB pixels.

No image-generation service. The source shell is the first 16x16 frame of
GTSWN Device_Info_Terminal.png supplies the shell; GTMB supplies the cat cutout.
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
                color = ('#f4ebcf' if light >= 185 else '#c8bfa7' if light >= 145
                         else '#92958d' if light >= 100 else '#555f65' if light >= 65
                         else '#303b42')
            else:
                color = '#d7bd70'
            pixels[x, y] = (*tuple(bytes.fromhex(color[1:])), 255)

    shell = shell.resize((64, 64), Image.Resampling.NEAREST)
    # Ivory casing, cool slate screen/bevel, restrained champagne status light.
    draw = ImageDraw.Draw(shell)
    draw.rectangle((12, 20, 43, 51), fill='#26353d')
    draw.rectangle((44, 24, 51, 31), fill='#eadca8')
    draw.rectangle((44, 32, 51, 39), fill='#b6a772')
    draw.rectangle((44, 40, 51, 47), fill='#777f79')

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


if __name__ == '__main__':
    make_terminal().save(OUTPUT)
    print(OUTPUT)
