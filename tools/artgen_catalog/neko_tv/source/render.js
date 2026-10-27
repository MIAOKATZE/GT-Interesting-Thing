// Approved landscape-bigcoin-5 composition. Canvas uses the original four coin frames.
const PERIOD = 12, COIN_SIZE = 48;
const canvas = document.createElement('canvas');
canvas.width = 96;
canvas.height = 64;
const px = canvas.getContext('2d', {willReadFrequently: true});
px.imageSmoothingEnabled = false;
let seed = 0x4e454b4f;
function random() {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    return seed / 4294967296;
}
const particles = [];
for (let n = 0; n < 2; n++) {
    particles.push({phase: n / 2 + random() * .025, drift: random() * Math.PI * 2,
        turn: Math.floor(random() * 4), laneX: n === 0 ? 30 : 66});
}
window.loadSources = async function(sources) {
    const textures = {};
    await Promise.all(Object.entries(sources).map(([name, src]) => new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => { textures[name] = img; resolve(); };
        img.onerror = () => reject(new Error(`Cannot load ${name}`));
        img.src = src;
    })));
    window.pixelsAt = function(time) {
        const t = ((time % PERIOD) + PERIOD) % PERIOD;
        px.clearRect(0, 0, 96, 64);
        px.drawImage(textures.background, 0, 0, 96, 64);
        for (let lane = 0; lane < 5; lane++) {
            const x = 16 + lane * 16, offset = (t / PERIOD * 2 * 44 + lane * 13) % 44;
            px.fillStyle = 'rgba(255,187,47,.055)';
            px.fillRect(x, 10, 3, 46);
            px.fillStyle = 'rgba(255,211,111,.10)';
            px.fillRect(x + 1, Math.round(10 + offset), 1, 9);
        }
        for (const c of particles) {
            const phase = (c.phase + t / PERIOD * 2) % 1;
            const y = -24 + phase * 112;
            const x = c.laneX + Math.sin(phase * Math.PI * 2 + c.drift) * 4;
            const coinFrame = (Math.floor(t / PERIOD * 24) + c.turn) % 4;
            px.drawImage(textures.coin, 0, coinFrame * 32, 32, 32,
                Math.round(x - COIN_SIZE / 2), Math.round(y - COIN_SIZE / 2), COIN_SIZE, COIN_SIZE);
        }
        px.fillStyle = '#30200e'; px.fillRect(3, 60, 90, 4);
        px.fillStyle = '#c69946'; px.fillRect(4, 60, 88, 1);
        px.fillStyle = '#6d4b20'; px.fillRect(8, 63, 80, 1);
        return Array.from(px.getImageData(0, 0, 96, 64).data);
    };
};
