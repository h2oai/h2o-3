# -*- encoding: utf-8 -*-
#
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
from __future__ import division, print_function, absolute_import, unicode_literals
# noinspection PyUnresolvedReferences
from .compatibility import *  # NOQA
import sys
from types import ModuleType

# Nothing to import; this module's only job is to install an exception hook for debugging.
__all__ = []



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
    import linecache
    if not exc_tb:  # Happens on SyntaxError exceptions
        sys.__excepthook__(exc_type, exc_value, exc_tb)
        return

    # Helper function for printing to stderr
    def err(msg=""):
        print(msg, file=sys.stderr)

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
        for key in sorted(frame_locl.keys(), reverse=True):
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


# Replace __repr__ function on str so that it produces double-quoted strings instead of single-quoted. This makes it
# easier to spot the difference between old-style strings of type 'str' and 'unicode', and new-style strings of type
# "newstr". This replacement only happens on Py2, since in Py3 we use native str type throughout.
if str("").__class__.__name__ == "newstr":
    def _new_str_repr(self):
        value = super(str, self).__repr__()
        return '"' + value[2:-1].replace("\\'", "'").replace('"', '\\"') + '"'

    str("").__class__.__repr__ = _new_str_repr
