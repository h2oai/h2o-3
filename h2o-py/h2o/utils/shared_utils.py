"""Shared utilities used by various classes, all placed here to avoid circular imports.

This file INTENTIONALLY has NO module dependencies!
"""
from builtins import map
from builtins import str
from builtins import zip
from builtins import range
import os
from past.builtins import basestring
from six import PY3
import imp
import itertools
import re

# private static methods
_id_ctr = 0
def _py_tmp_key(append=""):
  global _id_ctr
  _id_ctr=_id_ctr+1
  return "py_" + str(_id_ctr) + append

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

def quote(stuff_to_quote):
  if PY3:
    from urllib.request import quote
    return quote(stuff_to_quote)
  else:
    from urllib2 import quote
    return quote(stuff_to_quote)

def urlopen():
  if PY3:
    from urllib import request
    return request.urlopen
  else:
    import urllib2
    return urllib2.urlopen

def _gen_header(cols):
  return ["C" + str(c) for c in range(1, cols + 1, 1)]

def _check_lists_of_lists(python_obj):
  #check we have a lists of flat lists
  #returns longest length of sublist
  most_cols = 0
  for l in python_obj:
    # All items in the list must be a list!
    if not isinstance(l, (tuple,list)):
      raise ValueError("`python_obj` is a mixture of nested lists and other types.")
    most_cols = max(most_cols, len(l))
    for ll in l:
      # in fact, we must have a list of flat lists!
      if isinstance(ll, (tuple,list)):
        raise ValueError("`python_obj` is not a list of flat lists!")
  return most_cols
  

def _handle_python_lists(python_obj, check_header):
  #convert all inputs to lol
  if _is_list_of_lists(python_obj):  # do we have a list of lists: [[...], ..., [...]] ?
    ncols = _check_lists_of_lists(python_obj)  # must be a list of flat lists, raise ValueError if not
  elif isinstance(python_obj, (list,tuple)): #single list
    ncols = len(python_obj)
    python_obj = [python_obj]
  else: #scalar
    python_obj = [[python_obj]]
    ncols = 1
  # create the header
  header = _gen_header(ncols) if check_header != 1 else python_obj.pop(0)
  # shape up the data for csv.DictWriter
  data_to_write = [dict(list(zip(header,row))) for row in python_obj]
  return header, data_to_write

def _is_list(l):
  return isinstance(l, (tuple, list))

def _is_str_list(l):
  return isinstance(l, (tuple, list)) and all([isinstance(i,basestring) for i in l])

def _is_num_list(l):
  return isinstance(l, (tuple, list)) and all([isinstance(i,(float,int)) for i in l])

def _is_list_of_lists(o):
  return any(isinstance(l, (list, tuple)) for l in o)

def _handle_numpy_array(python_obj, header):
  return _handle_python_lists(python_obj.tolist(), header)

def _handle_pandas_data_frame(python_obj, header):
  return _handle_numpy_array(python_obj.as_matrix(), header)

def _handle_python_dicts(python_obj):
  header = list(python_obj.keys())
  is_valid = all([re.match(r'^[a-zA-Z_][a-zA-Z0-9_.]*$', col) for col in header])  # is this a valid header?
  if not is_valid:
    raise ValueError("Did not get a valid set of column names! Must match the regular expression: ^[a-zA-Z_][a-zA-Z0-9_.]*$ ")
  for k in python_obj:  # check that each value entry is a flat list/tuple or single int, float, or string
    v = python_obj[k]
    if isinstance(v, (tuple, list)):  # if value is a tuple/list, then it must be flat
      if _is_list_of_lists(v):
        raise ValueError("Values in the dictionary must be flattened!")
    elif isinstance(v, (int, float)) or _is_str(v): python_obj[k] = [v]
    else: raise ValueError("Encountered invalid dictionary value when constructing H2OFrame. Got: {0}".format(v))

  rows = list(map(list, itertools.zip_longest(*list(python_obj.values()))))
  data_to_write = [dict(list(zip(header, row))) for row in rows]
  return header, data_to_write

def _is_str(s):
  try: return isinstance(s, (str, basestring, unicode)) # python 2.x
  except NameError: return isinstance(s, str) # python 3.x

def _is_fr(o):
  return o.__class__.__name__ == "H2OFrame"  # hack to avoid circular imports

def _quoted(key):
  if key is None: return "\"\""
  # mimic behavior in R to replace "%" and "&" characters, which break the call to /Parse, with "."
  # key = key.replace("%", ".")
  # key = key.replace("&", ".")
  is_quoted = len(re.findall(r'\"(.+?)\"', key)) != 0
  key = key if is_quoted  else '"' + key + '"'
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