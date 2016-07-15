#!/usr/bin/env python
# -*- encoding: utf-8 -*-
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
# This file is a collection of notes / guidelines / best practices for developing the library that is compatible with
# both Python 2 and 3. This is mostly compiled from
#   http://python-future.org/compatible_idioms.html
#   https://docs.python.org/3.0/whatsnew/3.0.html
#
# First step in "future-proofing" the script is to include the following:
#   the __future__ statement makes sure that major Python syntax features are compatible across Py2 and Py3
#   the future.builtins import replaces several builtin types/functions in Py2 with their Py3 equivalents. The important
#       ones are: str, int, list, dict, object, range, open, next, pow, round, ...
from __future__ import division, print_function, absolute_import, unicode_literals
from h2o.compatibility import *  # NOQA


# Use print() as a function only:
print("Testing Python 2 & 3 compatibilities", end="...\n")

# "/" means floating point division. Use "//" for integer division
assert 2 / 3 * 3 == 2.0
assert 2 // 3 == 0
assert 3 / 2 == 1.5

# Python 3 has no "long" integer types (long is the same as int). Do not use repr() on integers, prefer str(). The
# former appends "L" to long integers on Python 2 but not on Python 3.
#     from future.builtins import int
# ensures that type-checking works both in Py2 and Py3.
x = 1234567890987654321234567890987654321
assert str(x) == "1234567890987654321234567890987654321"
assert isinstance(x, int)
assert isinstance(2**128, int)

# With the future "unicode_literals" import all string literals are unicode.
# In order to test whether a variable is a string, import the following:
#     from future.builtins import str
s = "こんにちは"
assert len(s) == 5
assert isinstance(s, str)

# In Py2 range() created a list, which is inefficient. Use the following import:
#     from future.builtins import range
for i in range(10 ** 5): pass
seq = list(range(7))
assert seq == [0, 1, 2, 3, 4, 5, 6]


#---- Dictionaries ----
# Python 3 discards of .iterkeys(), .itervalues() and .iteritems(), so don't use those (see PEP-3106).
#   .keys() in Py2 returns a list of dict's keys, in Py3 it's a dictionary view object.
# The .compatibility layer exports symbols `viewkeys`, `viewitems`, `viewvalues`, which should be the
# preferred way for iterating over keys/items/values.
#
planets = {"Mercury": 0.330, "Venus": 4.87, "Earth": 5.97, "Mars": 0.642, "Jupyter": 1898, "Saturn": 568,
           "Uranus": 86.8, "Neptune": 102}

# Iterating through keys:
for name in viewkeys(planets): pass

# Iterating through dictionary items:
for name, mass in viewitems(planets): pass

# Iterating through values:
for mass in viewvalues(planets): pass

# Getting keys as a list:
keyslist = list(planets)
assert isinstance(keyslist, list)

# Checking if dictionary contains a key (note that `smth in dictionary.keys()` is horribly inefficient!)
assert "Earth" in planets

# DON'T do any of the following:
#   `for name in planets.keys(): ...`
#   `for mass in planets.values(): ...`
#   `for name, mass in planets.items(): ...`


#---- Maps & filters ----
# In Py2 map() returned a list of mapped values, in Py3 map() returns an iterator.
# To get consistent behavior, one would need to write list(map(...)) -- however this is inefficient in Py2 as it creates
# an extra array copy. Instead, replace all map()s with array comprehensions:
mapped = [name.upper() for name in planets]

# DON'T ever run map for the purpose of its side effects -- it won't be executed in Py3 and you'll have a bug!


# future.builtins.open makes all files opened in Unicode-aware fashion (unless opened in "b" mode):
with open(__file__, "r", encoding="utf-8") as o:
    print("(This script has %d lines)" % len(list(o)))

# Don't use cmp() and __cmp__() -- gone in Py3.


#-----------------------------------------------------------------------------------------------------------------------
#   General conventions:
#-----------------------------------------------------------------------------------------------------------------------
#
# 1. Each script should start with the 3 line header used at the top of this file.
# 2. Lines are 120 chars long. Occasional overruns are ok, but if possible should be avoided.
# 3. Indentation is 4 spaces, and using spaces only.
# 4. Intellij Preferences > Editor > Inspections > Python
#    Enable all inspections, except the following:
#      - Argument passed to function is equal to default parameter value
#      - Too broad exception clauses
#      - PEP 8 coding style violation -- enable, but add the following to Ignored list:
#          E241 Multiple spaces after ':' or ','  (occasionally we vertical-align fragments of similar code)
#          E265 Block comment should start with '# '  (this prevents nicely formatted "banner" comments)
#          E302 Functions should be separated with 2 blank lines  (sometimes we want more than 2 for better structure)
#          E303 same?
#          E701 Multiple statements on the same line  (statements like "if foo: continue" are more readable in 1 line)
# 5. Prefer "double-quoted" strings.
#
print("Done.")
