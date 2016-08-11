#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Test case for pyparser."""
from __future__ import division, print_function

import os
import re
import textwrap
import tokenize

import colorama
from colorama import Fore, Style
from future.builtins import open

import pyparser

def _make_tuple(op):
    return lambda x: (op, x)

NL = tokenize.NL
NEWLINE = tokenize.NEWLINE
NAME = _make_tuple(tokenize.NAME)
OP = _make_tuple(tokenize.OP)
INDENT = tokenize.INDENT
DEDENT = tokenize.DEDENT
COMMENT = tokenize.COMMENT
STRING = tokenize.STRING
NUMBER = tokenize.NUMBER
END = tokenize.ENDMARKER
token_names = {NL: "NL", NEWLINE: "NEWLINE", INDENT: "INDENT", COMMENT: "COMMENT", DEDENT: "DEDENT",
               STRING: "STRING", NUMBER: "NUMBER", END: "END", tokenize.OP: "OP", tokenize.NAME: "NAME"}

Ws = pyparser.Whitespace
Comment = pyparser.Comment
Comment_banner = (pyparser.Comment, "banner")
Comment_code = (pyparser.Comment, "code")
Docstring = pyparser.Docstring
Import_future = (pyparser.ImportBlock, "future")
Import_stdlib = (pyparser.ImportBlock, "stdlib")
Import_3rdpty = (pyparser.ImportBlock, "third-party")
Import_1stpty = (pyparser.ImportBlock, "first-party")
Expression = pyparser.Expression
Function = (pyparser.Callable, "def")
Class = (pyparser.Callable, "class")

colorama.init()

def assert_same_code(code1, code2):
    """Verify whether 2 code fragments are identical, and if not print an error message."""
    regex = re.compile(r"\s+\\$", re.M)
    code1 = re.sub(regex, r"\\", code1)
    code2 = re.sub(regex, r"\\", code2)
    if code2 != code1:
        print()
        lines_code1 = code1.splitlines()
        lines_code2 = code2.splitlines()
        n_diffs = 0
        for i in range(len(lines_code1)):
            old_line = lines_code1[i]
            new_line = lines_code2[i] if i < len(lines_code2) else ""
            if old_line != new_line:
                print(("%3d " % (i + 1)) + Fore.RED + "- " + old_line + Style.RESET_ALL)
                print(("%3d " % (i + 1)) + Fore.LIGHTGREEN_EX + "+ " + new_line + Style.RESET_ALL)
                n_diffs += 1
                if n_diffs == 5: break
        raise AssertionError("Unparsed code1 does not match the original.")



def test_tokenization():
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
        return pyparser._tokenize(readline)

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

    def check_code(code, expected_tokens=None, filename=None):
        """Test parsing of the given piece of code."""
        code = textwrap.dedent(code)
        if filename:
            print("Testing tokenization of %s:" % filename, end=" ")
        else:
            check_code.index = getattr(check_code, "index", 0) + 1
            print("Testing tokenization %d:" % check_code.index, end=" ")
        tokens = _parse_to_tokens(code)
        try:
            try:
                unparsed = _unparse_tokens(tokens)
            except ValueError as e:
                raise AssertionError("Cannot unparse tokens: %s" % e)
            assert_same_code(code, unparsed)
            if expected_tokens:
                _assert_tokens(tokens, expected_tokens)
            print("ok")
        except AssertionError as e:
            print(u"Error: %s" % e)
            print(u"Original code fragment:\n" + code)
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
        def func():  # function
                     # hanging comment
            pass
        """, [NL, NAME("def"), NAME("func"), OP("("), OP(")"), OP(":"), COMMENT, NEWLINE, INDENT, COMMENT, NL,
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
        class Foo:
            def foo(self):
                pass

            def bar(self):
                return
        """, [NL, NAME("class"), NAME("Foo"), OP(":"), NEWLINE, INDENT, NAME("def"), NAME("foo"), OP("("),
              NAME("self"), OP(")"), OP(":"), NEWLINE, INDENT, NAME("pass"), NEWLINE, DEDENT, NL, NAME("def"),
              NAME("bar"), OP("("), NAME("self"), OP(")"), OP(":"), NEWLINE, INDENT, NAME("return"), NEWLINE, DEDENT,
              DEDENT, END])
    check_code("""
        def foo():
            # Attempt to create the output directory
            try:
                os.makedirs(destdir)
            except OSError as e:
                raise
        """, [NL, NAME("def"), NAME("foo"), OP("("), OP(")"), OP(":"), NEWLINE, INDENT, COMMENT, NL, NAME("try"),
              OP(":"), NEWLINE, INDENT, NAME("os"), OP("."), NAME("makedirs"), OP("("), NAME("destdir"), OP(")"),
              NEWLINE, DEDENT, NAME("except"), NAME("OSError"), NAME("as"), NAME("e"), OP(":"), NEWLINE, INDENT,
              NAME("raise"), NEWLINE, DEDENT, DEDENT, END])
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
                # INDENT will be inserted before this comment
                raise
                # DEDENT will be after this comment
            else:
                praise()
        """, [NL, NAME("if"), NAME("True"), OP(":"), NEWLINE, INDENT, NAME("if"), NAME("False"), OP(":"), NEWLINE,
              INDENT, COMMENT, NL, NAME("raise"), NEWLINE, COMMENT, NL, DEDENT, NAME("else"), OP(":"), NEWLINE,
              INDENT, NAME("praise"), OP("("), OP(")"), NEWLINE, DEDENT, DEDENT, END])

    for directory in [".", "../../h2o-py/h2o", "../../h2o-py/tests"]:
        absdir = os.path.abspath(directory)
        for dir_name, subdirs, files in os.walk(absdir):
            for f in files:
                if f.endswith(".py"):
                    filename = os.path.join(dir_name, f)
                    with open(filename, "rt", encoding="utf-8") as fff:
                        check_code(fff.read(), filename=filename)


def test_pyparser():
    """Test case: general parsing."""
    def _check_blocks(actual, expected):
        assert actual, "No parse results"
        for i in range(len(actual)):
            assert i < len(expected), "Unexpected block %d:\n%r" % (i, actual[i])
            valid = False
            if isinstance(expected[i], type):
                if isinstance(actual[i], expected[i]): valid = True
            elif isinstance(expected[i], tuple):
                if isinstance(actual[i], expected[i][0]) and actual[i].type == expected[i][1]: valid = True
            if not valid:
                assert False, "Invalid block: expected %r, got %r" % (expected[i], actual[i])


    def check_code(code, blocks=None, filename=None):
        code = textwrap.dedent(code)
        if not code.endswith("\n"): code += "\n"
        if filename:
            print("Testing file %s..." % filename, end=" ")
        else:
            check_code.index = getattr(check_code, "index", 0) + 1
            print("Testing code fragment %d..." % check_code.index, end=" ")
        preparsed = None
        parsed = None
        unparsed = None
        try:
            preparsed = pyparser.parse_text(code)
            parsed = preparsed.parse(2)
            try:
                unparsed = parsed.unparse()
            except ValueError as e:
                for i, tok in enumerate(parsed.tokens):
                    print("%3d %r" % (i, tok))
                raise AssertionError("Cannot unparse code: %s" % e)
            assert_same_code(code, unparsed)
            if blocks:
                _check_blocks(parsed.parsed, blocks)
            print("ok")
        except AssertionError as e:
            print()
            print(Style.BRIGHT + Fore.LIGHTRED_EX + u"Error: " + str(e) + Style.RESET_ALL)
            print(Style.BRIGHT + u"Original code fragment:\n" + Style.RESET_ALL + code)
            if unparsed: print(Style.BRIGHT + u"Unparsed code:\n" + Style.RESET_ALL + unparsed)
            if parsed:
                print(parsed)
                for i, tok in enumerate(parsed.tokens):
                    print("%3d %r" % (i, tok))
            raise
        except Exception as e:
            print()
            print(Style.BRIGHT + Fore.LIGHTRED_EX + u"Error: " + str(e) + Style.RESET_ALL)
            if preparsed:
                print("Preparsed tokens:")
                for i, tok in enumerate(preparsed.tokens):
                    print("%4d %r" % (i, tok))
            else:
                print("Initial parsing has failed...")
            raise

    check_code("""
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
        # bye""", [Ws, Comment, Docstring, Import_future, Ws, Import_stdlib, Ws, Import_1stpty, Ws, Expression,
                   Ws, Expression, Ws, Comment_banner, Ws, Class, Ws, Comment_code, Ws, Function, Comment, Ws])

    for directory in [".", "../../h2o-py", "../../py"]:
        absdir = os.path.abspath(directory)
        for dir_name, subdirs, files in os.walk(absdir):
            for f in files:
                if f.endswith(".py"):
                    filename = os.path.join(dir_name, f)
                    with open(filename, "rt", encoding="utf-8") as fff:
                        check_code(fff.read(), filename=filename)



# test_tokenization()
test_pyparser()
