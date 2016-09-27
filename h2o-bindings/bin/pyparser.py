# -*- encoding: utf-8 -*-
r"""
pyparser -- module for parsing Python files.

This module provides capabilities for parsing Python files into logical pieces. It relies on standard
module :mod:`tokenize` to break the file into tokens, and then combines those tokens into high-level
code constructs. Unlike :mod:`ast`, this module guarantees round-trip behavior in the sense that

    assert pyparser.parse_text(text).unparse() == text

There are few exceptions to this rules. Most notably, comments that has wrong indentation (negative
relative to the current block) will be forcibly moved to the proper position. Also, continuation lines
(i.e. "\" symbols at the end of the line) may shift slightly, and whitespace at the end of lines will get
removed. Lastly, parsing may break if the file does not contain a \n in the last line -- however fixing the
parser is more tedious than fixing that newline.

:copyright: 2016 H2O.ai
:license: Apache License Version 2.0
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import re
import sys
import tokenize
from tokenize import INDENT, DEDENT, NEWLINE, NL, COMMENT, NAME, OP, STRING, ENDMARKER

import colorama

if sys.version_info < (3,):
    # On Python 2, we need this newer version of ``open`` in order to read file in utf-8 encoding.
    from io import open
    _str_type = (str, unicode)
else:
    _str_type = str


# List of symbols exported from this module
__all__ = ("parse_text", "parse_file")



def parse_text(text):
    """Parse code from a string of text."""
    assert isinstance(text, _str_type), "`text` parameter should be a string, got %r" % type(text)
    gen = iter(text.splitlines(True))  # True = keep newlines
    readline = gen.next if hasattr(gen, "next") else gen.__next__
    return Code(_tokenize(readline))


def parse_file(filename):
    """Parse the provided file, and return Code object."""
    assert isinstance(filename, _str_type), "`filename` parameter should be a string, got %r" % type(filename)
    with open(filename, "rt", encoding="utf-8") as f:
        return Code(_tokenize(f.readline))



#=======================================================================================================================
# Implementation
#=======================================================================================================================

def _tokenize(readline):
    """
    Parse any object accessible through a readline interface into a list of :class:`Token`s.

    This function is very similar to :func:`tokenize.generate_tokens`, with few differences. First, the returned list
    is a list of :class:`Token` objects instead of 5-tuples. This may be slightly less efficient, but far more
    convenient for subsequent parsing. Second, the list of tokens is **normalized** to better match the way humans
    understand the code, not what is more suitable for the compiler.

    To better understand the normalization process, consider the following code example::

        def test():
            pass

        # funny function
        def test_funny():
            # not so funny
            pass

    Normally, Python will parse it as the following sequence of tokens:

        ['def', 'test', '(', ')', ':', NEWLINE, INDENT, 'pass', NEWLINE, NL, '# funny function', NL, DEDENT,
         'def', 'test_funny', '(', ')', ':', NEWLINE, '# not so funny', NL, INDENT, 'pass', NEWLINE, DEDENT, END]

    The problem here is that the DEDENT token is generated not after the first 'pass' but after the comment, which
    means that if we treat INDENTs / DEDENTs as block boundaries, then the comment "belongs" to the first function.
    This is contrary to how most people would understand this code. Similarly, the second comment visually goes
    after the INDENT, not before. Consequently, after "normalization" this function will return the following list
    of tokens:

        ['def', 'test', '(', ')', ':', NEWLINE, INDENT, 'pass', NEWLINE, DEDENT, NL, '# funny function', NL,
         'def', 'test_funny', '(', ')', ':', NEWLINE, INDENT, '# not so funny', NL, 'pass', NEWLINE, DEDENT, END]

    :param readline: a function that allows access to the code being parsed in a line-by-line fashion.

    :returns: a list of :class:`Token`s.
    """
    assert callable(readline), "`readline` should be a function"

    # Generate the initial list of tokens.
    tokens = [Token(tok) for tok in tokenize.generate_tokens(readline)]

    # Determine the levels of all indents / dedents.
    indents_stack = [0]  # Stack of all indent levels up to the current parsing point
    for tok in tokens:
        if tok.op == INDENT:
            tok.pre_indent = indents_stack[-1]
            indents_stack.append(tok.end_col)
            tok.post_indent = tok.end_col
        elif tok.op == DEDENT:
            tok.pre_indent = indents_stack.pop()
            tok.post_indent = indents_stack[-1]
        elif tok.op == COMMENT:
            tok.pre_indent = tok.post_indent = indents_stack[-1]

    # Iterate through tokens backwards and see whether it's necessary to swap any of them.
    i = len(tokens) - 1
    while i >= 2:
        pptok, ptok, tok = tokens[i - 2:i + 1]
        if tok.op == INDENT:
            if ptok.op == NL and pptok.op == COMMENT:
                # Comment preceding an INDENT token
                indent, nl, comment = tok, ptok, pptok
                assert nl.start_col == comment.end_col
                underindent = indent.post_indent - comment.start_col
                if underindent > 0:
                    _warn("Comment '%s' is under-indented. Fixing..." % comment.str)
                    comment.move(0, underindent)
                    nl.move(0, underindent)
                indent.move(-1, 0)
                tokens[i - 2:i + 1] = indent, comment, nl
                comment.pre_indent = comment.post_indent = indent.post_indent
                assert indent.end_row == comment.start_row and indent.end_col <= comment.start_col
            elif ptok.op == NL and ptok.start_col == 0:
                # Empty line before an INDENT
                indent, nl = tok, ptok
                indent.move(-1, 0)
                tokens[i - 1:i + 1] = indent, nl
        elif tok.op == DEDENT and ptok.op == NL:
            if pptok.op == COMMENT:
                # Comment preceding a DEDENT. Switch only if comment is not at the level of the previous block!
                dedent, nl, comment = tok, ptok, pptok
                if comment.start_col <= dedent.post_indent:
                    rel_indent = comment.start_col - dedent.start_col
                    if rel_indent < 0:
                        _warn("Comment '%s' has wrong indentation" % comment.str)
                        ptok.move(0, -rel_indent)
                        comment.move(0, -rel_indent)
                    dedent.move(-1)
                    tokens[i - 2:i + 1] = dedent, comment, nl
                    comment.pre_indent = comment.post_indent = dedent.post_indent
                    i += 1
                    continue
            elif ptok.start_col == 0:
                # Empty line before a DEDENT
                dedent, nl = tok, ptok
                dedent.move(-1, -dedent.start_col)
                tokens[i - 1:i + 1] = dedent, nl
                i += 1
                continue
            else:
                assert False, "Unexpected sequence of tokens: %r %r %r" % (pptok, ptok, tok)
        elif tok.op == COMMENT:
            if tok.start_col < tok.pre_indent:
                _warn("Comment '%s' is under-indented relative to the surrounding block" % tok.str)
        i -= 1

    return tokens


def _warn(message):
    if not hasattr(_warn, "colorama_initialized"):
        colorama.init()
        _warn.colorama_initialized = True
    print(colorama.Fore.YELLOW + "    Warning: " + message + colorama.Fore.RESET)



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
        self._op = t[0]
        self._str = t[1]
        self._start_row = t[2][0]
        self._start_col = t[2][1]
        self._end_row = t[3][0]
        self._end_col = t[3][1]
        # Indent levels before / after the token. These will be the same for all tokens, except for INDENTs and DEDENTs
        self.pre_indent = None
        self.post_indent = None

    @property
    def op(self):
        return self._op

    @property
    def str(self):
        return self._str

    @property
    def start_row(self):
        return self._start_row

    @property
    def start_col(self):
        return self._start_col

    @property
    def end_row(self):
        return self._end_row

    @property
    def end_col(self):
        return self._end_col

    @property
    def token(self):
        return (self._op, self._str, (self._start_row, self._start_col), (self._end_row, self._end_col), "")

    def indent(self):
        """Return 1 for an INDENT token, -1 for a DEDENT token, and 0 otherwise."""
        if self._op == INDENT: return 1
        if self._op == DEDENT: return -1
        return 0

    def paren(self):
        """Return 1 for an OP '(' token, -1 for an OP ')' token, and 0 otherwise."""
        if self._op == OP:
            if self._str == "(": return 1
            if self._str == ")": return -1
        return 0

    def move(self, drow, dcol=0):
        """Move the token by `drow` rows and `dcol` columns."""
        self._start_row += drow
        self._start_col += dcol
        self._end_row += drow
        self._end_col += dcol

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
    _bare_tokens = {NL, NEWLINE, DEDENT, ENDMARKER}

    def __repr__(self):
        tok_name = Token._token_names[self._op]
        if self._op not in Token._bare_tokens:
            # Properly escape any special symbols inside `self.str`
            s = repr(self.str)
            if s[0] == "u":
                s = s[2:-1]
            else:
                s = s[1:-1]
            tok_name += "(" + s + ")"
        return "<Token %s at %d:%d..%d:%d>" % (tok_name, self._start_row, self._start_col, self._end_row, self._end_col)



#-----------------------------------------------------------------------------------------------------------------------
# Untokenizer
#-----------------------------------------------------------------------------------------------------------------------

class Untokenizer(object):
    r"""
    Helper class to convert stream of Tokens back into code text.

    This is **very** similar to ``tokenize.Untokenizer``, with few differences: (1) it accepts the list of
    :class:`Token`s instead of tuples; (2) it supports multiple invokations, so that the user may call
    :meth:`add_tokens` multiple times, and finally get the :meth:`result`; (3) it inserts a single space before each
    line continuation backslash.
    """

    def __init__(self, start_row=1):
        self._untokens = []  # List of string representations of all processed tokens
        self._indents = []   # Indent levels as strings (needed to handle tabs as indents)
        self._last_row = start_row
        self._last_col = 0
        self._startline = False

    def add_tokens(self, tokens):
        for tok in tokens:
            if tok.op == ENDMARKER: break
            if tok.op == INDENT:
                self._indents.append(tok.str)
                continue
            elif tok.op == DEDENT:
                self._indents.pop()
                self._last_row = tok.end_row
                self._last_col = tok.end_col
                continue
            elif tok.op in {NEWLINE, NL}:
                self._startline = True
            elif self._startline and self._indents:
                if tok._start_col >= len(self._indents[-1]):
                    self._untokens.append(self._indents[-1])
                    self._last_col = len(self._indents[-1])
                self._startline = False
            self._add_whitespace(tok.start_row, tok.start_col)
            self._untokens.append(tok.str)
            self._last_row = tok.end_row
            self._last_col = tok.end_col
            if tok.op in {NEWLINE, NL}:
                self._last_row += 1
                self._last_col = 0

    def result(self):
        assert not self._indents, "Untokenizer did not finish properly: indent levels remain unbalanced."
        return "".join(self._untokens)

    def _add_whitespace(self, row, col):
        if row < self._last_row or row == self._last_row and col < self._last_col:
            raise ValueError("start ({},{}) precedes previous end ({},{})"
                             .format(row, col, self._last_row, self._last_col))
        row_offset = row - self._last_row
        if row_offset:
            self._untokens.append(" \\\n" * row_offset)
            self._last_col = 0
        col_offset = col - self._last_col
        if col_offset:
            self._untokens.append(" " * col_offset)



#-----------------------------------------------------------------------------------------------------------------------
# ParsedBase class
#-----------------------------------------------------------------------------------------------------------------------

class ParsedBase(object):
    def __init__(self, tokens):
        assert sum(tok.indent() for tok in tokens) == 0, "Unbalanced indentations in the token stream"
        self._tokens = tokens
        self._parsed = None
        self._type = None

    def parse(self, level=1):
        if level <= 0: return
        self._parsed = self._parse()
        if self._parsed:
            for p in self._parsed:
                assert isinstance(p, ParsedBase), "You should parse the tokens into a list of ParsedBase objects."
                p.parse(level - 1)
        return self

    def unparse(self):
        """Convert the parsed representation back into the source code."""
        ut = Untokenizer(start_row=self._tokens[0].start_row)
        self._unparse(ut)
        return ut.result()

    @property
    def type(self):
        return self._type

    @type.setter
    def type(self, val):
        self._type = val

    @property
    def tokens(self):
        return self._tokens

    @property
    def parsed(self):
        return self._parsed

    def _parse(self):
        # Override this method to provide meaningful parsing.
        return None

    def _unparse(self, ut):
        # You may override this method as well, for custom unparsing
        if self._parsed:
            for part in self._parsed:
                part._unparse(ut)
        else:
            ut.add_tokens(self._tokens)

    def __repr__(self):
        if self._type:
            s = "<%s %s>:\n" % (self.__class__.__name__, self._type)
        else:
            s = "<%s>:\n" % self.__class__.__name__
        if self._parsed:
            for p in self._parsed:
                try:
                    r = repr(p)
                    for line in r.splitlines(True):
                        s += "    %s" % line
                except UnicodeEncodeError as e:
                    s += "<%s>: %s" % (p.__class__.__name__, e)
        else:
            for line in self.unparse().splitlines(True):
                s += "    %s" % line
        return s



#-----------------------------------------------------------------------------------------------------------------------
# Code class
#-----------------------------------------------------------------------------------------------------------------------

class Code(ParsedBase):

    def __init__(self, tokens):
        super(Code, self).__init__(tokens)
        self._type = "unparsed"

    def _parse(self):
        self._type = ""
        parsed = self._parse2(self._parse1())
        for p in parsed:
            p.parse()
        return parsed

    def _parse1(self):
        """
        First stage of parsing the code (stored as a raw stream of tokens).

        This method will do the initial pass of the ``self._tokens`` list of tokens, and mark different section as
        belonging to one of the categories: comment, whitespace, docstring, import, code, decorator, def, class, end.
        These sections will be returned as a list of tuples ``(fragment_type, start, end)``, where ``start`` and ``end``
        are indices in the list of raw tokens.
        """
        fragments = []
        tokens = self._tokens

        def advance_after_newline(i0):
            """Return the index of the first token after the end of the current (logical) line."""
            for i in range(i0, len(tokens)):
                if tokens[i].op == NEWLINE:
                    break
            return i + 1

        i = 0
        while i < len(tokens):
            # Assume that we always start at the beginning of a new block
            i0 = i
            tok = tokens[i]
            fragment_type = "???"  # to be determined in the switch clause below

            if tok.op == ENDMARKER:
                fragment_type = "end"
                i += 1
                assert i == len(tokens), "ENDMARKER token encountered before the end of the stream"

            elif tok.op == NL:
                fragment_type = "whitespace"
                # If there are multiple whitespaces, gobble them all
                while tokens[i].op == NL:
                    i += 1

            elif tok.op == COMMENT:
                fragment_type = "comment"
                # Collapse multiple comment lines into a single comment fragment; but only if they are at the same
                # level of indentation.
                is_banner = False
                while i < len(tokens) and tokens[i].op == COMMENT and tokens[i].start_col == tok.start_col:
                    assert tokens[i + 1].op == NL, "Unexpected token after a comment: %r" % tokens[i + 1]
                    s = tokens[i].str
                    if re.match(r"^#\s?[#*=-]{10,}$", s) or re.match(r"^#\s?[#*=-]{4,}.*?[#*=-]{4,}$", s):
                        is_banner = True
                    i += 2
                if is_banner:
                    fragment_type = "banner-comment"

            elif (tok.op == STRING and tokens[i + 1].op == NEWLINE and
                  all(frag[0] == "whitespace" or frag[0] == "comment" for frag in fragments)):
                i += 2
                fragment_type = "docstring"

            elif tok.op == OP and tok.str == "@" and tokens[i + 1].op == NAME:
                while tokens[i].op == OP and tokens[i].str == "@" and tokens[i + 1].op == NAME:
                    i = advance_after_newline(i)
                fragment_type = "decorator"

            elif tok.op == NAME and tok.str in {"from", "import"}:
                while tokens[i].op == NAME and tokens[i].str in {"from", "import"}:
                    i = advance_after_newline(i)
                fragment_type = "import"

            elif tok.op in {INDENT, DEDENT, NEWLINE}:
                assert False, "Unexpected token %d: %r" % (i, tok)

            else:
                i = advance_after_newline(i)
                if i < len(tokens) and tokens[i].op == INDENT:
                    level = 1
                    while level > 0:
                        i += 1
                        level += tokens[i].indent()
                    assert tokens[i].op == DEDENT
                    i += 1  # consume the last DEDENT
                while i < len(tokens) and tokens[i].op == COMMENT and tokens[i].start_col > tok.start_col:
                    assert tokens[i + 1].op == NL
                    i += 2
                if tok.op == NAME and tok.str in {"def", "class"}:
                    fragment_type = tok.str
                else:
                    fragment_type = "code"
            assert i > i0, "Stuck at i = %d" % i
            fragments.append((fragment_type, i0, i))
        return fragments


    def _parse2(self, fragments):
        """
        Second stage of parsing: convert ``fragments`` into the list of code objects.

        This method in fact does more than simple conversion of fragments into objects. It also attempts to group
        certain fragments into one, if they in fact seem like a single piece. For example, decorators are grouped
        together with the objects they decorate, comments that explain certain objects or statements are attached to
        those as well.
        """
        out = []
        tokens = self._tokens
        i = 0
        saved_start = None
        while i < len(fragments):
            ftype, start, end = fragments[i]
            assert start == (0 if i == 0 else fragments[i - 1][2]), "Discontinuity in `fragments` at i = %d" % i
            if ftype == "whitespace" or ftype == "end":
                assert saved_start is None
                obj = Whitespace(tokens[start:end])
            elif ftype == "docstring":
                assert saved_start is None
                obj = Docstring(tokens[start:end])
            elif ftype == "comment":
                assert saved_start is None
                next_frag = fragments[i + 1][0] if i + 1 < len(fragments) else "end"
                if next_frag in {"docstring", "end", "whitespace", "comment", "banner-comment"}:
                    # Possibly merge with the previous Comment instance
                    # if (len(out) >= 2 and isinstance(out[-1], Whitespace) and isinstance(out[-2], Comment) and
                    #         out[-2].type != "banner"):
                    #     obj = Comment(out[-2].tokens + out[-1].tokens + tokens[start:end])
                    #     del out[-2:]
                    # else:
                    obj = Comment(tokens[start:end])
                elif next_frag in {"decorator", "import", "def", "class", "code"}:
                    # save this comment for later
                    saved_start = start
                    i += 1
                    continue
                else:
                    raise RuntimeError("Unknown token type %s" % next_frag)
            elif ftype == "banner-comment":
                assert saved_start is None
                obj = Comment(tokens[start:end])
                obj.type = "banner"
            elif ftype == "decorator":
                if saved_start is None:
                    saved_start = start
                i += 1
                continue
            elif ftype == "import":
                real_start = start if saved_start is None else saved_start
                saved_start = None
                obj = ImportBlock(tokens[real_start:end])
            elif ftype in {"class", "def"}:
                real_start = start if saved_start is None else saved_start
                saved_start = None
                obj = Callable(tokens[real_start:end])
                obj.type = ftype
            elif ftype == "code":
                real_start = start if saved_start is None else saved_start
                saved_start = None
                obj = Expression(tokens[real_start:end])
            else:
                assert False, "Unknown fragment type %s" % ftype
            out.append(obj)
            i += 1
        return out



#-----------------------------------------------------------------------------------------------------------------------
# ImportBlock class
#-----------------------------------------------------------------------------------------------------------------------

class ImportBlock(ParsedBase):
    """
    A block of `import ...` statements.

    This block may contain an optional Comment at the beginning, and then one or more :class:`ImportExpr`essions. The
    type of this block can be one of: "future", "stdlib", "third-party", "first-party" or "mixed".
    """

    def _parse(self):
        out = []
        tokens = self._tokens
        i = 0
        # Parse the initial Comment section
        while tokens[i].op == COMMENT and tokens[i + 1].op == NL:
            i += 2
        if i > 0:
            out.append(Comment(tokens[:i]))
        # Parse the remaining import instructions
        import_types = set()
        while i < len(tokens):
            assert tokens[i].op == NAME and tokens[i].str in {"import", "from"},\
                "Unexpected token in ImportBlock: %r" % tokens[i]
            i0 = i
            # Find the statement's end
            while tokens[i].op != NEWLINE:
                i += 1
            i += 1  # consume the NEWLINE too
            # Construct the `ImportExpr` object
            expr = ImportExpr(tokens[i0:i])
            import_types.add(expr.type)
            out.append(expr)
        if len(import_types) == 1:
            self._type = list(import_types)[0]
        else:
            self._type = "mixed"
        return out


class ImportExpr(ParsedBase):
    """Single import statement, may only appear inside an :class:`ImportBlock`."""

    def __init__(self, tokens):
        super(ImportExpr, self).__init__(tokens)
        self._module_name = tokens[1].str
        if self._module_name == "__future__":
            self._type = "future"
        elif self._module_name in ImportExpr.KNOWN_STDLIB:
            self._type = "stdlib"
        elif self._module_name == "." or self._module_name in ImportExpr.KNOWN_FIRST_PARTY:
            self._type = "first-party"
        else:
            self._type = "third-party"

    KNOWN_STDLIB = {"__main__", "_dummy_thread", "_thread", "abc", "aifc", "antigravity", "argparse", "array", "ast",
                    "asynchat", "asyncore", "atexit", "audioop", "base64", "bdb", "binascii", "binhex", "bisect",
                    "builtins", "bz2", "cProfile", "calendar", "cgi", "cgitb", "chunk", "cmath", "cmd", "code",
                    "codecs", "codeop", "collections", "colorsys", "compileall", "concurrent", "configparser",
                    "contextlib", "copy", "copyreg", "crypt", "csv", "ctypes", "curses", "datetime", "dbm", "decimal",
                    "difflib", "dis", "distutils", "doctest", "dummy_threading", "email", "encodings", "errno",
                    "faulthandler", "fcntl", "filecmp", "fileinput", "fnmatch", "formatter", "fpectl", "fractions",
                    "ftplib", "functools", "gc", "getopt", "getpass", "gettext", "glob", "grp", "gzip", "hashlib",
                    "heapq", "hmac", "html", "http", "imaplib", "imghdr", "imp", "importlib", "inspect", "io",
                    "ipaddress", "itertools", "json", "keyword", "lib2to3", "linecache", "locale", "logging", "lzma",
                    "macpath", "mailbox", "mailcap", "marshal", "math", "mimetypes", "mmap", "modulefinder", "msilib",
                    "msvcrt", "multiprocessing", "netrc", "nis", "nntplib", "numbers", "operator", "optparse", "os",
                    "ossaudiodev", "parser", "pdb", "pickle", "pickletools", "pipes", "pkgutil", "platform",
                    "plistlib", "poplib", "posix", "pprint", "profile", "pstats", "pty", "pwd", "py_compile",
                    "pyclbr", "pydoc", "queue", "quopri", "random", "re", "readline", "reprlib", "resource",
                    "rlcompleter", "runpy", "sched", "select", "shelve", "shlex", "shutil", "signal", "site", "smtpd",
                    "smtplib", "sndhdr", "socket", "socketserver", "spwd", "sqlite3", "ssl", "stat", "string",
                    "stringprep", "struct", "subprocess", "sunau", "symbol", "symtable", "sys", "sysconfig", "syslog",
                    "tabnanny", "tarfile", "telnetlib", "tempfile", "termios", "test", "textwrap", "threading", "time",
                    "timeit", "this", "tkinter", "token", "tokenize", "trace", "traceback", "tty", "turtle", "types",
                    "unicodedata", "unittest", "urllib", "uu", "uuid", "venv", "warnings", "wave", "weakref",
                    "webbrowser", "winreg", "winsound", "wsgiref", "xdrlib", "xml", "xmlrpc", "zipfile", "zipimport",
                    "zlib"}
    KNOWN_FIRST_PARTY = {"h2o"}


#-----------------------------------------------------------------------------------------------------------------------
# Callable class
#-----------------------------------------------------------------------------------------------------------------------

class Callable(ParsedBase):

    def __init__(self, tokens):
        super(Callable, self).__init__(tokens)
        self._name = None

    def _parse(self):
        out = []
        tokens = self._tokens
        i = 0
        # Parse the initial Comment section
        while tokens[i].op == COMMENT:
            assert tokens[i + 1].op == NL
            i += 2
        if i > 0:
            out.append(Comment(tokens[:i]))
        # Parse the decorators section
        while tokens[i].op == OP and tokens[i].str == "@":
            i0 = i
            while tokens[i].op != NEWLINE:
                i += 1
            i += 1
            out.append(Decorator(tokens[i0:i]))
        # Parse the object declaration line
        assert tokens[i].op == NAME and tokens[i].str == self._type
        assert tokens[i + 1].op == NAME
        self._name = tokens[i + 1].str
        i0 = i
        while i < len(tokens) and tokens[i].op != INDENT:
            i += 1
        if i < len(tokens):
            assert tokens[-1].op == DEDENT
            body = Code(tokens[i + 1:-1])
            out.append(Declaration(tokens[i0:i]))
            out.append(Bident(tokens[i]))
            out.append(body)
            out.append(Bident(tokens[-1]))
            body.parse()
        else:
            # It is possible to have a single-line function / class
            out.append(Declaration(tokens[i0:]))
        return out

    @property
    def name(self):
        return self._name



#-----------------------------------------------------------------------------------------------------------------------
# Comment
#-----------------------------------------------------------------------------------------------------------------------

class Comment(ParsedBase):

    def __init__(self, tokens):
        super(Comment, self).__init__(tokens)
        self._code = None

    def __repr__(self):
        name = "Comment"
        if self._type == "banner": name = "Banner-Comment"
        elif self._type == "code": name = "Commented code"
        s = "<%s>:\n" % name
        for t in self._tokens:
            if t.op == NL: s += "\n"
            if t.op == COMMENT: s += "    %s" % t.str
        return s

    def _parse(self):
        if self._type == "banner": return
        indent = self._tokens[0].start_col
        assert all(t.start_col == indent if t.op == COMMENT else t.op == NL for t in self._tokens)
        indentstr1 = " " * indent + "# "
        indentstr2 = indentstr1[:-1]
        uncommented = ""
        for line in self.unparse().splitlines(True):
            if line.startswith(indentstr1):
                uncommented += line[indent + 2:]
            elif line.startswith(indentstr2):
                uncommented += line[indent + 1:]
            else:
                # No uniform indentation
                return
        try:
            compile(uncommented, "<string>", "exec")
            self._type = "code"
            self._code = uncommented
        except Exception:
            pass


#-----------------------------------------------------------------------------------------------------------------------
# Other ParsedBase classes
#-----------------------------------------------------------------------------------------------------------------------

class Whitespace(ParsedBase):
    def __repr__(self):
        s = " ".join("NL" if t.op == NL else "END" if t.op == ENDMARKER else "??"
                     for t in self._tokens)
        return "<Whitespace>: %s\n" % s


class Docstring(ParsedBase):
    def __repr__(self):
        assert len(self._tokens) == 2
        assert self._tokens[0].op == STRING
        assert self._tokens[1].op == NEWLINE
        s = "<DocString>:\n"
        for line in self._tokens[0].str.splitlines(True):
            s += "    %s" % line
        s += "\n"
        return s


class Bident(ParsedBase):
    """This class serves as either an Indent or a Dedent, hence the funny name."""

    def __init__(self, token):
        super(Bident, self).__init__([])
        self._type = "Indent" if token.op == INDENT else "Dedent"
        self._tokens = [token]

    def __repr__(self):
        return "<%s>\n" % self._type



class Expression(ParsedBase):
    pass

class Decorator(ParsedBase):
    pass

class Declaration(ParsedBase):
    pass
