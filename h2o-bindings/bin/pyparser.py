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

import re
import tokenize

from future.builtins import open

__all__ = []  # ("parse_text", "parse_file")



def parse_text(text):
    gen = iter(text.splitlines(True))  # True = keep newlines
    readline = gen.next if hasattr(gen, "next") else gen.__next__
    return _parse(readline)


def parse_file(filename):
    """Parse the provided file, and return ParsedCode object."""
    with open(filename, "rt", encoding="utf-8") as f:
        return _parse(f.readline)



#=======================================================================================================================
# Implementation
#=======================================================================================================================

def _parse(readline):
    """
    Parse any object through a readline interface.

    :param readline: a function that returns subsequent lines on each call.

    :returns: an instance of ``target`` class.
    """
    assert hasattr(readline, "__call__"), "`readline` should be a function"
    tokens = [Token(tok) for tok in tokenize.generate_tokens(readline)]
    _normalize_tokens(tokens)
    return ParsedCode(tokens)


def _normalize_tokens(tokens):
    r"""
    Fix tokenization of dedents / comments and correct the list of tokens in-place.

    In order to understand the rationale for this function, consider the following code example::

        def test():
            pass

        # funny function
        def test_funny():
            # not so funny
            pass

    Normally, Python will parse it as the following sequence of tokens:

        ['def', 'test', '(', ')', ':', NEWLINE, INDENT, 'pass', NEWLINE, NL, '# funny', NL, DEDENT, 'def',
         'test_funny', '(', ')', ':', NEWLINE, '# not so funny', NL, INDENT, 'pass', NEWLINE, DEDENT, END]

    The problem here is that the DEDENT token is generated not after the first 'pass' but after the comment, which
    means that if we treat INDENTs / DEDENTs as block boundaries, then the comment "belongs" to the first function.
    This is contrary to how most people would understand this code. Similarly, the second comment visually goes
    after the INDENT, not before.

    So here we attempt to modify the token stream. Looking at the offset of each comment: if it's higher or
    equal to the current indentation level, then leave the comment as-is. However if it's lower, then we insert
    DEDENT token(s) in front of this comment and ignore the DEDENT token(s) that go after the comment. The
    resulting stream of tokens generates the same source code, yet is more sensible:

        ['def', 'test', '(', ')', ':', NEWLINE, INDENT, 'pass', NEWLINE, DEDENT, NL, '# funny function', NL,
         'def', 'test_funny', '(', ')', ':', NEWLINE, INDENT, '# not so funny', NL, 'pass', NEWLINE, DEDENT, END]

    :param tokens: list of :class:`Token`s.

    :returns: the augmented list of Tokens.
    """
    # First, determine the levels of all dedents
    indents_stack = [0]  # Current stack of indent levels
    for i, tok in enumerate(tokens):
        if tok.op == tokenize.INDENT:
            indents_stack.append(tok.end_col)
        elif tok.op == tokenize.DEDENT:
            indents_stack.pop()
            tok.indent_level = indents_stack[-1]

    i = len(tokens) - 1
    while i > 0:
        tok = tokens[i]
        if (tok.op == tokenize.INDENT and i >= 2 and tokens[i - 1].op == tokenize.NL and
                tokens[i - 2].op == tokenize.COMMENT and tokens[i - 2].start_col == tok.end_col):
            tokens[i].move(-1)
            tokens[i - 2], tokens[i - 1], tokens[i] = tokens[i], tokens[i - 2], tokens[i - 1]
        elif (tok.op == tokenize.DEDENT and i >= 2 and tokens[i - 1].op == tokenize.NL and
                tokens[i - 2].op == tokenize.COMMENT and tok.start_col <= tokens[i - 2].start_col <= tok.indent_level):
            tokens[i].move(-1)
            tokens[i - 2], tokens[i - 1], tokens[i] = tokens[i], tokens[i - 2], tokens[i - 1]
            i += 2
        elif (tok.op in {tokenize.DEDENT, tokenize.INDENT} and i >= 1 and
                tokens[i - 1].op == tokenize.NL and tokens[i - 1].start_col == 0):
            tokens[i].move(-1)
            tokens[i], tokens[i - 1] = tokens[i - 1], tokens[i]
            i += 2
        i -= 1



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
        self._indent_level = None

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

    @property
    def indent_level(self):
        """
        Indent level at the end of the token.

        Initially this indent level is known for INDENT tokens only, for all other tokens it must be set
        explicitly before it can be queried.
        """
        if self.op == tokenize.INDENT:
            return self.end_col
        return self._indent_level

    @indent_level.setter
    def indent_level(self, level):
        self._indent_level = level

    def indent(self):
        """Return 1 for an INDENT token, -1 for a DEDENT token, and 0 otherwise."""
        if self.op == tokenize.INDENT: return 1
        if self.op == tokenize.DEDENT: return -1
        return 0

    def paren(self):
        """Return 1 for an OP '(' token, -1 for an OP ')' token, and 0 otherwise."""
        if self.op == tokenize.OP:
            if self.str == "(": return 1
            if self.str == ")": return -1
        return 0

    def move(self, drow, dcol=0):
        """Move the token by `drow` rows and `dcol` columns."""
        self._t = (
            self.op,
            self.str,
            (self.start_row + drow, self.start_col + dcol),
            (self.end_row + drow, self.end_col + dcol),
            self._t[4]
        )

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
# ParsedBase class
#-----------------------------------------------------------------------------------------------------------------------

class ParsedBase(object):
    def __init__(self, tokens):
        assert sum(tok.indent() for tok in tokens) == 0, "Unbalanced indentations in the token stream"
        self._tokens = tokens
        self._parsed = None
        self._type = None
        line0 = tokens[0].start_row
        for t in self._tokens:
            t.move(1 - line0)

    @property
    def type(self):
        return self._type

    @type.setter
    def type(self, val):
        self._type = val


    def parse(self):
        self._parsed = self._parse()
        assert self._parsed is None or all(isinstance(p, ParsedBase) for p in self._parsed), \
            "You should parse the tokens into a list of ParsedBase objects."

    def unparse(self):
        """Convert the parsed representation back into the source code."""
        if self._parsed:
            return self._unparse()
        else:
            return tokenize.untokenize(t.token for t in self._tokens)

    @property
    def tokens(self):
        return self._tokens

    @property
    def parsed(self):
        return self._parsed

    def _parse(self):
        # Override this method to provide meaningful parsing.
        return None

    def _unparse(self):
        # You may override this method as well, for custom unparsing
        return "".join(p.unparse() for p in self._parsed)

    def __repr__(self):
        if self._type:
            s = "<%s %s>:\n" % (self.__class__.__name__, self._type)
        else:
            s = "<%s>:\n" % self.__class__.__name__
        if self._parsed:
            for p in self._parsed:
                r = repr(p)
                for line in r.splitlines(True):
                    s += "    %s" % line
        else:
            for line in tokenize.untokenize(t.token for t in self._tokens).splitlines(True):
                s += "    %s" % line
        return s



#-----------------------------------------------------------------------------------------------------------------------
# ParsedCode class
#-----------------------------------------------------------------------------------------------------------------------

class ParsedCode(ParsedBase):

    def __init__(self, tokens):
        super(ParsedCode, self).__init__(tokens)
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

        i = 0
        while i < len(tokens):
            # Assume that we always start at the beginning of a new block
            i0 = i
            tok = tokens[i]
            fragment_type = "???"  # to be determined in the switch clause below

            if tok.op == tokenize.ENDMARKER:
                fragment_type = "end"
                i += 1
                assert i == len(tokens), "ENDMARKER token encountered before the end of the stream"

            elif tok.op == tokenize.NL:
                fragment_type = "whitespace"
                # If there are multiple whitespaces, gobble them all
                while tokens[i].op == tokenize.NL:
                    i += 1

            elif tok.op == tokenize.COMMENT:
                fragment_type = "comment"
                # Collapse multiple comment lines into a single comment fragment; but only if they are at the same
                # level of indentation.
                is_banner = False
                while tokens[i].op == tokenize.COMMENT and tokens[i].start_col == tok.start_col:
                    assert tokens[i + 1].op == tokenize.NL, "Unexpected token after a comment: %r" % tokens[i + 1]
                    s = tokens[i].str
                    if len(s) > 1 and (s == "#" + (s[1] * (len(s) - 1)) or s == "# " + (s[1] * (len(s) - 2)) or
                                       re.match(r"^#\s?[#*=-]{4,}.*?[#*=-]{4,}$", s)):
                        is_banner = True
                    i += 2
                if is_banner:
                    fragment_type = "banner-comment"

            elif (tok.op == tokenize.STRING and tokens[i + 1].op == tokenize.NEWLINE and
                  all(frag[0] == "whitespace" or frag[0] == "comment" for frag in fragments)):
                i += 2
                fragment_type = "docstring"

            elif tok.op == tokenize.OP and tok.str == "@" and tokens[i + 1].op == tokenize.NAME:
                while tokens[i].op == tokenize.OP and tokens[i].str == "@" and tokens[i + 1].op == tokenize.NAME:
                    i += 2
                    if tokens[i].op == tokenize.OP and tokens[i].str == "(":
                        level = 1
                        while level > 0:
                            i += 1
                            level += tokens[i].paren()
                        assert tokens[i].str == ")"
                        i += 1
                    assert tokens[i].op == tokenize.NEWLINE
                    i += 1
                fragment_type = "decorator"
            elif tok.op == tokenize.NAME and tok.str in {"from", "import"}:
                while tokens[i].op == tokenize.NAME and tokens[i].str in {"from", "import"}:
                    while tokens[i].op != tokenize.NEWLINE:
                        i += 1
                    i += 1  # eat the NEWLINE
                fragment_type = "import"
            elif tok.op in {tokenize.INDENT, tokenize.DEDENT, tokenize.NEWLINE}:
                for i, tok in enumerate(self._tokens):
                    print("%3d  %r" % (i, tok))
                assert False, "Unexpected token %d: %r" % (i, tok)
            else:
                while tokens[i].op != tokenize.NEWLINE:
                    i += 1
                i += 1  # skip the NEWLINE too
                if tokens[i].op == tokenize.INDENT:
                    level = 1
                    while level > 0:
                        i += 1
                        level += tokens[i].indent()
                    assert tokens[i].op == tokenize.DEDENT
                    i += 1  # consume the last DEDENT
                while tokens[i].op == tokenize.COMMENT and tokens[i].start_col > tok.start_col:
                    assert tokens[i + 1].op == tokenize.NL
                    i += 2
                if tok.op == tokenize.NAME and tok.str in {"def", "class"}:
                    fragment_type = tok.str
                else:
                    fragment_type = "code"
            assert i > i0, "Stuck at i = %d" % i
            fragments.append((fragment_type, i0, i))
            print(fragments[-1])
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
                obj = ParsedObject(tokens[real_start:end])
                obj.type = ftype
            elif ftype == "code":
                real_start = start if saved_start is None else saved_start
                saved_start = None
                obj = Expression(tokens[real_start:end])
            else:
                assert False, "Unknown fragment type %s" % ftype
            print("%d %s" % (i, obj.__class__.__name__))
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
        while tokens[i].op == tokenize.COMMENT and tokens[i + 1].op == tokenize.NL:
            i += 2
        if i > 0:
            out.append(Comment(tokens[:i]))
        # Parse the remaining import instructions
        import_types = set()
        while i < len(tokens):
            assert tokens[i].op == tokenize.NAME and tokens[i].str in {"import", "from"}, \
                "Unexpected token in ImportBlock: %r" % tokens[i]
            i0 = i
            # Find the statement's end
            while tokens[i].op != tokenize.NEWLINE:
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
# ParsedObject class
#-----------------------------------------------------------------------------------------------------------------------

class ParsedObject(ParsedBase):

    def __init__(self, tokens):
        super(ParsedObject, self).__init__(tokens)
        self._name = None

    def _parse(self):
        out = []
        tokens = self._tokens
        i = 0
        # Parse the initial Comment section
        while tokens[i].op == tokenize.COMMENT:
            assert tokens[i + 1].op == tokenize.NL
            i += 2
        if i > 0:
            out.append(Comment(tokens[:i]))
        # Parse the decorators section
        while tokens[i].op == tokenize.OP and tokens[i].str == "@":
            i0 = i
            while tokens[i].op != tokenize.NEWLINE:
                i += 1
            i += 1
            out.append(Decorator(tokens[i0:i]))
        # Parse the object declaration line
        assert tokens[i].op == tokenize.NAME and tokens[i].str == self._type
        assert tokens[i + 1].op == tokenize.NAME
        self._name = tokens[i + 1].str
        i0 = i
        while tokens[i].op != tokenize.INDENT:
            i += 1
        assert tokens[-1].op == tokenize.DEDENT
        out.append(Declaration(tokens[i0:i]))
        out.append(Bident(tokens[i]))
        out.append(ParsedCode(tokens[i + 1:-1]))
        out.append(Bident(tokens[-1]))
        # Finish parsing
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
            if t.op == tokenize.NL: s += "\n"
            if t.op == tokenize.COMMENT: s += "    %s" % t.str
        return s

    def _parse(self):
        indent = self._tokens[0].start_col
        assert all(t.start_col == indent if t.op == tokenize.COMMENT else t.op == tokenize.NL for t in self._tokens)
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
# Other Parsed* classes
#-----------------------------------------------------------------------------------------------------------------------

class Whitespace(ParsedBase):
    def __repr__(self):
        s = " ".join("NL" if t.op == tokenize.NL else "END" if t.op == tokenize.ENDMARKER else "??"
                     for t in self._tokens)
        return "<Whitespace>: %s\n" % s


class Docstring(ParsedBase):
    def __repr__(self):
        assert len(self._tokens) == 2
        assert self._tokens[0].op == tokenize.STRING
        assert self._tokens[1].op == tokenize.NEWLINE
        s = "<DocString>:\n"
        for line in self._tokens[0].str.splitlines(True):
            s += "    %s" % line
        s += "\n"
        return s


class Bident(ParsedBase):
    """This class serves as either an Indent or a Dedent, hence the funny name."""

    def __init__(self, token):
        self._type = "Indent" if token.op == tokenize.INDENT else "Dedent"
        self._parsed = None
        self._tokens = [token]
        token.move(1 - token.start_row)

    def __repr__(self):
        return "<%s>\n" % self._type

    def unparse(self):
        return ""


class Expression(ParsedBase):
    pass

class Decorator(ParsedBase):
    pass

class Declaration(ParsedBase):
    pass
