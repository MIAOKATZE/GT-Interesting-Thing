from pathlib import Path
import shutil
import json
from datetime import datetime

root = Path(__file__).resolve().parents[2]
server = root / 'build/hologram-smoke/server'
server.mkdir(parents=True, exist_ok=True)
source = root / 'run/server/mods'
mods = server / 'mods'
mods.mkdir(exist_ok=True)
copied = []
for item in source.iterdir():
    target = mods / item.name
    if item.is_dir():
        shutil.copytree(item, target, dirs_exist_ok=True)
    else:
        shutil.copy2(item, target)
    copied.append(item.name)
world = 'hologram-smoke-' + datetime.now().strftime('%Y%m%d-%H%M%S')
(server / 'eula.txt').write_text('eula=true\n', encoding='ascii')
(server / 'server.properties').write_text('\n'.join([
    'level-name=' + world, 'level-type=FLAT', 'generator-settings=2;0;1;', 'server-port=25579',
    'server-ip=127.0.0.1', 'online-mode=false', 'white-list=true', 'spawn-protection=0',
    'spawn-monsters=false', 'spawn-animals=false', 'generate-structures=false', 'allow-nether=false',
    'max-tick-time=120000', 'view-distance=3', 'max-players=1', 'enable-rcon=false',
    'enable-query=false', 'snooper-enabled=false', 'motd=GTIT isolated hologram smoke'
]) + '\n', encoding='ascii')
receipt = {'working_directory': str(server), 'world': world, 'mods_copied_from': str(source), 'mods': copied,
           'shared_test_instance_used': False, 'binding': '127.0.0.1:25579'}
(root / 'build/hologram-smoke/environment.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
print(json.dumps(receipt))
