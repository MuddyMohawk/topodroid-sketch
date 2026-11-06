"""Host-only restoration contracts; no real adb or public survey files are accessed."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import qa_physical_cwd as guard

class Device:
    def __init__(self, original):
        self.xml = '<map><int name="sentinel" value="17" /></map>'
        self.xml = guard.with_preference(self.xml, original).decode()
        self.record = None
        self.stops = 0
        self.offline = False
    def marker(self):
        if self.offline:
            raise TimeoutError('offline')
        return copy.deepcopy(self.record)
    def prefs(self): return self.xml
    def write_private(self, path, content):
        if path == guard.MARKER: self.record = json.loads(content)
        elif path == guard.PREFS: self.xml = content.decode()
        else: raise AssertionError(path)
    def stop_owned_packages(self): self.stops += 1
    def clear_marker(self): self.record = None
    def enter_fixture(self):
        self.record['qaCwd'] = guard.QA_PREFIX+'owned-run/PhysicalCompat-case'
        self.xml = guard.with_preference(self.xml, dict(present=True,value=self.record['qaCwd'])).decode()

class PhysicalCwdRecovery(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'workbench'/'tmp'
        root.mkdir(parents=True,exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix='cwd-contract-',dir=root)
        assert Path(self.temp.name).resolve().is_relative_to(root.resolve())
        self.addCleanup(self.temp.cleanup)
        self.journal = Path(self.temp.name)/'state.json'
    def device(self, present=True, value='Field & "Cave"'):
        device = Device(dict(present=present,value=value if present else None))
        guard.capture(device,self.journal)
        return device
    def test_interruption_restores_exact_original_and_preserves_unrelated_preference(self):
        d=self.device();d.enter_fixture();self.assertEqual('restore',guard.restore(d,self.journal))
        self.assertEqual(dict(present=True,value='Field & "Cave"'),guard.preference(d.xml))
        self.assertEqual('17',ET.fromstring(d.xml).find('int').get('value'))
        self.assertEqual(1,d.stops);self.assertIsNone(d.record)
        self.assertFalse(json.loads(self.journal.read_text())['pending'])
    def test_absent_original_key_is_removed_rather_than_replaced_with_default(self):
        d=self.device(False);d.enter_fixture();guard.restore(d,self.journal)
        self.assertEqual(dict(present=False,value=None),guard.preference(d.xml))
    def test_empty_original_value_is_distinct_from_absent(self):
        d=self.device(True,'');d.enter_fixture();guard.restore(d,self.journal)
        self.assertEqual(dict(present=True,value=''),guard.preference(d.xml))
    def test_normal_fixture_cleanup_needs_no_preference_rewrite_or_force_stop(self):
        d=self.device();guard.restore(d,self.journal);self.assertEqual(0,d.stops)
    def test_later_user_folder_choice_is_not_overwritten(self):
        d=self.device();d.enter_fixture();d.xml=guard.with_preference(d.xml,dict(present=True,value='Other survey folder')).decode()
        self.assertEqual('preserved_user_choice',guard.restore(d,self.journal));self.assertEqual(0,d.stops)
        self.assertEqual('Other survey folder',guard.preference(d.xml)['value'])
    def test_different_qa_owner_is_refused_and_records_survive(self):
        d=self.device();d.enter_fixture();d.xml=guard.with_preference(d.xml,dict(present=True,value=guard.QA_PREFIX+'another-run')).decode()
        with self.assertRaises(ValueError):guard.restore(d,self.journal)
        self.assertIsNotNone(d.record);self.assertTrue(json.loads(self.journal.read_text())['pending'])
    def test_offline_target_retains_both_recovery_records(self):
        d=self.device();d.enter_fixture();d.offline=True
        with self.assertRaises(TimeoutError):guard.restore(d,self.journal)
        self.assertIsNotNone(d.record);self.assertTrue(json.loads(self.journal.read_text())['pending'])
    def test_recovery_after_host_loss_uses_device_record(self):
        d=self.device();d.enter_fixture();self.journal.unlink()
        self.assertEqual('restore',guard.restore(d));self.assertEqual('Field & "Cave"',guard.preference(d.xml)['value'])
    def test_conflicting_host_and_device_records_are_refused(self):
        d=self.device();d.enter_fixture();d.record['original']['value']='Someone else'
        with self.assertRaises(RuntimeError):guard.restore(d,self.journal)
        self.assertIsNotNone(d.record)
    def test_missing_ownership_marker_cannot_restore_unknown_qa_directory(self):
        d=self.device();d.enter_fixture();d.record=None
        with self.assertRaises(RuntimeError):guard.restore(d,self.journal)
        self.assertTrue(json.loads(self.journal.read_text())['pending'])
    def test_failed_write_keeps_recovery_record(self):
        d=self.device();d.enter_fixture();d.write_private=lambda *args: (_ for _ in ()).throw(OSError('disk'))
        with self.assertRaises(OSError):guard.restore(d,self.journal)
        self.assertIsNotNone(d.record)
    def test_unverified_write_keeps_recovery_record(self):
        d=self.device();d.enter_fixture();d.write_private=lambda *args:None
        with self.assertRaises(RuntimeError):guard.restore(d,self.journal)
        self.assertIsNotNone(d.record)
    def test_pending_record_prevents_a_new_capture(self):
        d=self.device()
        with self.assertRaises(RuntimeError):guard.capture(d,self.journal)
    def test_existing_host_evidence_is_not_overwritten(self):
        self.journal.write_text('preserve evidence')
        with self.assertRaises(FileExistsError):guard.capture(Device(dict(present=False,value=None)),self.journal)
        self.assertEqual('preserve evidence',self.journal.read_text())
    def test_legacy_qa_folder_without_original_is_refused(self):
        d=Device(dict(present=True,value=guard.QA_PREFIX+'unknown'))
        with self.assertRaises(RuntimeError):guard.capture(d,self.journal)
    def test_ambiguous_or_non_string_key_is_rejected(self):
        for xml in ['<map><int name="DISTOX_CWD" value="1"/></map>','<map><string name="DISTOX_CWD">a</string><string name="DISTOX_CWD">b</string></map>']:
            with self.assertRaises(ValueError):guard.preference(xml)

if __name__=='__main__':unittest.main()
