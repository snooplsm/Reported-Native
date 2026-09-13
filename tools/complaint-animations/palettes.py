"""Color-only variants of the approved motion, shared by both native apps."""
from copy import deepcopy

LIGHT = {
    '#dce4e7': '#777c80',
    '#f3f5ee': '#e3e4de', '#e9e5db': '#e3e4de',
    '#b8c9cb': '#626b70', '#afbec2': '#626b70',
    '#d2cfc5': '#c3c6bd',
    '#7bcab4': '#329b50', '#387cf4': '#2e6ed6', '#6097fa': '#548be3',
    '#ed6559': '#db5a4f',
    '#db7760': '#587a91', '#eda48c': '#83a0b1', '#914c41': '#344f64',
}
DARK = {
    '#dce4e7': '#343e48',
    '#f3f5ee': '#252b33', '#e9e5db': '#252b33',
    '#b8c9cb': '#647580', '#afbec2': '#647580',
    '#d2cfc5': '#46515a', '#aab9bb': '#5b6b75', '#f9faf5': '#87969f',
    '#ffffff': '#d0d9df', '#eff8ff': '#dce8f0',
    '#7bcab4': '#417f72',
    '#387cf4': '#6399f5', '#6097fa': '#82b1fa',
    '#db7760': '#819baa', '#eda48c': '#adc2cf', '#914c41': '#4a6578',
    '#587a91': '#819baa', '#83a0b1': '#adc2cf',
    '#ed6559': '#f48779', '#e8b83f': '#dec064',
    '#f6c653': '#e8c967', '#61c59b': '#75c9a7',
    '#53616a': '#46515c', '#9ebdef': '#7899bb',
    '#624c41': '#93765f', '#806353': '#b0927a',
}

def recolor(animation, palette):
    result = deepcopy(animation)
    def color(value):
        key = '#' + ''.join(f'{round(x * 255):02x}' for x in value[:3])
        target = palette.get(key)
        if not target:
            return value
        return [int(target[i:i+2], 16) / 255 for i in (1, 3, 5)] + value[3:]
    def visit(node):
        if isinstance(node, list):
            for item in node: visit(item)
        elif isinstance(node, dict):
            if node.get('ty') in ('fl', 'st') and 'c' in node:
                prop = node['c']
                if prop.get('a'):
                    for frame in prop['k']:
                        for key in ('s', 'e'):
                            if key in frame: frame[key] = color(frame[key])
                else:
                    prop['k'] = color(prop['k'])
            for item in node.values(): visit(item)
    visit(result)
    return result
