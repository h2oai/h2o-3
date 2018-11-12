#!/usr/bin/python
# -*- encoding: utf-8 -*-
from __future__ import division, print_function

import argparse
import os
import re
import sys

try:
    import pip
    version_tuple = tuple(map(int, pip.__version__.split('.')))
    if version_tuple >= (10, 0, 0):
        from pip._internal.utils.misc import get_installed_distributions
    else:
        from pip import get_installed_distributions
except ImportError:
    pip = None
    print("Module pip is not installed", file=sys.stderr)
    sys.exit(2)


def get_requirements(kind, metayaml_file):
    assert kind in {"build", "test"}

    assert os.path.isfile(metayaml_file), "Cannot find file " + metayaml_file

    with open(metayaml_file, "rt") as y:
        yaml = parse_yaml(y.read())
        if kind == "build":
            return yaml["requirements"]["build"]
        if kind == "test":
            return yaml["requirements"]["run"] + yaml["requirements"]["test"]


def parse_yaml(yaml_text):
    def tokenize(yaml):
        indents = [0]
        yield "INDENT"
        for lineno, line in enumerate(yaml.split("\n")):
            lline = line.rstrip()
            bline = lline.lstrip()
            if bline == "":
                continue
            indent_len = len(lline) - len(bline)
            assert indent_len >= 0
            if indent_len > indents[-1]:
                indents.append(indent_len)
                yield "INDENT"
            while indent_len < indents[-1]:
                indents.pop()
                yield "DEDENT"
            assert indent_len == indents[-1], "Unexpected indentation in YAML file, line %d" % lineno
            yield bline
        yield "DEDENT"

    def consume_object(token_stream):
        yaml = None
        for tok in token_stream:
            if tok.startswith("#"):
                continue
            if tok.startswith("{%") and tok.endswith("%}"):
                continue
            if tok == "INDENT":
                assert yaml is None
                continue
            if tok == "DEDENT":
                break
            if yaml is None:
                if re.match("^(\w+):.*", tok):
                    yaml = {}
                if tok.startswith("-"):
                    yaml = []
                assert yaml is not None, "Unexpected token: %s" % tok
            if isinstance(yaml, dict):
                mm = re.match("^(\w+):(.*)", tok)
                assert mm, "Unexpected token: %s" % tok
                if mm.group(2):
                    yaml[mm.group(1)] = mm.group(2).strip()
                else:
                    yaml[mm.group(1)] = consume_object(token_stream)
            if isinstance(yaml, list):
                yaml.append(tok[1:].lstrip())
        return yaml

    return consume_object(tokenize(yaml_text))


def test_requirements(kind, metayaml_file, installed):
    assert kind in {"build", "test"}
    requirements = get_requirements(kind, metayaml_file)
    messages = []
    for req in requirements:
        module, version = (req + " ").split(" ", 1)
        if module == "python":
            continue
        if version:
            assert version.startswith(">="), "Unexpected version spec: %s" % version
            version = version[2:].strip()
            msg = test_module(module, version, installed)
        else:
            msg = test_module(module, "0", installed)
        if msg:
            messages.append(msg)
    return messages


def test_module(mod, min_version, installed_modules):
    minv = tuple(int(x) for x in min_version.split("."))
    matching_modules = [d for d in installed_modules if d.key == mod]
    if not matching_modules:
        return "Python module `%s` is missing: install it with `pip install '%s>=%s'`" % (mod, mod, min_version)

    v = max(m.version for m in matching_modules)
    for i, vp in enumerate(v.split(".")):
        if i >= len(minv):
            break
        if vp.isdigit():
            intv = int(vp)
        else:
            j = 0
            while j < len(vp) and vp[j].isdigit():
                j += 1
            intv = int(vp[:j])
        if intv > minv[i]:
            break
        elif intv < minv[i]:
            return ("Python module `%s` has version %s whereas version %s is required: upgrade it with "
                    "`pip install %s --upgrade`" % (mod, v, min_version, mod))


def main(kind, metayaml_file):
    installed = get_installed_distributions(skip=())
    msgs = test_requirements(kind, metayaml_file, installed)
    if msgs:
        print("\n    ERRORS:\n", file=sys.stderr)
        for msg in msgs:
            print("    " + msg, file=sys.stderr)
        print("", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    thisdir = os.path.dirname(os.path.abspath(__file__))
    metayaml = os.path.join(thisdir, "..", "conda", "h2o", "meta.yaml")

    parser = argparse.ArgumentParser(description="Check that python dependencies are installed")
    parser.add_argument("--metayaml", help="Path to meta.yaml file describing the dependencies", default=metayaml)
    parser.add_argument("--kind", help="build|test", default="build")
    args = parser.parse_args()
    main(args.kind, args.metayaml)
