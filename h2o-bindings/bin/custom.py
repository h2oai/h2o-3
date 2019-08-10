from collections import defaultdict
import os

_gen_customizations = defaultdict(dict)


def get_customizations_for(language, algo, property=None, default=None):
    lang_customizations = _gen_customizations[language]
    if algo not in lang_customizations:
        custom_file = os.path.join(os.path.dirname(__file__), 'custom', language, 'gen_{}.py'.format(algo.lower()))
        customizations = dict()
        if os.path.isfile(custom_file):
            with open(custom_file) as f:
                exec(f.read(), customizations)
        lang_customizations.update({algo: customizations})

    customizations = lang_customizations[algo]
    if property:
        tokens = property.split('.')
        value = customizations
        for token in tokens:
            value = value.get(token)
            if value is None:
                return default
        return value
    else:
        return customizations


def reformat_block(string, indent=0, indent_first=True, prefix='', prefix_first=True, strip='\n'):
    if not string:
        return prefix if prefix_first else ""
    lines = string.strip(strip).split("\n")
    if len(lines) == 1:
        return (prefix if prefix_first else '') + (indent * ' ' if indent_first else '') + lines[0].strip()
    line0_indent = len(lines[0]) - len(lines[0].lstrip())
    out = ""
    for idx, line in enumerate(lines):
        dedented_line = line.lstrip()
        line_indent = len(line) - len(dedented_line)
        rel_indent = (line_indent - line0_indent)
        pref = prefix if (prefix_first or idx > 0) else ''
        if dedented_line:
            ind = (indent + rel_indent) * ' ' if (indent_first or idx > 0) else ''
            out += pref + ind + dedented_line + '\n'
        else:
            out += pref + '\n'
    return out.strip(strip)
