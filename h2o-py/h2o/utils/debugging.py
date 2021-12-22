# -*- encoding: utf-8 -*-
"""
Collection of utilities for debugging.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import division, print_function, absolute_import, unicode_literals

import inspect
import re
import sys
from types import ModuleType

from h2o.exceptions import H2OJobCancelled, H2OSoftError
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.compatibility import viewkeys

# Nothing to import; this module's only job is to install an exception hook for debugging.
__all__ = ()

def get_tb():
    return get_tb.tb

def err(msg=""):
    """Helper function for printing to stderr."""
    print(msg, file=sys.stderr)

# In case any other module installs its own exception hook, try to play nicely and use that when appropriate
_prev_except_hook = sys.excepthook


def _except_hook(exc_type, exc_value, exc_tb):
    """
    This is an advanced exception-handling hook function, that is designed to supercede the standard Python's
    exception handler. It offers several enhancements:
        * Clearer and more readable format for the exception message and the traceback.
        * Decorators are filtered out from the traceback (if they declare their implementation function to
          have name "decorator_invisible").
        * Local variables in all execution frames are also printed out.
        * Print out server-side stacktrace for exceptions that carry this information (e.g. H2OServerError).

    Some documentation about the objects used herein:

    "traceback" type (types.TracebackType):  -- stack traceback of an exception
        tb_frame:  execution frame object at the current level
        tb_lasti:  index of the last attempted instruction in the bytecode
        tb_lineno: line number in the source code
        tb_next:   next level in the traceback stack (towards the frame where exception occurred)

    "frame" type (types.FrameType):  -- the execution frame
        f_back: previous stack frame (toward the caller)
        f_code: code object being executed
        f_locals: dictionary of all local variables
        f_globals: dictionary of all global variables
        f_builtins: dictionary of built-in names
        f_lineno: line number
        f_lasti: current instruction being executed (index into the f_code.co_code)
        f_restricted: ?
        f_trace: (function) function called at the start of each code line (settable!)
        f_exc_type, f_exc_value, f_exc_traceback: [Py2 only] most recent exception triple

    "code" type (types.CodeType):  -- byte-compiled code
        co_name: function name
        co_argcount: number of positional arguments
        co_nlocals: number of local variables inside the function
        co_varnames: names of all local variables
        co_cellvars: names of variables referenced by nested functions
        co_freevars: names of free variables used by nested functions
        co_code: raw bytecode
        co_consts: literals used in the bytecode
        co_names: names used in the bytecode
        co_filename: name of the file that contains the function
        co_firstlineno: line number where the function starts
        co_lnotab: encoded offsets to line numbers within the bytecode
        co_stacksize: required stack size
        co_flags: function flags. Bit 2 = function uses *args; bit 3 = function uses **kwargs

    Note that when the Python code re-raises an exception (by calling `raise` without arguments, which semantically
    means "raise the exception again as if it wasn't caught"), then tb.tb_lineno will contain the line number of the
    original exception, whereas tb.tb_frame.f_lineno will be the line number where the exception was re-raised.

    :param exc_type: exception class
    :param exc_value: the exception instance object
    :param exc_tb: stacktrace at the point of the exception. The "traceback" type is actually a linked list,
                   and this object represents the beginning of this list (i.e. corresponds to the execution
                   frame of the outermost expression being evaluated). We need to walk down the list (by repeatedly
                   moving to exc_tb.tb_next) in order to find the execution frame where the actual exception occurred.
    """
    if isinstance(exc_value, H2OJobCancelled):
        return
    if isinstance(exc_value, H2OSoftError):
        _handle_soft_error(exc_type, exc_value, exc_tb)
    else:
        _prev_except_hook(exc_type, exc_value, exc_tb)

    # Everything else is disabled for now, because it generates too much output due to bugs in H2OFrame implementation
    return

    import linecache
    if not exc_tb:  # Happens on SyntaxError exceptions
        sys.__excepthook__(exc_type, exc_value, exc_tb)
        return
    get_tb.tb = exc_tb

    err("\n================================ EXCEPTION INFO ================================\n")
    if exc_type != type(exc_value):
        err("Exception type(s): %s / %s" % (exc_type, type(exc_value)))

    # If the exception contains .stacktrace attribute (representing server-side stack trace) then print it too.
    for arg in exc_value.args:
        if hasattr(arg, "stacktrace"):
            err("[SERVER STACKTRACE]")
            for line in arg.stacktrace:
                err("    %s" % line.strip())
            err()

    # Print local frames
    err("[LOCAL FRAMES]")
    err("Omitted: imported modules, class declarations, __future__ features, None-valued")
    tb = exc_tb
    while tb:
        tb_line = tb.tb_lineno
        frame = tb.tb_frame
        frame_file = frame.f_code.co_filename
        frame_func = frame.f_code.co_name
        frame_locl = frame.f_locals
        tb = tb.tb_next
        if frame_func == "decorator_invisible": continue
        if frame_func == "__getattribute__": continue
        if not frame_locl: continue
        err("\n  Within %s() line %s in file %s:" % (frame_func, tb_line, frame_file))
        for key in sorted(viewkeys(frame_locl), reverse=True):
            if key.startswith("__") and key.endswith("__"): continue
            value = frame_locl[key]
            if value is None: continue  # do not print uninitialized variables
            if hasattr(value, "__class__"):
                if value.__class__ is ModuleType: continue  # omit imported modules
                if value.__class__ is type: continue  # omit class declarations (new-style classes only)
                if value.__class__ is print_function.__class__: continue  # omit __future__ declarations
            try:
                strval = str(value)
                n_lines = strval.count("\n")
                if n_lines > 1:
                    strval = "%s... (+ %d line%s)" % (strval[:strval.index("\n")], n_lines - 1,
                                                      "s" if n_lines > 2 else "")
                err("%25s: %s" % (key, strval))
            except:
                err("%25s: <UNABLE TO PRINT VALUE>" % key)
    err()

    # Print the traceback
    err("[STACKTRACE]")
    last_file = None
    tb = exc_tb
    prev_info = None
    skip_frames = 0
    while tb:
        tb_lineno = tb.tb_lineno
        frame = tb.tb_frame
        frame_file = frame.f_code.co_filename
        frame_func = frame.f_code.co_name
        frame_glob = frame.f_globals
        tb = tb.tb_next
        if frame_func == "decorator_invisible": continue
        if frame_func == "__getattribute__": continue
        if (tb_lineno, frame_file) == prev_info:
            skip_frames += 1
            continue
        else:
            if skip_frames:
                err("    %20s   ... +%d nested calls" % ("", skip_frames))
            skip_frames = 0
            prev_info = (tb_lineno, frame_file)

        if frame_file != last_file:
            last_file = frame_file
            err("\n  File %s:" % frame_file)
        line_txt = linecache.getline(frame_file, tb_lineno, frame_glob)
        err("    %20s() #%04d  %s" % (frame_func, tb_lineno, line_txt.strip()))
    if skip_frames:
        err("    %20s   ... +%d nested calls" % ("", skip_frames))
    err()

    # Print the exception message
    tb = exc_tb
    while tb.tb_next:
        tb = tb.tb_next
    print("[EXCEPTION]", file=sys.stderr)
    print("  %s: %s" % (exc_value.__class__.__name__, str(exc_value)), file=sys.stderr)
    print("  at line %d in %s\n" % (tb.tb_lineno, tb.tb_frame.f_code.co_filename), file=sys.stderr)

    # There was a warning in {https://docs.python.org/2/library/sys.html} that storing traceback object in a local
    # variable may cause a circular reference; so we explicitly delete these vars just in case.
    del tb
    del exc_tb


# Install the enhanced exception hook into the system.
# The original exception hook is stored at sys.__excepthook__, and it will get called if our custom exception
# handling function itself raises an exception.
sys.excepthook = _except_hook


def _handle_soft_error(exc_type, exc_value, exc_tb):
    err("%s: %s" % (exc_type.__name__, exc_value))

    # Convert to the list of frames
    tb = exc_tb
    frames = []
    while tb:
        frames.append(tb.tb_frame)
        tb = tb.tb_next

    i0 = len(frames) - 1 - getattr(exc_value, "skip_frames", 0)
    indent = " " * (len(exc_type.__name__) + 2)
    for i in range(i0, 0, -1):
        co = frames[i].f_code
        func = _find_function_from_code(frames[i - 1], co)
        fullname = _get_method_full_name(func) if func else "???." + co.co_name
        highlight = getattr(exc_value, "var_name", None) if i == i0 else None
        args_str = _get_args_str(func, highlight=highlight)
        indent_len = len(exc_type.__name__) + len(fullname) + 6
        line = indent + ("in " if i == i0 else "   ")
        line += fullname + "("
        line += _wrap(args_str + ") line %d" % frames[i].f_lineno, indent=indent_len)
        err(line)
    err()


def _get_method_full_name(func):
    """
    Return fully qualified function name.

    This method will attempt to find "full name" of the given function object. This full name is either of
    the form "<class name>.<method name>" if the function is a class method, or "<module name>.<func name>"
    if it's a regular function. Thus, this is an attempt to back-port func.__qualname__ to Python 2.

    :param func: a function object.

    :returns: string with the function's full name as explained above.
    """
    # Python 3.3 already has this information available...
    if hasattr(func, "__qualname__"): return func.__qualname__

    module = inspect.getmodule(func)
    if module is None:
        return "?.%s" % getattr(func, "__name__", "?")
    for cls_name in dir(module):
        cls = getattr(module, cls_name)
        if not inspect.isclass(cls): continue
        for method_name in dir(cls):
            cls_method = getattr(cls, method_name)
            if cls_method == func:
                return "%s.%s" % (cls_name, method_name)
    if hasattr(func, "__name__"):
        return "%s.%s" % (module.__name__, func.__name__)
    return "<unknown>"


def _find_function_from_code(frame, code):
    """
    Given a frame and a compiled function code, find the corresponding function object within the frame.

    This function addresses the following problem: when handling a stacktrace, we receive information about
    which piece of code was being executed in the form of a CodeType object. That objects contains function name,
    file name, line number, and the compiled bytecode. What it *doesn't* contain is the function object itself.

    So this utility function aims at locating this function object, and it does so by searching through objects
    in the preceding local frame (i.e. the frame where the function was called from). We expect that the function
    should usually exist there -- either by itself, or as a method on one of the objects.

    :param types.FrameType frame: local frame where the function ought to be found somewhere.
    :param types.CodeType code: the compiled code of the function to look for.

    :returns: the function object, or None if not found.
    """
    def find_code(iterable, depth=0):
        if depth > 3: return  # Avoid potential infinite loops, or generally objects that are too deep.
        for item in iterable:
            if item is None: continue
            found = None
            if hasattr(item, "__code__") and ((code.__eq__(item.__code__)) is True):
                found = item
            elif isinstance(item, type) or isinstance(item, ModuleType):  # class / module
                try:
                    found = find_code((getattr(item, n, None) for n in dir(item)), depth + 1)
                except Exception:
                    # Sometimes merely getting module's attributes may cause an exception. For example :mod:`six.moves`
                    # is such an offender...
                    continue
            elif isinstance(item, (list, tuple, set)):
                found = find_code(item, depth + 1)
            elif isinstance(item, dict):
                found = find_code(item.values(), depth + 1)
            if found: return found
    return find_code(frame.f_locals.values()) or find_code(frame.f_globals.values())


def _get_args_str(func, highlight=None):
    """
    Return function's declared arguments as a string.

    For example for this function it returns "func, highlight=None"; for the ``_wrap`` function it returns
    "text, wrap_at=120, indent=4". This should usually coincide with the function's declaration (the part
    which is inside the parentheses).
    """
    if not func: return ""
    s = str(inspect.signature(func))[1:-1]
    if highlight:
        s = re.sub(r"\b%s\b" % highlight, "**%s**" % highlight, s)
    return s


def _wrap(text, wrap_at=120, indent=4):
    """
    Return piece of text, wrapped around if needed.

    :param text: text that may be too long and then needs to be wrapped.
    :param wrap_at: the maximum line length.
    :param indent: number of spaces to prepend to all subsequent lines after the first.
    """
    out = ""
    curr_line_length = indent
    space_needed = False
    for word in text.split():
        if curr_line_length + len(word) > wrap_at:
            out += "\n" + " " * indent
            curr_line_length = indent
            space_needed = False
        if space_needed:
            out += " "
            curr_line_length += 1
        out += word
        curr_line_length += len(word)
        space_needed = True
    return out
