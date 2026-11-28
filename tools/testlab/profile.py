"""GTIT native configuration and Pocket client profile."""
CONFIG = ('config/gtit/testlab.cfg', 'testlab', 'enabled', 1048580)
CLIENT_UPDATES = {'clientUiEnabled': ('B', 'true')}
CLIENT_RUNNER = None
CLIENT_WORKSPACE = 'miaotest-client-qa'
CLIENT_METADATA = ('client-launch.json', 'host-helper-compile.json')
CLIENT_TOPOLOGY = 'run-owned-integrated'


def client_mode(args, config, preparing, blockers):
    if not config['clientUiEnabled'] and not preparing:
        blockers.append('GTIT clientUiEnabled disabled; use --prepare for temporary native UI opt-in')
    if args.case not in ('all', 'client-ui-pocket'):
        blockers.append('Native client adapter profile is client-ui-pocket; other cases use their declared lane')
    return 'client-ui-pocket'
