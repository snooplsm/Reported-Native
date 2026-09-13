"""Reuse approved timelines with alternative parked vehicles and open doors."""
from copy import deepcopy
import gzip
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from convert import convert, square_canvas
from palettes import LIGHT, DARK, recolor

ROOT = Path(__file__).parent
NS = 'http://www.w3.org/2000/svg'
ET.register_namespace('', NS)
DOOR = '''<g xmlns="http://www.w3.org/2000/svg" id="open-passenger-door">
<path d="M24-20L49-9L44 9L25 4Z" fill="#233a43" stroke="#387cf4" stroke-width="3" stroke-linejoin="round"/>
<path d="M28-16L44-8L40 3L28 0Z" fill="#8ab6d7"/>
<path d="M49-9L44 9" stroke="#6097fa" stroke-width="4" stroke-linecap="round"/>
<path d="M24-19V5" stroke="#182d37" stroke-width="3"/>
</g>'''
VAN = '''<g xmlns="http://www.w3.org/2000/svg" id="prime-van">
<g fill="#1c2b33"><rect x="-29" y="-39" width="7" height="19" rx="3"/><rect x="22" y="-39" width="7" height="19" rx="3"/><rect x="-29" y="33" width="7" height="19" rx="3"/><rect x="22" y="33" width="7" height="19" rx="3"/></g>
<path d="M-18-63Q0-69 18-63Q26-59 27-42V53Q27 63 18 64H-18Q-27 63-27 53V-42Q-26-59-18-63Z" fill="#263b4c"/>
<path d="M-20-53Q0-62 20-53L22-36H-22Z" fill="#405d70"/>
<path d="M-21-32Q0-38 21-32L23-14H-23Z" fill="#a1c5d9"/>
<path d="M-17-29L-3-32L10-17H-4Z" fill="#c5dfe9" opacity=".5"/>
<rect x="-22" y="-8" width="44" height="61" rx="5" fill="#314c60"/>
<path d="M-25-5V49M25-5V49" stroke="#13b8e8" stroke-width="3"/>
<path d="M-23-56l9-3m28 0 9 3" stroke="#eff8ff" stroke-width="3" stroke-linecap="round"/>
<path d="M-25 55v5M25 55v5" stroke="#ffafa2" stroke-width="3"/>
<path d="M-27-26l-6 3m60-3 6 3" stroke="#263b4c" stroke-width="4" stroke-linecap="round"/>
<g transform="translate(-18 12)" fill="none" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
<path d="M0 14V2h5v7H0M9 9V2h4M17 2v7m0-11v.1M21 9V2h5v7-7h5v7M36 6h5V2h-5v7h5"/>
</g>
<path d="M-17 32Q0 44 18 31M12 31l7-1-1 7" fill="none" stroke="#13b8e8" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M-22 56h44M0 56v7" stroke="#172a37" stroke-width="2"/>
</g>'''

def build():
    scenes = {s['name']: s for s in json.loads(gzip.decompress((ROOT/'motion-capture.json.gz').read_bytes()))}
    for base, name, van in [('blocked-bike-lane','blocked-bike-lane-door',False),('parked-illegally','parked-illegally-door',False),('blocked-bike-lane','blocked-bike-lane-prime',True)]:
        scene = deepcopy(scenes[base])
        root = ET.fromstring(scene['svg'])
        targets = {base+'-vehicle-model-y'}
        if base == 'parked-illegally': targets.add(base+'-sidewalk-vehicle-model-y')
        for element in root.iter():
            if element.get('id') in targets:
                if van:
                    for child in list(element): element.remove(child)
                    element.append(ET.fromstring(VAN))
                else:
                    element.append(ET.fromstring(DOOR))
        scene['svg'] = ET.tostring(root, encoding='unicode')
        animation = convert(scene)
        animation['nm'] = name
        (ROOT/'lottie'/f'{name}.json').write_text(json.dumps(animation,separators=(',',':')))
        square_canvas(root,base)
        (ROOT/f'{name}.svg').write_text(ET.tostring(root,encoding='unicode'))
        for suffix,palette in [('light',LIGHT),('dark',DARK)]:
            (ROOT/'lottie'/f'{name}_{suffix}.json').write_text(json.dumps(recolor(animation,palette),separators=(',',':')))
        print(name)

if __name__ == '__main__': build()
