#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
This module enables magical transformations in h2o-py.

(more info to follow)
"""
from __future__ import absolute_import, division, print_function
#, unicode_literals

import os
import sys
import tokenize

from future.builtins.misc import open

ROOT_DIR = "../../h2o-py/h2o"

def test(x):
    """identity."""
    return x


def locate_files(root_dir):
    """Find all python files in the given directory and all subfolders."""
    all_files = []
    root_dir = os.path.abspath(root_dir)
    for dir_name, subdirs, files in os.walk(root_dir):
        for f in files:
            if f.endswith(".py"):
                all_files.append(os.path.join(dir_name, f))
    return all_files


@test
def find_magic_in_file(filename):
    """
    Search the file for any magic incantations.

    :param filename: file to search
    :returns: a tuple containing the spell and then maybe some extra words (or None if no magic present)
    """
    with open(filename, "rt", encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                comment = line[1:].strip()
                if comment.startswith("~~~~* ") or comment.startswith("----* ") or comment.startswith("====* "):
                    spell = comment[5:].strip()
                    return tuple(spell.split())
            else:
                break
    return None  # No magic found in file


# Parser
def parse_python_file(filename):
    """Parse file into chunks / objects."""



    with open(filename, "rt", encoding="utf-8") as f:
        tokens = list(tokenize.generate_tokens(f.readline))
        tokens = normalize_tokens(tokens)
        module = ChunkCode(tokens, 0, len(tokens))
        module.parse()
        print(module)



def main():
    """Executed when script is run as-is."""
    # magic_files = {}
    for filename in locate_files(ROOT_DIR):
        print("Processing %s" % filename)
        with open(filename, "rt") as f:
            tokens = list(tokenize.generate_tokens(f.readline))
            text1 = tokenize.untokenize(tokens)
            ntokens = normalize_tokens(tokens)
            text2 = tokenize.untokenize(ntokens)
            assert text1 == text2

        # magic = find_magic_in_file(f)
        # if magic:
        #     magic_files[f] = magic

    # print(magic_files)
    # parse_python_file("pymagic.py")

if __name__ == "__main__":
    main()
