"""Check packaged CTM assets and every internal join on representative side/top/bottom surfaces."""
from pathlib import Path
from PIL import Image
from materials import casing, connected_casing


ROOT = Path(__file__).resolve().parents[4]
ASSETS = ROOT / 'src/main/resources/assets/gtit/textures/blocks'
MASKS = ((6, 14, 12), (7, 15, 13), (3, 11, 9))


def main():
    joins = 0
    preview = Image.new('RGBA', (144, 48))
    for face_index, face in enumerate(('side', 'top', 'bottom')):
        base = Image.open(ASSETS / f'neko_vending_casing_{face}.png').convert('RGBA')
        assert base.tobytes() == casing(face).tobytes(), f'{face}: original mask-zero base changed'
        for mask in range(1, 16):
            image = Image.open(ASSETS / f'neko_vending_casing_{face}_{mask}.png').convert('RGBA')
            assert image.size == (16, 16) and image.getextrema()[3] == (255, 255), (face, mask)
            assert image.tobytes() == connected_casing(face, mask).tobytes(), f'{face}/{mask}: stale asset'
        tiles = [[Image.open(ASSETS / f'neko_vending_casing_{face}_{mask}.png').convert('RGBA')
                  for mask in row] for row in MASKS]
        for row in range(3):
            for col in range(3):
                preview.paste(tiles[row][col], (face_index * 48 + col * 16, row * 16))
                if col < 2:
                    assert [tiles[row][col].getpixel((15, y)) for y in range(16)] == [
                        tiles[row][col + 1].getpixel((0, y)) for y in range(16)], (face, row, col, 'horizontal')
                    joins += 1
                if row < 2:
                    assert [tiles[row][col].getpixel((x, 15)) for x in range(16)] == [
                        tiles[row + 1][col].getpixel((x, 0)) for x in range(16)], (face, row, col, 'vertical')
                    joins += 1
    (ROOT / 'build').mkdir(exist_ok=True)
    preview.resize((576, 192), Image.Resampling.NEAREST).save(ROOT / 'build/neko-casing-ctm-preview.png')
    print(f'CTM PASS: 45 opaque 16px variants, unchanged base images, {joins} exact internal joins')


if __name__ == '__main__':
    main()
