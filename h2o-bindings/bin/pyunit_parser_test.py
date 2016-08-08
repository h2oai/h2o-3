#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Test case for pyparser."""

import textwrap

import pyparser


def test_pyparser():
    """Test case: general parsin."""
    code1 = textwrap.dedent("""
        # -*- encoding: utf-8 -*-
        # copyright: 2016 h2o.ai
        \"\"\"
        A code example.

        It's not supposed to be functional, or even functionable.
        \"\"\"
        from __future__ import braces, antigravity

        # Standard library imports
        import sys
        import time
        import this

        import h2o
        from h2o import H2OFrame, init
        from . import *

        # Do some initalization for legacy python versions
        if PY2:
            def unicode():
                raise RuntimeError   # disable this builtin function
                                     # because it doesn't exist in Py3

        handler = lambda: None  # noop
                                # (will redefine later)

        ################################################################################

        # comment 1
        class Foo(object):
            #---------------------------------------------------------------------------
            def bar(self):
                pass

        # def foo():
        #     print(1)

        #     print(2)

        # comment 2
        @decorated(
            1, 2, (3))
        @dddd
        def bar():
            print("bar!")
        # bye""")
    print(code1)

    res = pyparser.parse_text(code1)
    assert res.unparse() == code1, "%r vs %r" % (code1, res.unparse())
    res.parse()
    for i, tok in enumerate(res._tokens):
        print("%3d  %r" % (i, tok))
    print(res)

test_pyparser()
