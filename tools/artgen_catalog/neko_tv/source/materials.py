"""Deterministic native-resolution wood/brass casing, shared by block and front art."""
from PIL import Image, ImageDraw


def casing(face):
    image = Image.new('RGBA', (16, 16))
    px = image.load()
    for y in range(16):
        for x in range(16):
            # Small stepped grain and plank joints, deliberately no smooth gradients.
            grain = ((x * 13 + y * 3 + (y // 3) * 7) % 7) - 3
            base = (104, 65, 35) if face == 'bottom' else (133, 88, 46)
            if face == 'top':
                grain = ((y * 11 + x // 3) % 7) - 3
                base = (151, 105, 57)
            px[x, y] = (*[c + grain * 2 for c in base], 255)
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 15, 15), outline='#49311f')
    d.line((1, 1, 14, 1), fill='#d3ad62')
    d.line((1, 2, 14, 2), fill='#a67b3a')
    d.line((1, 14, 14, 14), fill='#5a3b22')
    if face == 'side':
        for x in (1, 14):
            d.line((x, 2, x, 13), fill='#bd914d')
        for y in (6, 11):
            d.line((3, y, 12, y), fill='#79502e')
            d.line((4, y + 1, 10, y + 1), fill='#a06d38')
        d.point((1, 1), fill='#f0d596')
        d.point((14, 1), fill='#f0d596')
        d.point((1, 14), fill='#c59a52')
        d.point((14, 14), fill='#c59a52')
    elif face == 'top':
        for x in (5, 10):
            d.line((x, 3, x, 13), fill='#76502d')
            d.line((x + 1, 3, x + 1, 12), fill='#b08046')
        d.line((1, 14, 14, 14), fill='#c69b52')
    else:
        d.rectangle((3, 3, 12, 12), outline='#684528')
    return image


def write_materials(output):
    for face in ('side', 'top', 'bottom'):
        casing(face).save(output / f'neko_vending_casing_{face}.png', optimize=True)
    for state in ('off', 'on'):
        image = Image.open(output / f'nekovm_front_{state}.png').convert('RGBA')
        source, wood = image.copy(), casing('side')
        for y in range(16):
            for x in range(16):
                r,g,b,a = source.getpixel((x,y))
                if x in (0,15) or y in (0,15):
                    image.putpixel((x,y), wood.getpixel((x,y)))
                elif min(r,g,b) >= 95 and max(r,g,b)-min(r,g,b) < 30:
                    # Replace the old silver substrate, preserve the electronic panel pixels.
                    c = wood.getpixel((x,y))
                    image.putpixel((x,y), (*[min(255, int(v*1.08)) for v in c[:3]], 255))
        image.save(output / f'neko_vending_controller_{state}.png', optimize=True)
    # Original emissive indicator frames have no metal substrate; keep their pixels/animation.
    glow = Image.open(output / 'nekovm_front_on_glow.png')
    glow.save(output / 'neko_vending_controller_glow.png', optimize=True)
    old_meta = output / 'nekovm_front_on_glow.png.mcmeta'
    (output / 'neko_vending_controller_glow.png.mcmeta').write_bytes(old_meta.read_bytes())
