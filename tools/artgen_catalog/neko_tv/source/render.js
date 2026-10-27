// Full 5x4 front. Original four coin frames, native casing rim, controller left open.
const PERIOD = 12, COIN_SIZE = 49, WIDTH = 160, HEIGHT = 128;
const canvas = document.createElement('canvas');
canvas.width = WIDTH;
canvas.height = HEIGHT;
const px = canvas.getContext('2d', {willReadFrequently: true});
px.imageSmoothingEnabled = false;
let seed = 0x4e454b4f;
function random() {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    return seed / 4294967296;
}
const particles = [];
for (let n = 0; n < 8; n++) {
    const cycles = 1 + n % 3, travel = 176 + n * 8;
    particles.push({phase: (n / 8 + random() * .025) % 1,
        drift: random() * Math.PI * 2, turn: Math.floor(random() * 4),
        laneX: [30, 80, 130][n % 3], cycles, travel,
        speed: travel * cycles / PERIOD, size: COIN_SIZE});
}
window.loadSources = async function(sources) {
    const textures = {};
    await Promise.all(Object.entries(sources).map(([name, src]) => new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => { textures[name] = img; resolve(); };
        img.onerror = () => reject(new Error(`Cannot load ${name}`));
        img.src = src;
    })));
    window.nekoTV = {width: WIDTH, height: HEIGHT, period: PERIOD, particles,
        controller: [64, 96, 32, 32], coinSize: COIN_SIZE};
    window.pixelsAt = function(time) {
        const t = ((time % PERIOD) + PERIOD) % PERIOD;
        px.clearRect(0, 0, WIDTH, HEIGHT);
        // Match the 16px casing UVs at the outer edge of every 32px block.
        for (let y = 0; y < HEIGHT; y += 32)
            for (let x = 0; x < WIDTH; x += 32)
                px.drawImage(textures.casing, 0, 0, 16, 16, x, y, 32, 32);
        px.drawImage(textures.background, 0, 0, WIDTH, HEIGHT);
        px.save();
        px.beginPath();
        px.rect(8, 8, 144, 112);
        px.rect(60, 92, 40, 28);
        px.clip('evenodd');
        for (let lane = 0; lane < 7; lane++) {
            const x = 17 + lane * 21, offset = (t / PERIOD * 2 * 100 + lane * 17) % 100;
            px.fillStyle = 'rgba(255,187,47,.045)';
            px.fillRect(x, 10, 2, 108);
            px.fillStyle = 'rgba(255,211,111,.10)';
            px.fillRect(x, Math.round(10 + offset), 1, 10);
        }
        for (const c of particles) {
            const phase = (c.phase + t / PERIOD * c.cycles) % 1;
            const y = -24 + phase * c.travel;
            const x = c.laneX + Math.sin(phase * Math.PI * 2 + c.drift) * 2;
            const coinFrame = (Math.floor(t / PERIOD * 24) + c.turn) % 4;
            px.drawImage(textures.coin, 0, coinFrame * 32, 32, 32,
                Math.round(x - COIN_SIZE / 2), Math.round(y - COIN_SIZE / 2), COIN_SIZE, COIN_SIZE);
        }
        px.restore();
        // The actual controller keeps its native front texture, never a TV tile.
        px.clearRect(64, 96, 32, 32);
        return Array.from(px.getImageData(0, 0, WIDTH, HEIGHT).data);
    };
};
