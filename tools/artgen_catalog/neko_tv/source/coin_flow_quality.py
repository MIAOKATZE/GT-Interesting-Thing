"""Compare the fixed coin trajectories with the v2.0.16 three-lane layout, offline only.

Run from any directory. Outputs are a compact JSON receipt and a coin-only comparison sheet;
the latter is a resource preview, not a Minecraft screenshot or the final cat composition.
"""
import itertools
import json
import math
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OUT = ROOT / 'build/neko-tv-preview'
FRAMES, FPS, PERIOD, SIZE = 240, 10, 24, 49
PAIRS = np.array(list(itertools.combinations(range(8), 2)))
TRIPLES = np.array(list(itertools.combinations(range(8), 3)))


def old_layout():
    seed = 0x4e454b4f

    def random():
        nonlocal seed
        seed = (seed * 1664525 + 1013904223) & 0xffffffff
        return seed / 4294967296

    coins = []
    for n in range(8):
        coins.append(dict(phase=n / 8 + random() * .025, drift=random() * math.tau,
                          turn=int(random() * 4), laneX=[30, 80, 130][n % 3],
                          cycles=2 + n % 3, travel=156 + n * 4, amplitude=2))
    frames = []
    for frame in range(FRAMES):
        time = frame / FPS
        poses = []
        for coin in coins:
            phase = (coin['phase'] + time / PERIOD * coin['cycles']) % 1
            poses.append(dict(x=coin['laneX'] + math.sin(phase * math.tau + coin['drift']) * 2,
                              y=-24 + phase * coin['travel'], phase=phase,
                              coinFrame=(math.floor(time / PERIOD * 48) + coin['turn']) % 4))
        frames.append(poses)
    return frames


def measure(frames):
    x = np.array([[pose['x'] for pose in frame] for frame in frames])
    y = np.array([[pose['y'] for pose in frame] for frame in frames])
    visible = (y >= 8) & (y <= 96)
    a, b = PAIRS.T
    dy, dx = np.abs(y[:, a] - y[:, b]), np.abs(x[:, a] - x[:, b])
    both = visible[:, a] & visible[:, b]
    rows = both & (dy <= 6) & (dx >= 30)
    near_height = both & (dy <= 6)
    overlap = both & (dx * dx + dy * dy < 30 * 30)
    xs = np.sort(x[:, TRIPLES], axis=2)
    triples = np.all(visible[:, TRIPLES], axis=2) & (np.ptp(y[:, TRIPLES], axis=2) <= 9)
    triples &= np.min(np.diff(xs, axis=2), axis=2) >= 24
    sustained = rows & np.roll(rows, 1, axis=0) & np.roll(rows, 2, axis=0)
    counts = visible.sum(axis=1)
    return dict(pair_frames=int(rows.any(axis=1).sum()), pair_samples=int(rows.sum()),
                triple_frames=int(triples.any(axis=1).sum()), triple_samples=int(triples.sum()),
                sustained_pair_samples=int(sustained.sum()),
                all_near_height_pair_samples=int(near_height.sum()),
                close_overlap_pair_samples=int(overlap.sum()),
                visible_min=int(counts.min()), visible_mean=float(counts.mean()), visible_max=int(counts.max())), rows


def main():
    # Evaluate the actual shared JS API rather than maintaining a second implementation of it.
    script = """
require(process.argv[1]);
const flow = globalThis.NekoCoinFlow;
const frames = Array.from({length:240},(_,n)=>flow.particles.map(c=>flow.at(c,n/10)));
for (const c of flow.particles) for (let n=0;n<240;n++) {
    const a=flow.at(c,n/10), b=flow.at(c,24+n/10);
    for (const key of ['x','y','phase']) if (Math.abs(a[key]-b[key])>1e-10) throw Error('loop '+key);
    if (a.coinFrame!==b.coinFrame) throw Error('turn loop');
    const next=flow.at(c,n/10+.001);
    if (next.phase>=a.phase && Math.abs(next.y-a.y-c.speed*.001)>1e-9) throw Error('downward speed');
    if (next.phase<a.phase && (a.y-24.5<96 || next.y+24.5>8)) throw Error('visible wrap');
}
process.stdout.write(JSON.stringify({particles:flow.particles,frames}));
"""
    actual = json.loads(subprocess.check_output(['node', '-e', script, str(HERE / 'coin_flow.js')], text=True))
    coins, new = actual['particles'], actual['frames']
    assert len(coins) == 8 and len({coin['speed'] for coin in coins}) == 8
    assert all(coin['size'] == SIZE and coin['speed'] > 0 and coin['cycles'] == int(coin['cycles']) for coin in coins)
    old = old_layout()
    old_metrics, old_rows = measure(old)
    new_metrics, new_rows = measure(new)
    assert new_metrics['pair_frames'] < old_metrics['pair_frames']
    assert new_metrics['close_overlap_pair_samples'] <= old_metrics['close_overlap_pair_samples']
    original = Image.open(ROOT / 'src/main/resources/assets/gtit/textures/items/miao_coin.png').convert('RGBA')
    sprites = [original.crop((0, n * 32, 32, (n + 1) * 32)).resize((SIZE, SIZE), Image.Resampling.NEAREST)
               for n in range(4)]
    alpha_boxes = [sprite.getchannel('A').getbbox() for sprite in sprites]
    min_diameter = min(min(box[2] - box[0], box[3] - box[1]) for box in alpha_boxes)
    assert min_diameter > 32
    clipped_widths = []
    for frame in new:
        for pose in frame:
            left = math.floor(pose['x'] - SIZE / 2 + .5)
            box = alpha_boxes[pose['coinFrame']]
            clipped_widths.append(min(152, left + box[2]) - max(8, left + box[0]))
    assert min(clipped_widths) > 32, 'Do not reduce rows by clipping the coins narrower at the side edges'
    keyframes = list(dict.fromkeys([int(old_rows.sum(axis=1).argmax()), 60, 120,
                                  int(new_rows.sum(axis=1).argmax())]))
    sheet = Image.new('RGBA', (160 * len(keyframes), 148 * 2), '#21190f')
    for row, frames in enumerate([old, new]):
        for column, frame in enumerate(keyframes):
            image = Image.new('RGBA', (160, 128), '#43301c')
            layer = Image.new('RGBA', image.size)
            for pose in frames[frame]:
                sprite = sprites[pose['coinFrame']]
                layer.alpha_composite(sprite, (math.floor(pose['x'] - SIZE / 2 + .5),
                                               math.floor(pose['y'] - SIZE / 2 + .5)))
            image.alpha_composite(layer.crop((8, 8, 152, 96)), (8, 8))
            ImageDraw.Draw(image).rectangle((8, 8, 151, 95), outline='#8f7746')
            sheet.alpha_composite(image, (column * 160, row * 148 + 20))
            ImageDraw.Draw(sheet).text((column * 160 + 5, row * 148 + 3),
                                      f'{"OLD" if row == 0 else "NEW"}  {frame / FPS:.1f}s', fill='#f4dc96')
    OUT.mkdir(parents=True, exist_ok=True)
    sheet.resize((sheet.width * 3, sheet.height * 3), Image.Resampling.NEAREST).save(OUT / 'coin-flow-comparison.png')
    report = dict(scope='240 offline coin-only resource samples; not Minecraft or final cat composition',
                  seed='0x4e454b4f', search='10 restarts x 1400 fixed-seed phase/lane proposals; overlap penalized',
                  period_seconds=PERIOD, fps=FPS, frames=FRAMES, coin_count=8, render_size_px=SIZE,
                  alpha_min_diameter_px=min_diameter, distinct_positive_downward_speeds=8,
                  alpha_min_width_after_horizontal_clip_px=min(clipped_widths),
                  definitions=dict(visible='coin center y in [8,96], inside upper three-row clip',
                                   pair='both visible; center height difference <=6px, horizontal gap >=30px',
                                   triple='three visible; height span <=9px, sorted horizontal gaps >=24px',
                                   sustained='same qualifying pair in >=3 consecutive 10fps samples; circular loop',
                                   overlap='visible pair center distance <30px; penalized to avoid stacking'),
                  baseline=old_metrics, staggered=new_metrics,
                  pair_frame_reduction_percent=round(100 * (1 - new_metrics['pair_frames'] / old_metrics['pair_frames']), 1),
                  keyframe_seconds=[frame / FPS for frame in keyframes],
                  notes='Brief two-coin crossings remain acceptable; zero-row crossings is not a gate.')
    (OUT / 'coin-flow-quality.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
