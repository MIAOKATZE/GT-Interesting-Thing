// Offline-only trajectories. Seed 0x4e454b4f; eight fixed coins, never player/order state.
// Staggered phase/lane search also penalized overlap: reducing rows must not stack coins together.
(function () {
    const PERIOD = 24;
    const particles = [
    {
        "phase": 0.751145024,
        "drift": 2.660619399,
        "turn": 2,
        "laneX": 59,
        "cycles": 2,
        "travel": 156,
        "speed": 13.0,
        "size": 35,
        "amplitude": 2.5
    },
    {
        "phase": 0.143977183,
        "drift": 1.741472928,
        "turn": 2,
        "laneX": 44,
        "cycles": 3,
        "travel": 160,
        "speed": 20.0,
        "size": 35,
        "amplitude": 2.5
    },
    {
        "phase": 0.887549465,
        "drift": 1.523154853,
        "turn": 1,
        "laneX": 102,
        "cycles": 4,
        "travel": 164,
        "speed": 27.333333333333332,
        "size": 35,
        "amplitude": 2.5
    },
    {
        "phase": 0.020327407,
        "drift": 2.79064588,
        "turn": 3,
        "laneX": 73,
        "cycles": 2,
        "travel": 168,
        "speed": 14.0,
        "size": 35,
        "amplitude": 2.5
    },
    {
        "phase": 0.627796856,
        "drift": 0.972332558,
        "turn": 0,
        "laneX": 130,
        "cycles": 3,
        "travel": 172,
        "speed": 21.5,
        "size": 35,
        "amplitude": 2.5
    },
    {
        "phase": 0.138802118,
        "drift": 4.562881728,
        "turn": 3,
        "laneX": 88,
        "cycles": 4,
        "travel": 176,
        "speed": 29.333333333333332,
        "size": 35,
        "amplitude": 2.5
    },
    {
        "phase": 0.408833411,
        "drift": 5.234110606,
        "turn": 0,
        "laneX": 117,
        "cycles": 2,
        "travel": 180,
        "speed": 15.0,
        "size": 35,
        "amplitude": 2.5
    },
    {
        "phase": 0.547864476,
        "drift": 3.766634269,
        "turn": 0,
        "laneX": 30,
        "cycles": 3,
        "travel": 184,
        "speed": 23.0,
        "size": 35,
        "amplitude": 2.5
    }
];
    function at(coin, time) {
        const t = ((time % PERIOD) + PERIOD) % PERIOD;
        const phase = (coin.phase + t / PERIOD * coin.cycles) % 1;
        return {
            phase,
            x: coin.laneX + Math.sin(phase * Math.PI * 2 + coin.drift) * coin.amplitude,
            y: -24 + phase * coin.travel,
            coinFrame: (Math.floor(t / PERIOD * 48) + coin.turn) % 4
        };
    }
    globalThis.NekoCoinFlow = {particles, at};
})();
