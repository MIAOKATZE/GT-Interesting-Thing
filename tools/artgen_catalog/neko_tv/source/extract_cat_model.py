"""Extract actual Minecraft 1.7.10 ModelOcelot constructor data after Gradle setup.

Run from the repository: python tools/artgen_catalog/neko_tv/source/extract_cat_model.py
The saved JSON is a geometry/UV snapshot, not a manually drawn substitute model.
"""
import hashlib
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
SOURCE = ROOT / 'build/rfg/minecraft-src/java/net/minecraft/client/model'


def numbers(arguments):
    return [float(n.rstrip('F')) for n in arguments.split(', ')]


def main():
    text = (SOURCE / 'ModelOcelot.java').read_text(encoding='utf-8')
    constructor = text.split('public ModelOcelot()')[1].split('/**')[0]
    offsets = {name: [int(u), int(v)] for name, u, v in
               re.findall(r'setTextureOffset\("([^"]+)", (\d+), (\d+)\)', constructor)}
    parts = []
    for match in re.finditer(r'this\.(\w+) = new ModelRenderer\(this, ([^)]+)\);', constructor):
        name, texture = match.groups()
        start = match.end()
        end = constructor.find(' = new ModelRenderer', start)
        chunk = constructor[start:end] if end != -1 else constructor[start:]
        boxes = []
        for arguments in re.findall(r'\.addBox\(([^)]+)\)', chunk):
            if arguments.startswith('"'):
                box_name, arguments = arguments.split('", ')
                uv = offsets[texture.strip('"') + '.' + box_name.strip('"')]
            else:
                uv = [int(v) for v in texture.split(', ')]
            data = numbers(arguments)
            boxes.append({'origin': data[:3], 'size': [int(v) for v in data[3:6]], 'uv': uv})
        pivot = numbers(re.search(r'\.setRotationPoint\(([^)]+)\)', chunk)[1])
        parts.append({'name': name, 'pivot': pivot, 'boxes': boxes})
    assert len(parts) == 8 and sum(len(p['boxes']) for p in parts) == 11
    result = {
        'minecraft': '1.7.10', 'texture_size': [64, 32],
        'provenance': {
            'generated_source_directory': 'build/rfg/minecraft-src/java/net/minecraft/client/model',
            'geometry': 'ModelOcelot constructor; uninflated ModelBox geometry',
            'uv': 'ModelBox quadList[0..5], TexturedQuad constructor vertex order',
            'source_sha256': {name: hashlib.sha256((SOURCE / name).read_bytes()).hexdigest()
                              for name in ('ModelOcelot.java', 'ModelBox.java', 'TexturedQuad.java')},
            'texture_jar': 'build/rfg/mcp_patched_minecraft-sources.jar',
            'texture_entries': {
                'red': {'entry': 'assets/minecraft/textures/entity/cat/red.png',
                        'sha256': 'a6fb1611ceb69a0f978326b76dc40d9e45bb67e0f1ebbdc4b727510d537b8d55'},
                'black': {'entry': 'assets/minecraft/textures/entity/cat/black.png',
                          'sha256': '4cac187aa0cf1bcc3a7a99df10d8c25c2593854d4e591ed8d7cbe6ea4f58b229'}}},
        'parts': parts}
    (HERE / 'cat-model.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print('Extracted 8 ModelOcelot parts / 11 ModelBoxes with original UV offsets.')


if __name__ == '__main__':
    main()
