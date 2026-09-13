"""Regression checks for native export artifacts and loop-reset rendering."""
import json
import unittest
from pathlib import Path
from convert import prop, walk
from copy import deepcopy

ROOT = Path(__file__).resolve().parents[2]

class ExportTests(unittest.TestCase):
    def test_offscreen_reset_is_held_not_interpolated(self):
        track = prop([[335, 90], [342, 90], [-22, 90], [-15, 90]])
        boundary = next(k for k in track['k'] if k['t'] == 1)
        self.assertEqual(boundary.get('h'), 1)

    def test_both_platforms_ship_identical_square_vectors(self):
        for name in ('bikelane', 'crosswalk', 'ranredlight', 'reckless', 'parkedillegally'):
            with self.subTest(name=name):
                android = ROOT / 'assets/complaints' / (name + '.json')
                ios = ROOT / 'native/iosApp/ReportediOS/Resources/complaints' / (name + '.json')
                self.assertEqual(android.read_bytes(), ios.read_bytes())
                data = json.loads(android.read_text())
                self.assertEqual((data['w'], data['h']), (320, 320))
                self.assertFalse(any('p' in asset for asset in data.get('assets', [])))
                self.assertFalse(any(node.get('nm', '').endswith('-extension') for node in walk(data)))

    def test_themes_change_only_colors(self):
        def without_colors(data):
            data = deepcopy(data)
            for node in walk(data):
                if node.get('ty') in ('fl', 'st'):
                    node.pop('c', None)
            return data
        for name in ('bikelane', 'crosswalk', 'ranredlight', 'reckless', 'parkedillegally'):
            with self.subTest(name=name):
                folder = ROOT / 'assets/complaints'
                light = json.loads((folder / (name + '.json')).read_text())
                dark_file = folder / (name + '_dark.json')
                dark = json.loads(dark_file.read_text())
                self.assertNotEqual(light, dark)
                self.assertEqual(without_colors(light), without_colors(dark))
                self.assertEqual(dark_file.read_bytes(), (ROOT / 'native/iosApp/ReportediOS/Resources/complaints' / dark_file.name).read_bytes())

    def test_hidden_sidewalk_scene_can_become_visible(self):
        data = json.loads((ROOT / 'assets/complaints/parkedillegally.json').read_text())
        scene = next(n for n in walk(data) if n.get('nm') == 'parked-illegally-sidewalk-scene')
        transform = next(n for n in scene['it'] if n.get('ty') == 'tr')
        self.assertTrue(transform['o']['a'])
        self.assertTrue(any(k['s'] == [100] for k in transform['o']['k']))
        fills = [n for n in walk(scene) if n.get('ty') == 'fl']
        self.assertTrue(fills)
        self.assertTrue(all(n['o'].get('k') != 0 for n in fills))

if __name__ == '__main__':
    unittest.main()
