#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Test case for pyparser."""
from __future__ import division, print_function

import textwrap
import tokenize

import pyparser

def _make_2_tuple(op):
    return lambda x: (op, x)

NL = tokenize.NL
NEWLINE = tokenize.NEWLINE
NAME = _make_2_tuple(tokenize.NAME)
OP = _make_2_tuple(tokenize.OP)
INDENT = tokenize.INDENT
DEDENT = tokenize.DEDENT
COMMENT = tokenize.COMMENT
STRING = tokenize.STRING
NUMBER = tokenize.NUMBER
END = tokenize.ENDMARKER
token_names = {NL: "NL", NEWLINE: "NEWLINE", INDENT: "INDENT", COMMENT: "COMMENT", DEDENT: "DEDENT",
               STRING: "STRING", NUMBER: "NUMBER", END: "END", tokenize.OP: "OP", tokenize.NAME: "NAME"}

def test_normalize_tokens():
    """
    Test function for ``pyparser._normalize_tokens()``.

    Even though this function is private, it is extremely important to verify that it behaves correctly. In
    particular, we want to check that it does not break the round-trip guarantee of the tokenizer, and that it
    fixes all the problems that the original tokenizer has.
    """
    # Helper functions
    def _parse_to_tokens(text):
        """Parse text into tokens and then normalize them."""
        gen = iter(text.splitlines(True))  # True = keep newlines
        readline = gen.next if hasattr(gen, "next") else gen.__next__
        tokens = [pyparser.Token(tok) for tok in tokenize.generate_tokens(readline)]
        pyparser._normalize_tokens(tokens)
        return tokens

    def _unparse_tokens(tokens):
        """Convert tokens back into the source code."""
        return tokenize.untokenize(t.token for t in tokens)

    def _assert_tokens(tokens, target):
        """Check that the tokens list corresponds to the target provided."""
        for i in range(len(tokens)):
            assert i < len(target), "Token %d %r not expected" % (i, tokens[i])
            tok = tokens[i]
            trg = target[i]
            valid = False
            if isinstance(trg, int):
                if tok.op == trg: valid = True
                name = token_names[trg]
            elif isinstance(trg, tuple) and len(trg) == 2:
                if tok.op == trg[0] and tok.str == trg[1]: valid = True
                name = "%s(%s)" % (token_names[trg[0]], trg[1])
            else:
                assert False, "Unknown target: %r" % trg
            if not valid:
                assert False, "Mismatched token %d: found %r, should be %r" % (i, tok, name)
        assert len(target) == len(tokens), "Expected too many tokens: %d vs %d" % (len(tokens), len(target))

    def check_code(code, expected_tokens):
        """Test parsing of the given piece of code."""
        code = textwrap.dedent(code)
        check_code.index = getattr(check_code, "index", 0) + 1
        print("Testing fragment %d:" % check_code.index, end=" ")
        tokens = _parse_to_tokens(code)
        try:
            try:
                unparsed = _unparse_tokens(tokens)
            except ValueError as e:
                raise AssertionError("Cannot unparse tokens: %s" % e)
            assert unparsed == code, "Unparsed code does not match the original:\n" + unparsed
            _assert_tokens(tokens, expected_tokens)
            print("ok")
        except AssertionError as e:
            print("Error: %s" % e)
            print("Original code fragment:\n" + code)
            print("Tokens:")
            for i, tok in enumerate(tokens):
                print("%3d %r" % (i, tok))
            raise

    check_code("""
        try:
            while True:
                pass
                # comment
        except: pass
        """, [NL, NAME("try"), OP(":"), NEWLINE, INDENT, NAME("while"), NAME("True"), OP(":"), NEWLINE,
              INDENT, NAME("pass"), NEWLINE, COMMENT, NL, DEDENT, DEDENT, NAME("except"), OP(":"),
              NAME("pass"), NEWLINE, END]
    )
    check_code("""
        try:
            while True:
                pass
            # comment
        except: pass
        """, [NL, NAME("try"), OP(":"), NEWLINE, INDENT, NAME("while"), NAME("True"), OP(":"), NEWLINE,
              INDENT, NAME("pass"), NEWLINE, DEDENT, COMMENT, NL, DEDENT, NAME("except"), OP(":"),
              NAME("pass"), NEWLINE, END]
    )
    check_code("""
        try:
            while True:
                pass
        # comment
        except: pass
        """, [NL, NAME("try"), OP(":"), NEWLINE, INDENT, NAME("while"), NAME("True"), OP(":"), NEWLINE,
              INDENT, NAME("pass"), NEWLINE, DEDENT, DEDENT, COMMENT, NL, NAME("except"), OP(":"),
              NAME("pass"), NEWLINE, END]
    )
    check_code("""
        def func():
            # function
            pass
        """, [NL, NAME("def"), NAME("func"), OP("("), OP(")"), OP(":"), NEWLINE, INDENT, COMMENT, NL,
              NAME("pass"), NEWLINE, DEDENT, END])
    check_code("""
        def foo():
            pass

        #comment
        def bar():
            pass
        """, [NL, NAME("def"), NAME("foo"), OP("("), OP(")"), OP(":"), NEWLINE, INDENT, NAME("pass"), NEWLINE,
              DEDENT, NL, COMMENT, NL, NAME("def"), NAME("bar"), OP("("), OP(")"), OP(":"), NEWLINE, INDENT,
              NAME("pass"), NEWLINE, DEDENT, END])
    check_code("""
        def hello():


            print("hello")
        """, [NL, NAME("def"), NAME("hello"), OP("("), OP(")"), OP(":"), NEWLINE, INDENT, NL, NL,
              NAME("print"), OP("("), STRING, OP(")"), NEWLINE, DEDENT, END])
    check_code("""
        if PY2:
            def unicode():
                raise RuntimeError   # disable this builtin function
                                     # because it doesn't exist in Py3

        handler = lambda: None  # noop
                                # (will redefine later)

        ################################################################################

        # comment 1
        print("I'm done.")
        """, [NL, NAME("if"), NAME("PY2"), OP(":"), NEWLINE, INDENT, NAME("def"), NAME("unicode"), OP("("), OP(")"),
              OP(":"), NEWLINE, INDENT, NAME("raise"), NAME("RuntimeError"), COMMENT, NEWLINE, COMMENT, NL,
              DEDENT, DEDENT, NL, NAME("handler"), OP("="), NAME("lambda"), OP(":"), NAME("None"), COMMENT, NEWLINE,
              COMMENT, NL, NL, COMMENT, NL, NL, COMMENT, NL, NAME("print"), OP("("), STRING, OP(")"), NEWLINE, END])
    check_code("""
        def test3():
            x = 1
        # bad
            print(x)
        """, [NL, NAME("def"), NAME("test3"), OP("("), OP(")"), OP(":"), NEWLINE, INDENT, NAME("x"), OP("="),
              NUMBER, NEWLINE, COMMENT, NL, NAME("print"), OP("("), NAME("x"), OP(")"), NEWLINE, DEDENT, END])
    check_code("""
        class Foo(object):
            #-------------
            def bar(self):
                if True:
                    pass

        # Originally the DEDENTs are all the way down near the decorator. Here we're testing how they'd travel
        # all the way up across multiple comments.

        # comment 3

        # commmmmmmment 4
        @decorator
        """, [NL, NAME("class"), NAME("Foo"), OP("("), NAME("object"), OP(")"), OP(":"), NEWLINE, INDENT,
              COMMENT, NL, NAME("def"), NAME("bar"), OP("("), NAME("self"), OP(")"), OP(":"), NEWLINE, INDENT,
              NAME("if"), NAME("True"), OP(":"), NEWLINE, INDENT, NAME("pass"), NEWLINE,
              DEDENT, DEDENT, DEDENT, NL, COMMENT, NL, COMMENT, NL, NL, COMMENT, NL, NL, COMMENT,
              NL, OP("@"), NAME("decorator"), NEWLINE, END])

    # Really, one should avoid code like this.... It won't break the normalizer, but may create problems down
    # the stream.
    check_code("""
        if True:
            if False:
        # INDENT will be inserted after this comment
                raise
        # DEDENT will be after this comment (really, there is no good alternative for this one...)
            else:
                praise()
        """, [NL, NAME("if"), NAME("True"), OP(":"), NEWLINE, INDENT, NAME("if"), NAME("False"), OP(":"), NEWLINE,
              COMMENT, NL, INDENT, NAME("raise"), NEWLINE, COMMENT, NL, DEDENT, NAME("else"), OP(":"), NEWLINE,
              INDENT, NAME("praise"), OP("("), OP(")"), NEWLINE, DEDENT, DEDENT, END])


def test_pyparser():
    """Test case: general parsing."""
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
            #------ Public -------------------------------------------------------------
            def bar(self):
                pass

        # def foo():
        #     print(1)
        #
        #     print(2)

        # comment 2
        @decorated(
            1, 2, (3))
        @dddd
        def bar():
            # be
            # happy
            print("bar!")
        # bye""")
    print(code1)

    res = pyparser.parse_text(code1)
    # for i, tok in enumerate(res._tokens):
    #     print("%3d %r" % (i, tok))
    res.parse()
    assert res.unparse() == code1, "%r vs %r" % (code1, res.unparse())
    print(res)


test_normalize_tokens()
test_pyparser()
