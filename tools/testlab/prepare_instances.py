"""Extract independent server copies. Never overwrite an existing instance."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

NAMES = ('GTIT', 'GTSR', 'GTMB', 'GTSWN', 'OUTPOST')
PREFIX = '\u6d4b\u8bd5\u5b9e\u4f8b-'


def configure(root, name, port):
    props = root / 'server.properties'
    lines = props.read_text(encoding='utf-8').splitlines()
    settings = {'server-ip': '127.0.0.1', 'server-port': str(port),
                'enable-rcon': 'false', 'enable-query': 'false',
                'level-name': 'world', 'motd': 'GTIT dedicated testlab ' + name}
    lines = [line for line in lines if line.split('=', 1)[0] not in settings]
    props.write_text('\n'.join(lines + [k + '=' + v for k, v in settings.items()]) + '\n', encoding='utf-8')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--zip', required=True, type=Path)
    p.add_argument('--parent', type=Path, default=Path('E:/CodeGT'))
    args = p.parse_args()
    digest = hashlib.file_digest(args.zip.open('rb'), 'sha256').hexdigest()
    with zipfile.ZipFile(args.zip) as archive:
        required = sum(i.file_size for i in archive.infolist()) * len(NAMES)
        if shutil.disk_usage(args.parent).free < required + 2**30:
            raise SystemExit('Insufficient disk space')
        for info in archive.infolist():
            name = Path(info.filename)
            if name.is_absolute() or '..' in name.parts or ':' in info.filename:
                raise SystemExit('Unsafe ZIP path: ' + info.filename)
        for index, name in enumerate(NAMES):
            root = args.parent / (PREFIX + name)
            root.mkdir()  # Fail rather than merge with or overwrite existing data.
            archive.extractall(root)
            configure(root, name, 25671 + index)
            manifest = {'instance': name, 'port': 25671 + index, 'bind': '127.0.0.1',
                        'source_zip': str(args.zip.resolve()), 'source_sha256': digest,
                        'eula': 'Preserved from archive; not accepted by this tool',
                        'copy_mode': 'independent extraction; no hardlinks'}
            (root / 'testlab-instance.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
            print(json.dumps(manifest, ensure_ascii=True))


if __name__ == '__main__':
    main()
