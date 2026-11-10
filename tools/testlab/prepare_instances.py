"""Resolve existing fixed instances from the root registry. No pack extraction."""
import argparse
import json
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'tools/testlab'))
import host


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--registry', type=Path, default=host.REGISTRY)
    parser.add_argument('--name', choices=['GTIT', 'GTSR', 'GTMB', 'GTSWN'], action='append')
    parser.add_argument('--zip', type=Path, help='Deprecated: full-pack extraction is disabled')
    parser.add_argument('--parent', type=Path, help='Deprecated: paths come from registry')
    args = parser.parse_args()
    if args.zip or args.parent:
        parser.error('Full-pack extraction is disabled; reuse --registry instances')
    results = []
    for name in args.name or ['GTIT', 'GTSR', 'GTMB', 'GTSWN']:
        item = host.registered(args.registry, name)
        results.append({'instance': name, 'server': str(item['_path']), 'client': item['client'],
                        'world': str(host.server_world(item)), 'copyBytes': 0, 'state': 'VERIFIED_NOT_LAUNCHED'})
    print(json.dumps({'registrySHA256': host.sha(args.registry), 'instances': results}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
