from pathlib import Path
import shutil

# Run prepare_smoke.py and the dedicated smoke first; the client reads its real NBT snapshot.
root = Path(__file__).resolve().parents[2]
client = root / 'build/hologram-smoke/client'
mods = client / 'mods'
mods.mkdir(parents=True, exist_ok=True)
for item in (root / 'build/hologram-smoke/server/mods').iterdir():
    target = mods / item.name
    if item.is_dir():
        shutil.copytree(item, target, dirs_exist_ok=True)
    else:
        shutil.copy2(item, target)
# GT5U/VisualProspecting load these APIs during client construction.
cache = Path.home() / '.gradle/caches/modules-2/files-2.1/com.github.GTNewHorizons'
for module, version in [('waila', '1.19.34'), ('Navigator', '1.1.9'), ('NotEnoughItems', '2.8.155-GTNH'), ('CodeChickenCore', '1.4.21')]:
    jars = list((cache / module / version).glob('*/*-dev.jar'))
    if len(jars) != 1:
        raise SystemExit('Expected the resolved ' + module + ' ' + version + ' dev jar in the Gradle cache')
    shutil.copy2(jars[0], mods / jars[0].name)
# The old dedicated-server fixture predates GT5U 205's client IFluidRenderer API.
for name in ['NotEnoughItems-2.8.130-GTNH-dev.jar', 'CodeChickenCore-1.4.17-dev.jar']:
    obsolete = mods / name
    if obsolete.is_file():
        assert obsolete.resolve().parent == mods.resolve()
        obsolete.unlink()
(client / 'options.txt').write_text('lang:zh_CN\nfullscreen:false\nguiScale:1\nsound:0.0\nmusic:0.0\n', encoding='utf-8')
print(str(client))
