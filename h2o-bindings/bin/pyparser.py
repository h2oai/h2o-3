# -*- encoding: utf-8 -*-
"""
pyparser -- module for parsing Python files.

This module provides capabilities for parsing Python files into logical pieces. It relies on standard
module :mod:`tokenize` to break the file into tokens, and then combines those tokens into high-level
code constructs. Unlike :mod:`ast`, this module guarantees round-trip behavior in the sense that

    assert pyparser.parse_text(text).unparse() == text

(the only exception being improperly placed line continuation symbols).

:copyright: 2016 H2O.ai
:license: Apache License Version 2.0
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import tokenize
import types

from future.builtins import open

__all__ = []  # ("parse_text", "parse_file")



def parse_text(text):
    gen = iter(text.splitlines(keepends=True))
    readline = gen.next if hasattr(gen, "next") else gen.__next__
    return _parse(readline, target=ParsedCode)


def parse_file(filename):
    """Parse the provided file, and return ParsedFile object."""
    with open(filename, "rt", encoding="utf-8") as f:
        return _parse(f.readline, target=ParsedFile)




#=======================================================================================================================
# Implementation
#=======================================================================================================================

def _parse(readline, target):
    """
    Parse any object through a readline interface.

    :param readline: a function that returns subsequent lines on each call.
    :param target: which class to construct from the list of tokens.
    :returns: an instance of ``target`` class.
    """
    assert isinstance(readline, (types.FunctionType, types.MethodType, types.BuiltinFunctionType))
    assert isinstance(target, type)
    tokens = _normalize_tokens(Token(tok) for tok in tokenize.generate_tokens(readline))
    return target(tokens)


def _normalize_tokens(tokens):
    r"""
    Fix tokenization of dedents / comments and return the corrected list of tokens.

    In order to understand the rationale for this function, consider the following code example::

        def test():
            pass

        # funny function
        def test_funny():
            pass

    Normally, Python will parse it as the following sequence of tokens:

        ['def', 'test', '(', ')', ':', NEWLINE, INDENT, 'pass', NEWLINE, NL, '# funny function', NL,
         DEDENT, 'def', 'test_funny', '(', ')', ':', NEWLINE, INDENT, 'pass', NEWLINE, DEDENT, END]

    The problem here is that the DEDENT token is generated not after the 'pass' but after the comment, which means
    that if we treat INDENTs / DEDENTs as block boundaries, then the comment "belongs" to the first function. This
    is contrary to how most people understand this code.

    This function attempts to rectify the situation by looking at the offset of each comment. If it's higher or
    equal to the current indentation level, then leave the comment as-is. However if it's lower, then we insert
    DEDENT token(s) in front of this comment and ignore the DEDENT token(s) that go after the comment. The
    resulting stream of tokens generates the same source code, yet is more sensible. Note that this function will
    raise an error on some code which is ok from Python's perspective -- this will happen if there is a comment
    which is visually dedented, but does not correspond to a true dedent in the code::

        def test3():
            x = x + 1
          # haha
            print(x)

    We consider such code unreadable and un-Pythonic, so please don't do such things.

    :param tokens: list (or any iterable) of :class:`Token`s.
    :returns: the augmented list of Tokens.
    """
    indent_levels = [0]
    dedents_injected = 0
    out = []
    for i, tok in enumerate(tokens):
        if tok.op == tokenize.INDENT:
            assert dedents_injected == 0, "INDENT encountered, but was expecting a DEDENT"
            assert tok.start_col == 0 and tok.end_col == len(tok.str), "Bad token %r" % tok
            indent_levels.append(tok.end_col)
        elif tok.op == tokenize.DEDENT:
            if dedents_injected:
                dedents_injected -= 1
                continue  # skip this token
            else:
                indent_levels.pop()
        elif tok.op == tokenize.COMMENT:
            loc = (tok.start_row, tok.start_col)
            while indent_levels[-1] > tok.start_col:
                indent_levels.pop()
                out.append(Token((tokenize.DEDENT, '', loc, loc)))
                dedents_injected += 1
        else:
            assert dedents_injected == 0 or tok.op == tokenize.NL
        out.append(tok)
    assert dedents_injected == 0
    return out



#-----------------------------------------------------------------------------------------------------------------------
# Token class
#-----------------------------------------------------------------------------------------------------------------------

class Token(object):
    """
    Single lexical token in the parse stream.

    This is convenience wrapper around the underlying representation of a token as a 5-tuple.
    """

    def __init__(self, t):
        assert (isinstance(t, tuple) and len(t) >= 4 and
                isinstance(t[2], tuple) and len(t[2]) == 2 and
                isinstance(t[3], tuple) and len(t[3]) == 2), "Wrong initializer for Token object: %r" % t
        self._t = t

    @property
    def op(self):
        return self._t[0]

    @property
    def str(self):
        return self._t[1]

    @property
    def start_row(self):
        return self._t[2][0]

    @property
    def start_col(self):
        return self._t[2][1]

    @property
    def end_row(self):
        return self._t[3][0]

    @property
    def end_col(self):
        return self._t[3][1]

    @property
    def token(self):
        return self._t

    def indent(self):
        """Return 1 for an INDENT token, -1 for a DEDENT token, and 0 otherwise."""
        if self.op == tokenize.INDENT: return 1
        if self.op == tokenize.DEDENT: return -1
        return 0

    #-------------------------------------------------------------------------------------------------------------------

    # Mapping from token code to human-readable string
    _token_names = [""] * 300
    for name in dir(tokenize):
        if not name.isupper(): continue
        id = getattr(tokenize, name)
        if isinstance(id, int):
            assert id < 300, "Unexpected token %d : %s" % (id, name)
            _token_names[id] = name
    # List of tokens that do not have meaningful string representation
    _bare_tokens = {tokenize.NL, tokenize.NEWLINE, tokenize.DEDENT, tokenize.ENDMARKER}

    def __repr__(self):
        tok_name = Token._token_names[self.op]
        if self.op not in Token._bare_tokens:
            # Properly escape any special symbols inside `self.str`
            s = repr(self.str)
            if s[0] == "u":
                s = s[2:-1]
            else:
                s = s[1:-1]
            tok_name += "(" + s + ")"
        return "<Token %s at %d:%d..%d:%d>" % (tok_name, self.start_row, self.start_col, self.end_row, self.end_col)


#-----------------------------------------------------------------------------------------------------------------------
# ParsedFile class
#-----------------------------------------------------------------------------------------------------------------------

class ParsedFile(object):
    """
    The "result" class for the :func:`parse_file` function.

    This class represents a parsed Python modulle. At the lowest level it is just a stream of :class:`Token`s, but it
    also allows you to view the file as a tree of high-level constructs.
    """

    def __init__(self, tokens):
        assert isinstance(tokens, list)
        self._tokens = tokens
        # When the file is parsed, this will be the list of all top-level constructs found.
        self._parsed = None

    def parse(self):
        chunk = ParsedCode(self._tokens)
        chunk.parse()
        self._parsed = chunk.parsed

    def unparse(self):
        """Convert the parsed representation back into the source code."""
        if self._parsed:
            pass  # TODO
        else:
            return tokenize.untokenize(t.token for t in self._tokens)



#-----------------------------------------------------------------------------------------------------------------------
# ParsedCode class
#-----------------------------------------------------------------------------------------------------------------------

class ParsedCode(object):
    def __init__(self, tokens):
        assert sum(tok.indent() for tok in tokens) == 0, "Unbalanced indentations in the token stream"
        self._tokens = tokens
        self._parsed = None

    @property
    def parsed(self):
        return self._parsed

    def parse(self):
        self._parsed = self._parse1()

    #-------------------------------------------------------------------------------------------------------------------
    # Private
    #-------------------------------------------------------------------------------------------------------------------

    def _parse1(self):
        """First stage of parsing: find all function / class declarations at the top level."""
        chunks = []

        # If `level` is not None, then we are seacrhing for the end of the block, and this variable contains the
        # current indentation level.
        level = None
        # When searching for the end of the block, this will store its beginning.
        i0 = None
        for i, tok in enumerate(self._tokens):
            if level is None:
                if tok.op == tokenize.NAME and (tok.str == "class" or tok.str == "def"):
                    level = 0
                    i0 = i
            else:
                level += tok.indent()
                if level == 0 and tok.op == tokenize.DEDENT:
                    level = None
                    i0 = None
                    chunks.append((self._tokens[i0].str, i0, i + 1))
        return chunks

    def __repr__(self):
        if self._parsed:
            return "<ParsedCode %r>" % (self._parsed)
        else:
            return "<ParsedCode raw>"


#-----------------------------------------------------------------------------------------------------------------------
# ParsedObject class
#-----------------------------------------------------------------------------------------------------------------------

class ParsedObject(object):
    def __init__(self, objtype, tokens, start, end, hard_start):
        assert tokens[start][1] == objtype
        self.objtype = objtype
        self.tokens = tokens
        self.start = start
        self.end = end
        self.pieces = None
        self.find_end()

    def find_end(self):
        level = 0
        for i in range(self.start, self.end):
            opcode = self.tokens[i][0]
            if opcode == tokenize.INDENT:
                level += 1
            if opcode == tokenize.DEDENT:
                level -= 1
                if level == 0:
                    self.end = i + 1
                    break

    def find_start(self, start0):
        pass

    def __repr__(self):
        return "<ParsedObject(%s) %d..%d>" % (self.objtype, self.start, self.end)
