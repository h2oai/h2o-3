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
import zipfile
import io
import string
import subprocess
import csv
import shutil
import tempfile

from h2o.exceptions import H2OValueError
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.typechecks import assert_is_type, is_type, numeric
from h2o.backend.server import H2OLocalServer

_id_ctr = 0

# The set of characters allowed in frame IDs. Since frame ids are used within REST API urls, they may
# only contain characters allowed within the "segment" part of the URL (see RFC 3986). Additionally, we
# forbid all characters that are declared as "illegal" in Key.java.
_id_allowed_characters = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")

__all__ = ('mojo_predict_csv', 'mojo_predict_pandas')


def _py_tmp_key(append):
    """
    :examples:

    >>> from h2o.utils.shared_utils import _py_tmp_key
    >>> h2o.init()
    >>> _py_tmp_key(append=h2o.connection().session_id)
    """
    global _id_ctr
    _id_ctr += 1
    return "py_" + str(_id_ctr) + append


def check_frame_id(frame_id):
    check_id(frame_id, "H2OFrame")
    """
    :examples:

    >>> from h2o.utils.shared_utils import check_frame_id
    >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
    >>> airlines.frame_id
    >>> check_frame_id('AirlinesTrain.hex')
    """


def check_id(id, type):
    """Check that the provided id is valid in Rapids language."""
    if id is None:
        return
    if id.strip() == "":
        raise H2OValueError("%s id cannot be an empty string: %r" % (type, id))
    for i, ch in enumerate(id):
        # '$' character has special meaning at the beginning of the string; and prohibited anywhere else
        if ch == "$" and i == 0: continue
        if ch not in _id_allowed_characters:
            raise H2OValueError("Character '%s' is illegal in %s id: %s" % (ch, type, id))
    if re.match(r"-?[0-9]", id):
        raise H2OValueError("%s id cannot start with a number: %s" % (type, id))
    """
    :examples:

    >>> from h2o.utils.shared_utils import check_id
    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv")
    >>> gbm = H2OGradientBoostingEstimator(ntrees=1)
    >>> gbm.train(x=["Origin","Dest"],
    ...           y="IsDepDelayed",
    ...           training_frame=airlines)
    >>> id = gbm.model_id
    >>> check_id(id, "model_id")
    """


def temp_ctr():

    """
    :examples:

    >>> from h2o.utils.shared_utils import temp_ctr
    >>> h2o.init()
    >>> temp_ctr()
    """
    return _id_ctr


def can_use_pandas():
    try:
        imp.find_module('pandas')
        return True
    except ImportError:
        return False
    """
    :examples:

    >>> from h2o.utils.shared_utils import can_use_pandas
    >>> h2o.init()
    >>> can_use_pandas()
    """


def can_use_numpy():
    try:
        imp.find_module('numpy')
        return True
    except ImportError:
        return False
    """
    :examples:

    >>> from h2o.utils.shared_utils import can_use_numpy
    >>> h2o.init()
    >>> can_use_numpy()
    """


_url_safe_chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
_url_chars_map = [chr(i) if chr(i) in _url_safe_chars else "%%%02X" % i for i in range(256)]

def url_encode(s):
    # Note: type cast str(s) will not be needed once all code is made compatible
    """
    :examples:

    >>> from h2o.utils.shared_utils import url_encode
    >>> url_encode("https://h2o.ai")
    """
    return "".join(_url_chars_map[c] for c in bytes_iterator(s))

def quote(s):
    """
    :examples:

    >>> from h2o.utils.shared_utils import quote, url_encode
    >>> water = url_encode("https://h2o.ai")
    >>> quote(water)
    """
    return url_encode(s)

def clamp(x, xmin, xmax):
    """Return the value of x, clamped from below by `xmin` and from above by `xmax`.

    :examples:

    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>> from h2o.utils.shared_utils import clamp
    >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    >>> response = "class"
    >>> predictors = ["sepal_len","sepal_wid","petal_len","petal_wid"]
    >>> gbm_iris = H2OGradientBoostingEstimator()
    >>> gbm_iris.train(x=predictors, y=response, training_frame=iris)
    >>> mse = gbm_iris.mse()
    >>> clamp(mse, 0, 0.0028)
    """
    return max(xmin, min(x, xmax))

def _gen_header(cols):
    """
    :examples:

    >>> from h2o.utils.shared_utils import _gen_header
    >>> _gen_header(5)
    """
    return ["C" + str(c) for c in range(1, cols + 1, 1)]


def _check_lists_of_lists(python_obj):
    # check we have a lists of flat lists
    # returns longest length of sublist
    most_cols = 1
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
    """
    :examples:

    >>> from h2o.utils.shared_utils import _check_lists_of_lists
    >>> list = [[1,2,3], [4,5,6], [7,6,5], [7], [2,3,4,5]]
    >>> _check_lists_of_lists(list)
    """


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
    """
    :examples:

    >>> from h2o.utils.shared_utils import _handle_python_lists
    >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    >>> _handle_python_lists(iris["sepal_len"], "sepal_len")
    """


def stringify_dict(d):
    return stringify_list(["{'key': %s, 'value': %s}" % (_quoted(k), v) for k, v in d.items()])
    """
    :examples:

    >>> from h2o.utils.shared_utils import stringify_dict
    >>> MLB_TEAM = {
    ...     'Colorado' : 'Rockies',
    ...     'Boston'   : 'Red Sox',
    ...     'Minnesota': 'Twins',
    ...     'Milwaukee': 'Brewers',
    ...     'Seattle'  : 'Mariners'
    ... }
    >>> stringify_dict(MLB_TEAM)
    """

def stringify_list(arr):
    return "[%s]" % ",".join(stringify_list(item) if isinstance(item, list) else _str(item)
                             for item in arr)
    """
    :examples:

    >>> from h2o.utils.shared_utils import stringify_list
    >>> list = [[1,2,3], [4,5,6], [7,6,5]]
    >>> stringify_list(list)
    """
    
def _str(item):
    return _str_tuple(item) if isinstance(item, tuple) else str(item)
    """
    :examples:

    >>> from h2o.utils.shared_utils import _str
    >>> list = [[1,2,3], [4,5,6], [7,6,5]]
    >>> _str(list)
    """

def _str_tuple(t):
    return "{%s}" % ",".join(["%s: %s" % (ti[0], str(ti[1])) for ti in zip(list(string.ascii_lowercase), t)])
    """
    :examples:

    >>> from h2o.utils.shared_utils import _str_tuple
    >>> list = [[1,2,3], [4,5,6], [7,6,5]]
    >>> _str_tuple(list)
    """
    
def _is_list(l):
    return isinstance(l, (tuple, list))
    """
    :examples:

    >>> from h2o.utils.shared_utils import _is_list
    >>> list = [[1,2,3], [4,5,6], [7,6,5]]
    >>> _is_list(list)
    """


def _is_str_list(l):
    return is_type(l, [str])
    """
    :examples:

    >>> from h2o.utils.shared_utils import _is_str_list
    >>> fruit = ['apple','peach','pear']
    >>> _is_str_list(fruit)
    """


def _is_num_list(l):
    return is_type(l, [numeric])
    """
    :examples:

    >>> from h2o.utils.shared_utils import _is_num_list
    >>> num = [2,4,6,8]
    >>> _is_num_list(num)
    """


def _is_list_of_lists(o):
    return any(isinstance(l, (tuple, list)) for l in o)
    """
    :examples:

    >>> from h2o.utils.shared_utils import _is_list_of_lists
    >>> list = [[1,2,3], [4,5,6], [7,6,5]]
    >>> _is_list_of_lists(list)
    """


def _handle_numpy_array(python_obj, header):
    return _handle_python_lists(python_obj.tolist(), header)
    """
    :examples:

    >>> import numpy as np
    >>> from h2o.utils.shared_utils import _handle_numpy_array
    >>> m = 1000
    >>> n = 100
    >>> k = 10
    >>> y = np.random.rand(k,n)
    >>> x = np.random.rand(m,k)
    >>> train = np.dot(x,y)
    >>> train_h2o = h2o.H2OFrame(train.tolist())
    >>> _handle_numpy_array(train, "C1")
    """


def _handle_pandas_data_frame(python_obj, header):
    data = _handle_python_lists(python_obj.values.tolist(), -1)[1]
    return list(str(c) for c in python_obj.columns), data
    """
    :examples:

    >>> import pandas as pd
    >>> from h2o.utils.shared_utils import _handle_pandas_data_frame
    >>> python_obj = pd.DataFrame({'foo' : pd.Series([1]),
    ...                            'bar' : pd.Series([6]),
    ...                            'baz' : pd.Series(["a"]) })
    >>> _handle_pandas_data_frame(python_obj, "foo")
    """

def _handle_python_dicts(python_obj, check_header):
    header = list(python_obj.keys()) if python_obj else _gen_header(1)
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
    """
    :examples:

    >>> from h2o.utils.shared_utils import _handle_python_dicts
    >>> MLB_TEAM = {
    ...     'Colorado' : 'Rockies',
    ...     'Boston'   : 'Red Sox',
    ...     'Minnesota': 'Twins',
    ...     'Milwaukee': 'Brewers',
    ...     'Seattle'  : 'Mariners'
    ... }
    >>> _handle_python_dicts(MLB_TEAM, check_header=1)
    """


def _is_fr(o):
    return o.__class__.__name__ == """H2OFrame

    :examples:

    >>> from h2o.utils.shared_utils import _is_fr
    >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    >>> _is_fr(iris)
    """  # hack to avoid circular imports


def _quoted(key):
    if key is None: return "\"\""
    # mimic behavior in R to replace "%" and "&" characters, which break the call to /Parse, with "."
    # key = key.replace("%", ".")
    # key = key.replace("&", ".")
    is_quoted = len(re.findall(r'\"(.+?)\"', key)) != 0
    key = key if is_quoted else '"' + key + '"'
    """
    :examples:

    >>> from h2o.utils.shared_utils import _quoted
    >>> col_types = ['enum','numeric','enum','enum','enum',
    ...              'numeric','numeric','numeric']
    >>> col_headers = ["CAPSULE","AGE","RACE","DPROS",
    ...                "DCAPS","PSA","VOL","GLEASON"] 
    >>> hex_key = "prostate.hex"
    >>> prostate = h2o.import_file(("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_cat.csv"), destination_frame=hex_key, header=1, sep = ',', col_names=col_headers, col_types=col_types, na_strings=["NA"])
    >>> _quoted("prostate.hex")
    """
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
    """Return the mean of a single-column frame.

    :examples:

    >>> from h2o.utils.shared_utils import _colmean
    >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    >>> _colmean(iris["sepal_len"])
    """
    assert column.ncols == 1
    return column.mean(return_frame=True).flatten()


def get_human_readable_bytes(size):
    """
    Convert given number of bytes into a human readable representation, i.e. add prefix such as kb, Mb, Gb,
    etc. The `size` argument must be a non-negative integer.

    :param size: integer representing byte size of something
    :return: string representation of the size, in human-readable form

    :examples:

    >>> from h2o.utils.shared_utils import get_human_readable_bytes
    >>> get_human_readable_bytes(1234567890)
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

    :examples:

    >>> from h2o.utils.shared_utils import get_human_readable_time
    >>> get_human_readable_time(1234567890) 
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

    :examples:

    >>> from h2o.utils.shared_utils import print2
    >>> print2("Hello World", flush=False, end="/n")
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
h2o_predictor_class = "hex.genmodel.tools.PredictCsv"


def mojo_predict_pandas(dataframe, mojo_zip_path, genmodel_jar_path=None, classpath=None, java_options=None, 
                        verbose=False, setInvNumNA=False):
    """
    MOJO scoring function to take a Pandas frame and use MOJO model as zip file to score.

    :param dataframe: Pandas frame to score.
    :param mojo_zip_path: Path to MOJO zip downloaded from H2O.
    :param genmodel_jar_path: Optional, path to genmodel jar file. If None (default) then the h2o-genmodel.jar in the same
        folder as the MOJO zip will be used.
    :param classpath: Optional, specifies custom user defined classpath which will be used when scoring. If None
        (default) then the default classpath for this MOJO model will be used.
    :param java_options: Optional, custom user defined options for Java. By default ``-Xmx4g`` is used.
    :param verbose: Optional, if True, then additional debug information will be printed. False by default.
    :return: Pandas frame with predictions
    """
    tmp_dir = tempfile.mkdtemp()
    try:
        if not can_use_pandas():
            raise RuntimeError('Cannot import pandas')
        import pandas
        assert_is_type(dataframe, pandas.DataFrame)
        input_csv_path = os.path.join(tmp_dir, 'input.csv')
        prediction_csv_path = os.path.join(tmp_dir, 'prediction.csv')
        dataframe.to_csv(input_csv_path)
        mojo_predict_csv(input_csv_path=input_csv_path, mojo_zip_path=mojo_zip_path,
                         output_csv_path=prediction_csv_path, genmodel_jar_path=genmodel_jar_path,
                         classpath=classpath, java_options=java_options, verbose=verbose, setInvNumNA=setInvNumNA)
        return pandas.read_csv(prediction_csv_path)
    finally:
        shutil.rmtree(tmp_dir)


def mojo_predict_csv(input_csv_path, mojo_zip_path, output_csv_path=None, genmodel_jar_path=None, classpath=None, 
                     java_options=None, verbose=False, setInvNumNA=False):
    """
    MOJO scoring function to take a CSV file and use MOJO model as zip file to score.

    :param input_csv_path: Path to input CSV file.
    :param mojo_zip_path: Path to MOJO zip downloaded from H2O.
    :param output_csv_path: Optional, name of the output CSV file with computed predictions. If None (default), then
        predictions will be saved as prediction.csv in the same folder as the MOJO zip.
    :param genmodel_jar_path: Optional, path to genmodel jar file. If None (default) then the h2o-genmodel.jar in the same
        folder as the MOJO zip will be used.
    :param classpath: Optional, specifies custom user defined classpath which will be used when scoring. If None
        (default) then the default classpath for this MOJO model will be used.
    :param java_options: Optional, custom user defined options for Java. By default ``-Xmx4g -XX:ReservedCodeCacheSize=256m`` is used.
    :param verbose: Optional, if True, then additional debug information will be printed. False by default.
    :return: List of computed predictions
    """
    default_java_options = '-Xmx4g -XX:ReservedCodeCacheSize=256m'
    prediction_output_file = 'prediction.csv'

    # Checking java
    java = H2OLocalServer._find_java()
    H2OLocalServer._check_java(java=java, verbose=verbose)

    # Ensure input_csv exists
    if verbose:
        print("input_csv:\t%s" % input_csv_path)
    if not os.path.isfile(input_csv_path):
        raise RuntimeError("Input csv cannot be found at %s" % input_csv_path)

    # Ensure mojo_zip exists
    mojo_zip_path = os.path.abspath(mojo_zip_path)
    if verbose:
        print("mojo_zip:\t%s" % mojo_zip_path)
    if not os.path.isfile(mojo_zip_path):
        raise RuntimeError("MOJO zip cannot be found at %s" % mojo_zip_path)

    parent_dir = os.path.dirname(mojo_zip_path)

    # Set output_csv if necessary
    if output_csv_path is None:
        output_csv_path = os.path.join(parent_dir, prediction_output_file)

    # Set path to h2o-genmodel.jar if necessary and check it's valid
    if genmodel_jar_path is None:
        genmodel_jar_path = os.path.join(parent_dir, gen_model_file_name)
    if verbose:
        print("genmodel_jar:\t%s" % genmodel_jar_path)
    if not os.path.isfile(genmodel_jar_path):
        raise RuntimeError("Genmodel jar cannot be found at %s" % genmodel_jar_path)

    if verbose and output_csv_path is not None:
        print("output_csv:\t%s" % output_csv_path)

    # Set classpath if necessary
    if classpath is None:
        classpath = genmodel_jar_path
    if verbose:
        print("classpath:\t%s" % classpath)

    # Set java_options if necessary
    if java_options is None:
        java_options = default_java_options
    if verbose:
        print("java_options:\t%s" % java_options)

    # Construct command to invoke java
    cmd = [java]
    for option in java_options.split(' '):
        cmd += [option]
    cmd += ["-cp", classpath, h2o_predictor_class, "--mojo", mojo_zip_path, "--input", input_csv_path,
            '--output', output_csv_path, '--decimal']

    if setInvNumNA:
        cmd.append('--setConvertInvalidNum')
        
    if verbose:
        cmd_str = " ".join(cmd)
        print("java cmd:\t%s" % cmd_str)
               

    # invoke the command
    subprocess.check_call(cmd, shell=False)

    # load predictions in form of a dict
    with open(output_csv_path) as csv_file:
        result = list(csv.DictReader(csv_file))
    return result


class InMemoryZipArch(object):
    def __init__(self, file_name = None, compression = zipfile.ZIP_DEFLATED):
        self._data = io.BytesIO()
        self._arch = zipfile.ZipFile(self._data, "w", compression, False)
        self._file_name = file_name

    def append(self, filename_in_zip, file_contents):
        self._arch.writestr(filename_in_zip, file_contents)
        return self

    def write_to_file(self, filename):
        # Mark the files as having been created on Windows so that
        # Unix permissions are not inferred as 0000
        for zfile in self._arch.filelist:
            zfile.create_system = 0
        self._arch.close()
        with open(filename, 'wb') as f:
            f.write(self._data.getvalue())

    def __enter__(self):
            return self

    def __exit__(self, exc_type, exc_value, traceback):
        if self._file_name is None:
            return
        self.write_to_file(self._file_name)
