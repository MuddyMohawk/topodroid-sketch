"""Recover only Sketch QA's working-directory preference after interrupted physical tests."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
import uuid
import xml.etree.ElementTree as ET

PACKAGE = 'com.topodroid.TDX.sketch'
PREFS = f'shared_prefs/{PACKAGE}_preferences.xml'
MARKER = 'files/qa-physical-cwd.json'
KEY = 'DISTOX_CWD'
QA_PREFIX = 'TopoDroid Sketch QA/'

def preference(xml):
    root = ET.fromstring(xml)
    if root.tag != 'map':
        raise ValueError('Invalid preference document')
    values = [node for node in root if node.get('name') == KEY]
    if len(values) > 1 or (values and values[0].tag != 'string'):
        raise ValueError('Ambiguous working-directory preference')
    return {'present': bool(values), 'value': (values[0].text or '') if values else None}

def with_preference(xml, original):
    preference(xml)  # Validate before changing exactly one key.
    root = ET.fromstring(xml)
    for node in list(root):
        if node.get('name') == KEY:
            root.remove(node)
    if original['present']:
        ET.SubElement(root, 'string', name=KEY).text = original['value']
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)

def validate_marker(marker):
    if marker.get('version') != 1 or marker.get('package') != PACKAGE:
        raise ValueError('Unrecognized physical recovery record')
    original = marker.get('original', {})
    if type(original.get('present')) is not bool or (original['present'] and not isinstance(original.get('value'), str)):
        raise ValueError('Invalid original working directory')
    active = marker.get('qaCwd')
    if active is not None and (not isinstance(active, str) or not active.startswith(QA_PREFIX)):
        raise ValueError('Recovery record does not identify an owned QA directory')
    return marker

def restoration_action(current, marker):
    validate_marker(marker)
    if current == marker['original']:
        return 'already_restored'
    if current['present'] and current['value'] == marker.get('qaCwd'):
        return 'restore'
    if current['present'] and current['value'].startswith(QA_PREFIX):
        raise ValueError('Different QA directory is active; refusing an unowned restoration')
    return 'preserved_user_choice'

def save_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.tmp')
    with temporary.open('w', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)

class Adb:
    def __init__(self, executable, serial):
        if not serial.strip():
            raise ValueError('An explicit physical serial is required')
        self.executable, self.serial = executable, serial

    def run(self, *args):
        result = subprocess.run([self.executable, '-s', self.serial, *args],
                                capture_output=True, timeout=5, check=True)
        return result.stdout.decode('utf-8').strip()

    def physical(self):
        if self.run('get-state') != 'device':
            raise RuntimeError('Physical target is unavailable')
        if self.run('shell', 'getprop ro.kernel.qemu') == '1':
            raise RuntimeError('Physical recovery refuses an emulator')

    def read_private(self, relative):
        if relative not in (MARKER, PREFS):
            raise ValueError('Unallowlisted private read')
        # Match SharedPreferences: a surviving .bak takes precedence after a killed write.
        choice = (f'if [ -f {PREFS}.bak ]; then cat {PREFS}.bak; '
                  f'elif [ -f {PREFS} ]; then cat {PREFS}; else echo QA_ABSENT; fi') if relative == PREFS else (
                  f'if [ -f {MARKER} ]; then cat {MARKER}; else echo QA_ABSENT; fi')
        result = self.run('shell', f"run-as {PACKAGE} sh -c '{choice}'")
        return None if result == 'QA_ABSENT' else result

    def marker(self):
        value = self.read_private(MARKER)
        return validate_marker(json.loads(value)) if value is not None else None

    def prefs(self):
        return self.read_private(PREFS) or '<map />'

    def write_private(self, relative, content):
        if relative not in (PREFS, MARKER):
            raise ValueError('Unallowlisted private write')
        remote = '/data/local/tmp/qa-cwd-' + uuid.uuid4().hex + '.tmp'
        temporary_root=Path(__file__).resolve().parents[1]/'workbench'/'tmp'
        temporary_root.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='qa-cwd-payload-',dir=temporary_root) as directory:
            assert Path(directory).resolve().is_relative_to(temporary_root.resolve())
            payload = Path(directory)/'payload'
            payload.write_bytes(content)
            self.run('push', str(payload), remote)
            try:
                parent = relative.rsplit('/', 1)[0]
                command = (f'mkdir -p {parent} && cat {remote} > {relative}.qa-tmp && '
                           f'mv {relative}.qa-tmp {relative}')
                if relative == PREFS:
                    command += f' && rm -f {PREFS}.bak'
                self.run('shell', f"run-as {PACKAGE} sh -c '{command}'")
            finally:
                try:
                    self.run('shell', f'rm -f {remote}')
                except (subprocess.SubprocessError, OSError):
                    pass  # A disconnected device retains its recovery record.

    def clear_marker(self):
        self.run('shell', f'run-as {PACKAGE} rm -f {MARKER}')

    def stop_owned_packages(self):
        self.run('shell', f'am force-stop {PACKAGE}.test; am force-stop {PACKAGE}')

def capture(device, journal):
    if device.marker() is not None:
        raise RuntimeError('Pending physical working-directory recovery; run qa_physical_cwd.py recover first')
    if Path(journal).exists():
        raise FileExistsError('Refusing to overwrite an existing physical recovery journal')
    original = preference(device.prefs())
    if original['present'] and original['value'].startswith(QA_PREFIX):
        raise RuntimeError('QA directory already active without an original recovery record; do not guess the field directory')
    record = dict(version=1, package=PACKAGE, original=original, qaCwd=None, pending=True)
    save_json(journal, record)  # Durable host copy precedes any device mutation.
    device.write_private(MARKER, json.dumps(record).encode('utf-8'))
    return record

def restore(device, journal=None):
    marker = device.marker()
    host = json.loads(Path(journal).read_text(encoding='utf-8-sig')) if journal and Path(journal).exists() else None
    if marker is None:
        if host is None:
            return 'no_pending_recovery'
        validate_marker(host)
        current = preference(device.prefs())
        if current != host['original'] and current['present'] and current['value'].startswith(QA_PREFIX):
            raise RuntimeError('QA directory active but device ownership record is missing; manual recovery required')
        action = 'already_restored' if current == host['original'] else 'preserved_user_choice'
    else:
        if host and host['original'] != marker['original']:
            raise RuntimeError('Host/device original-directory records differ; refusing to overwrite either')
        current = preference(device.prefs())
        action = restoration_action(current, marker)
        if action == 'restore':
            device.stop_owned_packages()
            # Recheck after stopping writers, and modify only our exact active QA directory.
            xml = device.prefs()
            action = restoration_action(preference(xml), marker)
            if action == 'restore':
                device.write_private(PREFS, with_preference(xml, marker['original']))
                if preference(device.prefs()) != marker['original']:
                    raise RuntimeError('Working-directory restoration verification failed')
        device.clear_marker()  # Never clear the journal before successful verification.
    if host is not None:
        host.update(pending=False, outcome=action)
        save_json(journal, host)
    return action

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['check', 'capture', 'restore', 'recover'])
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--journal')
    args = parser.parse_args()
    device = Adb(args.adb, args.serial)
    device.physical()
    if args.mode == 'check':
        # A first installation has no private state to recover.
        if device.run('shell', f'pm path {PACKAGE}') and device.marker() is not None:
            raise RuntimeError('Pending tablet working-directory recovery; run qa_physical_cwd.py recover before physical testing')
        print('No pending physical working-directory recovery')
    elif args.mode == 'capture':
        if not args.journal:
            parser.error('capture requires --journal')
        capture(device, args.journal)
        print('Original physical working directory recorded')
    else:
        print('Physical working directory:', restore(device, args.journal))

if __name__ == '__main__':
    main()
