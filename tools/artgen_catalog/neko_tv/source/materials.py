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


def connected_casing(face, mask):
    """Four-neighbour CTM: top/right/bottom/left bits remove only shared frame edges.

    Mask zero remains the original block/item/front-art asset, byte-for-byte unchanged.
    Connected edges use periodic wood grain; surviving outer edges run through the join.
    """
    if mask == 0:
        return casing(face)
    image = casing(face)
    px = image.load()
    for y in range(16):
        for x in range(16):
            if 2 <= x <= 13 and 3 <= y <= 12:
                continue
            gx, gy = x % 15, y % 15
            grain = ((gx * 13 + gy * 3 + (gy // 3) * 7) % 7) - 3
            base = (104, 65, 35) if face == 'bottom' else (133, 88, 46)
            if face == 'top':
                grain = ((gy * 11 + gx // 3) % 7) - 3
                base = (151, 105, 57)
            color = tuple(c + grain * 2 for c in base) + (255,)
            if ((y == 0 and not mask & 1) or (x == 15 and not mask & 2)
                    or (y == 15 and not mask & 4) or (x == 0 and not mask & 8)):
                color = (73, 49, 31, 255)
            elif y == 1 and not mask & 1:
                color = (211, 173, 98, 255)
            elif y == 2 and not mask & 1:
                color = (166, 123, 58, 255)
            elif y == 14 and not mask & 4:
                color = (198, 155, 82, 255) if face == 'top' else (90, 59, 34, 255)
            elif (x == 1 and not mask & 8) or (x == 14 and not mask & 2):
                color = (189, 145, 77, 255)
            px[x, y] = color
    return image


def write_connected_materials(output):
    """Only writes the 45 CTM variants; never rewrites base, controller or television art."""
    for face in ('side', 'top', 'bottom'):
        for mask in range(1, 16):
            connected_casing(face, mask).save(output / f'neko_vending_casing_{face}_{mask}.png', optimize=True)
