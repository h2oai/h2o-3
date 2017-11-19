#!/usr/bin/env python
# -*- encoding: utf-8 -*-
#
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
"""Shared utilities used by various classes, all placed here to avoid circular imports.

This file INTENTIONALLY has NO module dependencies!
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import imp
import itertools
import os
import re
import sys
import subprocess
import json

from h2o.exceptions import H2OValueError
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.typechecks import assert_is_type, is_type, numeric
from h2o.backend.server import H2OLocalServer

_id_ctr = 0

# The set of characters allowed in frame IDs. Since frame ids are used within REST API urls, they may
# only contain characters allowed within the "segment" part of the URL (see RFC 3986). Additionally, we
# forbid all characters that are declared as "illegal" in Key.java.
_id_allowed_characters = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")

__all__ = ("predict_json", )


def _py_tmp_key(append):
    global _id_ctr
    _id_ctr += 1
    return "py_" + str(_id_ctr) + append


def check_frame_id(frame_id):
    """Check that the provided frame id is valid in Rapids language."""
    if frame_id is None:
        return
    if frame_id.strip() == "":
        raise H2OValueError("Frame id cannot be an empty string: %r" % frame_id)
    for i, ch in enumerate(frame_id):
        # '$' character has special meaning at the beginning of the string; and prohibited anywhere else
        if ch == "$" and i == 0: continue
        if ch not in _id_allowed_characters:
            raise H2OValueError("Character '%s' is illegal in frame id: %s" % (ch, frame_id))
    if re.match(r"-?[0-9]", frame_id):
        raise H2OValueError("Frame id cannot start with a number: %s" % frame_id)



def temp_ctr():
    return _id_ctr


def can_use_pandas():
    try:
        imp.find_module('pandas')
        return True
    except ImportError:
        return False


def can_use_numpy():
    try:
        imp.find_module('numpy')
        return True
    except ImportError:
        return False


_url_safe_chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
_url_chars_map = [chr(i) if chr(i) in _url_safe_chars else "%%%02X" % i for i in range(256)]

def url_encode(s):
    # Note: type cast str(s) will not be needed once all code is made compatible
    return "".join(_url_chars_map[c] for c in bytes_iterator(s))

def quote(s):
    return url_encode(s)


def urlopen():
    if PY3:
        from urllib import request
        return request.urlopen
    else:
        import urllib2
        return urllib2.urlopen

def clamp(x, xmin, xmax):
    """Return the value of x, clamped from below by `xmin` and from above by `xmax`."""
    return max(xmin, min(x, xmax))

def _gen_header(cols):
    return ["C" + str(c) for c in range(1, cols + 1, 1)]


def _check_lists_of_lists(python_obj):
    # check we have a lists of flat lists
    # returns longest length of sublist
    most_cols = 0
    for l in python_obj:
        # All items in the list must be a list!
        if not isinstance(l, (tuple, list)):
            raise ValueError("`python_obj` is a mixture of nested lists and other types.")
        most_cols = max(most_cols, len(l))
        for ll in l:
            # in fact, we must have a list of flat lists!
            if isinstance(ll, (tuple, list)):
                raise ValueError("`python_obj` is not a list of flat lists!")
    return most_cols


def _handle_python_lists(python_obj, check_header):
    # convert all inputs to lol
    if _is_list_of_lists(python_obj):  # do we have a list of lists: [[...], ..., [...]] ?
        ncols = _check_lists_of_lists(python_obj)  # must be a list of flat lists, raise ValueError if not
    elif isinstance(python_obj, (list, tuple)):  # single list
        ncols = 1
        python_obj = [[e] for e in python_obj]
    else:  # scalar
        python_obj = [[python_obj]]
        ncols = 1
    # create the header
    if check_header == 1:
        header = python_obj[0]
        python_obj = python_obj[1:]
    else:
        header = _gen_header(ncols)
    # shape up the data for csv.DictWriter
    # data_to_write = [dict(list(zip(header, row))) for row in python_obj]
    return header, python_obj


def stringify_list(arr):
    return "[%s]" % ",".join(stringify_list(item) if isinstance(item, list) else str(item)
                             for item in arr)


def _is_list(l):
    return isinstance(l, (tuple, list))


def _is_str_list(l):
    return is_type(l, [str])


def _is_num_list(l):
    return is_type(l, [numeric])


def _is_list_of_lists(o):
    return any(isinstance(l, (tuple, list)) for l in o)


def _handle_numpy_array(python_obj, header):
    return _handle_python_lists(python_obj.tolist(), header)


def _handle_pandas_data_frame(python_obj, header):
    data = _handle_python_lists(python_obj.as_matrix().tolist(), -1)[1]
    return list(python_obj.columns), data

def _handle_python_dicts(python_obj, check_header):
    header = list(python_obj.keys())
    is_valid = all(re.match(r"^[a-zA-Z_][a-zA-Z0-9_.]*$", col) for col in header)  # is this a valid header?
    if not is_valid:
        raise ValueError(
            "Did not get a valid set of column names! Must match the regular expression: ^[a-zA-Z_][a-zA-Z0-9_.]*$ ")
    for k in python_obj:  # check that each value entry is a flat list/tuple or single int, float, or string
        v = python_obj[k]
        if isinstance(v, (tuple, list)):  # if value is a tuple/list, then it must be flat
            if _is_list_of_lists(v):
                raise ValueError("Values in the dictionary must be flattened!")
        elif is_type(v, str, numeric):
            python_obj[k] = [v]
        else:
            raise ValueError("Encountered invalid dictionary value when constructing H2OFrame. Got: {0}".format(v))

    zipper = getattr(itertools, "zip_longest", None) or getattr(itertools, "izip_longest", None) or zip
    rows = list(map(list, zipper(*list(python_obj.values()))))
    data_to_write = [dict(list(zip(header, row))) for row in rows]
    return header, data_to_write


def _is_fr(o):
    return o.__class__.__name__ == "H2OFrame"  # hack to avoid circular imports


def _quoted(key):
    if key is None: return "\"\""
    # mimic behavior in R to replace "%" and "&" characters, which break the call to /Parse, with "."
    # key = key.replace("%", ".")
    # key = key.replace("&", ".")
    is_quoted = len(re.findall(r'\"(.+?)\"', key)) != 0
    key = key if is_quoted else '"' + key + '"'
    return key


def _locate(path):
    """Search for a relative path and turn it into an absolute path.
    This is handy when hunting for data files to be passed into h2o and used by import file.
    Note: This function is for unit testing purposes only.

    Parameters
    ----------
    path : str
      Path to search for

    :return: Absolute path if it is found.  None otherwise.
    """

    tmp_dir = os.path.realpath(os.getcwd())
    possible_result = os.path.join(tmp_dir, path)
    while True:
        if os.path.exists(possible_result):
            return possible_result

        next_tmp_dir = os.path.dirname(tmp_dir)
        if next_tmp_dir == tmp_dir:
            raise ValueError("File not found: " + path)

        tmp_dir = next_tmp_dir
        possible_result = os.path.join(tmp_dir, path)


def _colmean(column):
    """Return the mean of a single-column frame."""
    assert column.ncols == 1
    return column.mean(return_frame=True).flatten()


def get_human_readable_bytes(size):
    """
    Convert given number of bytes into a human readable representation, i.e. add prefix such as kb, Mb, Gb,
    etc. The `size` argument must be a non-negative integer.

    :param size: integer representing byte size of something
    :return: string representation of the size, in human-readable form
    """
    if size == 0: return "0"
    if size is None: return ""
    assert_is_type(size, int)
    assert size >= 0, "`size` cannot be negative, got %d" % size
    suffixes = "PTGMk"
    maxl = len(suffixes)
    for i in range(maxl + 1):
        shift = (maxl - i) * 10
        if size >> shift == 0: continue
        ndigits = 0
        for nd in [3, 2, 1]:
            if size >> (shift + 12 - nd * 3) == 0:
                ndigits = nd
                break
        if ndigits == 0 or size == (size >> shift) << shift:
            rounded_val = str(size >> shift)
        else:
            rounded_val = "%.*f" % (ndigits, size / (1 << shift))
        return "%s %sb" % (rounded_val, suffixes[i] if i < maxl else "")


def get_human_readable_time(time_ms):
    """
    Convert given duration in milliseconds into a human-readable representation, i.e. hours, minutes, seconds,
    etc. More specifically, the returned string may look like following:
        1 day 3 hours 12 mins
        3 days 0 hours 0 mins
        8 hours 12 mins
        34 mins 02 secs
        13 secs
        541 ms
    In particular, the following rules are applied:
        * milliseconds are printed only if the duration is less than a second;
        * seconds are printed only if the duration is less than an hour;
        * for durations greater than 1 hour we print days, hours and minutes keeping zeros in the middle (i.e. we
          return "4 days 0 hours 12 mins" instead of "4 days 12 mins").

    :param time_ms: duration, as a number of elapsed milliseconds.
    :return: human-readable string representation of the provided duration.
    """
    millis = time_ms % 1000
    secs = (time_ms // 1000) % 60
    mins = (time_ms // 60000) % 60
    hours = (time_ms // 3600000) % 24
    days = (time_ms // 86400000)

    res = ""
    if days > 1:
        res += "%d days" % days
    elif days == 1:
        res += "1 day"

    if hours > 1 or (hours == 0 and res):
        res += " %d hours" % hours
    elif hours == 1:
        res += " 1 hour"

    if mins > 1 or (mins == 0 and res):
        res += " %d mins" % mins
    elif mins == 1:
        res += " 1 min"

    if days == 0 and hours == 0:
        res += " %02d secs" % secs
    if not res:
        res = " %d ms" % millis

    return res.strip()


def print2(msg, flush=False, end="\n"):
    """
    This function exists here ONLY because Sphinx.ext.autodoc gets into a bad state when seeing the print()
    function. When in that state, autodoc doesn't display any errors or warnings, but instead completely
    ignores the "bysource" member-order option.
    """
    print(msg, end=end)
    if flush: sys.stdout.flush()


def normalize_slice(s, total):
    """
    Return a "canonical" version of slice ``s``.

    :param slice s: the original slice expression
    :param total int: total number of elements in the collection sliced by ``s``
    :return slice: a slice equivalent to ``s`` but not containing any negative indices or Nones.
    """
    newstart = 0 if s.start is None else max(0, s.start + total) if s.start < 0 else min(s.start, total)
    newstop = total if s.stop is None else max(0, s.stop + total) if s.stop < 0 else min(s.stop, total)
    newstep = 1 if s.step is None else s.step
    return slice(newstart, newstop, newstep)


def slice_is_normalized(s):
    """Return True if slice ``s`` in "normalized" form."""
    return (s.start is not None and s.stop is not None and s.step is not None and s.start <= s.stop)


gen_header = _gen_header
py_tmp_key = _py_tmp_key
locate = _locate
quoted = _quoted
is_list = _is_list
is_fr = _is_fr
handle_python_dicts = _handle_python_dicts
handle_pandas_data_frame = _handle_pandas_data_frame
handle_numpy_array = _handle_numpy_array
is_list_of_lists = _is_list_of_lists
is_num_list = _is_num_list
is_str_list = _is_str_list
handle_python_lists = _handle_python_lists
check_lists_of_lists = _check_lists_of_lists

gen_model_file_name = "h2o-genmodel.jar"
h2o_predictor_class = "water.util.H2OPredictor"

def find_file(name, path):
    """
    Helper function for predict_json() function to check if both MOJO and h2o-genmodel.jar are available
    :param name: file name to find
    :param path: folder/directory name with full path
    :return: Successful path name or None if failed
    """
    for root, dirs, files in os.walk(path):
        if name in files:
            return os.path.join(root, name)

def check_json(json_str):
    try:
        json_object = json.loads(json_str)
    except ValueError:
        raise RuntimeError("Error: Given JSON string does not look like valid JSON string.")
    return True

def predict_json(mojo_model, json, genmodelpath=None, labels=False, classpath=None, javaoptions='-Xmx4g', show_debug=False):
    """
    MOJO scoring function to take a pandas data frame as json string and use MOJO model as zip file to score

    :param mojo_model: This is the MOJO model file name which is download after model was build using H2O, you can pass it two ways:
           1. Full mojo model path i.e. /Users/avkashchauhan/src/github.com/h2oai/h2o-tutorials/tutorials/python_mojo_scoring/gbm_prostate_new.zip
           2. mojo name first i.e. gbm_prostate_new.zip and genmodelpath = /Users/avkashchauhan/src/github.com/h2oai/h2o-tutorials/tutorials/python_mojo_scoring
           3. only mojo model name: In this case system uses os.path.abspath to get the folder where mojo is and then look for h2o-genmodel.jar in the same location
    :param json:  convert pandas frame to json (pd.to_json) and pass here
    :param genmodelpath: This is the local file system folder name where both h2o-genmodel.jar and MOJO zip file is stored.
    :param labels:     (Optional) True : Shows results, False: Does no show results and just pass result to given object
    :param javaoptions: (Optional) These are the Java string options given by the user
    :param show_debug: True/False - Default ( Use True to see the full Java command line)
    :return: score as json values
    """
    # Verifying JSON
    if show_debug:
        print(json)

    check_json(json)

    # Checking java
    java = H2OLocalServer._find_java()

    jver_bytes = subprocess.check_output([java, "-version"], stderr=subprocess.STDOUT)
    jver = jver_bytes.decode(encoding="utf-8", errors="ignore")
    if show_debug:
        print("  Java Version: " + jver.strip().replace("\n", "; "))
    if "GNU libgcj" in jver:
        raise H2OStartupError("Sorry, GNU Java is not supported for H2O.\n"
                              "Please download the latest 64-bit Java SE JDK from Oracle.")
    """
    if "Client VM" in jver:
        warn("  You have a 32-bit version of Java. H2O works best with 64-bit Java.\n"
             "  Please download the latest 64-bit Java SE JDK from Oracle.\n")
    # genmodelpath > must start and ends with "/" or add to it

    if sys.platform == "win32":
        separator = ";"
    else:
        separator = ":"
    """

    separator = os.pathsep
    file_separator = os.sep

    gen_model_arg = "." + separator

    ## Working on mojo_model
    mojo_model_path=None

    head, tail = os.path.split(mojo_model)
    if not head:
        if not genmodelpath:
            # taking path from asbpath, which is based on given mojo model file name
            head, tail = os.path.split(os.path.abspath(mojo_model))
            mojo_model_path = head
        else:
            # taking path from given genmodelpath
            mojo_model_path = genmodelpath
    else:
        # Taking path from the path given with model mojo file
        mojo_model_path = head

    if not mojo_model_path:
        raise RuntimeError("Error: given mojo model " + mojo_model + " does not have full path!")

    # mojo model name is
    mojo_model_name = tail

    # Mojo model is given by name only and separate path is given also
    # Check both genmodel and pojo is available in the same path
    mojo_test = find_file(mojo_model_name, mojo_model_path)
    if mojo_test is None:
        raise RuntimeError("Error: MOJO model " + mojo_model_name + " is not available in " + mojo_model_path + " folder.")


    genmodel_test = find_file(gen_model_file_name, mojo_model_path)
    if genmodel_test is None:
        raise RuntimeError("Error:" + gen_model_file_name +  " is not available in " + mojo_model_path + " folder.")


    temp_dir_path =  mojo_model_path

    if mojo_model_path.endswith(file_separator):
        gen_model_arg = gen_model_arg + mojo_model_path
        temp_dir_path = mojo_model_path[:-1]
    else:
        gen_model_arg = gen_model_arg + mojo_model_path + file_separator

    gen_model_arg = (gen_model_arg
                     + gen_model_file_name + separator
                     + temp_dir_path
                     + separator + "genmodel.jar" + separator + file_separator)

    if (show_debug):
        print(gen_model_arg)

    if mojo_model_path.endswith(file_separator):
        mojo_model_args = mojo_model_path + mojo_model_name
    else:
        mojo_model_args = mojo_model_path +  file_separator + mojo_model_name

    if show_debug:
        print(mojo_model_args)

    if classpath:
        gen_model_arg = classpath

    result_output = subprocess.check_output([java , javaoptions, "-cp", gen_model_arg, h2o_predictor_class,
                                             mojo_model_args, json], shell=False).decode()

    if labels:
        print(result_output)

    return result_output


def deprecated(message):
    """The decorator to mark deprecated functions."""
    from traceback import extract_stack
    assert message, "`message` argument in @deprecated is required."

    def deprecated_decorator(fun):
        def decorator_invisible(*args, **kwargs):
            stack = extract_stack()
            assert len(stack) >= 2 and stack[-1][2] == "decorator_invisible", "Got confusing stack... %r" % stack
            print("[WARNING] in %s line %d:" % (stack[-2][0], stack[-2][1]))
            print("    >>> %s" % (stack[-2][3] or "????"))
            print("        ^^^^ %s" % message)
            return fun(*args, **kwargs)

        decorator_invisible.__doc__ = message
        decorator_invisible.__name__ = fun.__name__
        decorator_invisible.__module__ = fun.__module__
        decorator_invisible.__deprecated__ = True
        return decorator_invisible

    return deprecated_decorator
