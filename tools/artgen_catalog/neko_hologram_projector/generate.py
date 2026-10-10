"""Draw the original 32px Neko Hologram Projector icon. Requires Pillow.

Run from any working directory; only this item's PNG is generated.
"""

from pathlib import Path

from PIL import Image, ImageDraw


def generate() -> Image.Image:
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    outline = "#342C48"
    metal = "#726C95"
    highlight = "#CEC6E6"
    cyan = "#8AF8F0"

    # Transparent light cone beneath a floating cat-shaped hologram.
    draw.polygon([(13, 23), (8, 11), (24, 11), (19, 23)], fill=(72, 232, 229, 45))
    draw.line([(11, 13), (14, 22)], fill=(91, 232, 241, 130))
    draw.line([(22, 13), (18, 22)], fill=(91, 232, 241, 130))
    # Cat ears, head and scan lines; pixels remain crisp at native resolution.
    cat = [(7, 5), (7, 1), (12, 4), (19, 4), (24, 1), (24, 9),
           (22, 13), (10, 13), (7, 10)]
    draw.polygon(cat, fill=(47, 181, 191, 210))
    draw.line(cat + [cat[0]], fill=cyan, width=1)
    draw.line([(9, 4), (10, 5)], fill="#D5FFFB")
    draw.line([(21, 5), (22, 4)], fill="#D5FFFB")
    draw.line([(8, 7), (23, 7)], fill=(112, 245, 235, 140))
    draw.line([(9, 11), (22, 11)], fill=(112, 245, 235, 120))
    draw.rectangle((11, 8, 12, 9), fill="#E0FFFD")
    draw.rectangle((19, 8, 20, 9), fill="#E0FFFD")
    draw.point((16, 10), fill="#FFF1CD")
    draw.line([(14, 11), (16, 12), (18, 11)], fill=cyan)
    draw.point((4, 11), fill="#8AF8F0")
    draw.point((27, 6), fill="#C7FFF5")

    # A handheld copper-trimmed projector body with a cyan emission aperture.
    draw.polygon([(7, 22), (12, 19), (22, 19), (26, 23), (26, 28),
                  (22, 31), (8, 31), (5, 28), (5, 24)], fill=outline)
    draw.polygon([(7, 24), (12, 21), (22, 21), (24, 24), (21, 27),
                  (9, 27)], fill=metal)
    draw.line([(8, 24), (12, 22), (21, 22)], fill=highlight)
    draw.polygon([(7, 26), (10, 28), (22, 28), (24, 26), (24, 28),
                  (21, 29), (9, 29), (7, 28)], fill="#4C476A")
    draw.line([(8, 30), (22, 30)], fill="#BD8B72")
    draw.rectangle((12, 23, 20, 24), fill="#216A7E")
    draw.line([(13, 22), (19, 22)], fill="#B9FFF7")
    draw.line([(13, 23), (19, 23)], fill=cyan)
    draw.point((23, 25), fill="#FFD9A0")
    draw.line([(10, 28), (12, 28)], fill=highlight)
    draw.line([(17, 28), (19, 28)], fill=cyan)
    return image


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[3]
    target = root / "src/main/resources/assets/gtit/textures/items/neko_hologram_projector.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    generate().save(target, format="PNG", optimize=False)
    print(f"Generated {target} (32x32 RGBA)")
