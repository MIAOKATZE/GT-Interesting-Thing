// Offline native front composition. Cats are perspective/UV/depth-rendered ModelOcelot frames.
const PERIOD = 24, COIN_SIZE = 49, WIDTH = 160, HEIGHT = 128, FPS = 10;
const canvas = document.createElement('canvas');
canvas.width = WIDTH;
canvas.height = HEIGHT;
const px = canvas.getContext('2d', {willReadFrequently: true});
px.imageSmoothingEnabled = false;
const particles = NekoCoinFlow.particles;
function drawerFront(x) {
    // Closed drawer front beneath the solid counter; no cat emerges through a drawer slot.
    px.fillStyle = '#352115'; px.fillRect(x + 2, 105, 60, 21);
    px.fillStyle = '#946032'; px.fillRect(x + 3, 107, 58, 18);
    px.fillStyle = '#be8c49'; px.fillRect(x + 4, 107, 56, 1);
    px.fillStyle = '#6d4326'; px.fillRect(x + 5, 111, 54, 12);
    px.fillStyle = '#a7733b'; px.fillRect(x + 6, 112, 52, 10);
    px.fillStyle = '#966333';
    for (let n = 0; n < 8; n++) px.fillRect(x + 9 + n*6, 114 + n%3*2, 5, 1);
    px.fillStyle = '#cc9c53'; px.fillRect(x + 6, 112, 52, 1);
    px.fillStyle = '#51341e'; px.fillRect(x + 25, 115, 15, 5);
    px.fillStyle = '#8b622f'; px.fillRect(x + 24, 114, 16, 4);
    px.fillStyle = '#e1bb6d'; px.fillRect(x + 25, 114, 14, 1);
    px.fillStyle = '#c69445'; px.fillRect(x + 26, 115, 12, 2);
    px.fillStyle = '#f1d58f'; px.fillRect(x + 25, 115, 1, 2); px.fillRect(x + 38, 115, 1, 2);
    px.fillStyle = '#d7af61'; px.fillRect(x + 2, 104, 60, 1);
    px.fillStyle = '#a87b3c'; px.fillRect(x + 2, 105, 60, 1);
    px.fillStyle = '#5a3b22'; px.fillRect(x + 2, 106, 60, 1);
}
function counterTop() {
    px.fillStyle = '#ad7b42'; px.fillRect(2,96,156,8);
    px.fillStyle = '#d0a15a'; px.fillRect(2,96,156,1);
    px.fillStyle = '#bb8b4d'; px.fillRect(3,97,154,5);
    px.fillStyle = '#a4733d';
    for (let x=8; x<153; x+=17) px.fillRect(x,99+x%2,11,1);
    px.fillStyle = '#c79a54'; px.fillRect(3,102,154,1);
    px.fillStyle = '#8f602f'; px.fillRect(2,103,156,1);
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
        controller: [64, 96, 32, 32], coinSize: COIN_SIZE, coinClip: [8,8,144,88],
        cabinetBackOcclusionY: 96, cabinetFrontOcclusionY: 104,
        cats: 'offline ModelOcelot / asymmetric cabinet-back jump story'};
    window.pixelsAt = function(time, showCoins = true, showCats = true) {
        const t = ((time % PERIOD) + PERIOD) % PERIOD;
        px.clearRect(0, 0, WIDTH, HEIGHT);
        for (let y = 0; y < HEIGHT; y += 32)
            for (let x = 0; x < WIDTH; x += 32)
                px.drawImage(textures.casing, 0, 0, 16, 16, x, y, 32, 32);
        px.drawImage(textures.background, 0, 0, WIDTH, HEIGHT);
        px.save();
        px.beginPath(); px.rect(8, 8, 144, 88); px.clip();
        for (let lane = 0; lane < 7; lane++) {
            const x = 17 + lane * 21, offset = (t / PERIOD * 4 * 80 + lane * 17) % 80;
            px.fillStyle = 'rgba(255,187,47,.045)'; px.fillRect(x, 10, 2, 86);
            px.fillStyle = 'rgba(255,211,111,.10)'; px.fillRect(x, Math.round(10 + offset), 1, 10);
        }
        if (showCoins) for (const c of particles) {
            const {x,y,coinFrame} = NekoCoinFlow.at(c,t);
            px.drawImage(textures.coin, 0, coinFrame * 32, 32, 32,
                Math.round(x - COIN_SIZE / 2), Math.round(y - COIN_SIZE / 2), COIN_SIZE, COIN_SIZE);
        }
        px.restore();
        counterTop();
        if (showCats) {
            const n = Math.floor((t + 1e-7) * FPS) % (PERIOD*FPS);
            px.save(); px.beginPath(); px.rect(2,2,156,103); px.clip();
            px.drawImage(textures.cats, 0, n*HEIGHT, WIDTH, HEIGHT, 0, 0, WIDTH, HEIGHT);
            px.restore();
        }
        drawerFront(0); drawerFront(96);
        // Controller cell is never covered by any TV, cat, coin, counter or drawer pixel.
        px.clearRect(64, 96, 32, 32);
        return Array.from(px.getImageData(0, 0, WIDTH, HEIGHT).data);
    };
};
