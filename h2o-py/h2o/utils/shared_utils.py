"""Shared utilities used by various classes, all placed here to avoid circular imports.

This file INTENTIONALLY has NO module dependencies!
"""

import imp
import itertools
import re

# private static methods
_id_ctr = 0
def _py_tmp_key():
  global _id_ctr
  _id_ctr=_id_ctr+1
  return "py_" + str(_id_ctr)

def can_use_pandas():
  try:
    imp.find_module('pandas')
    return True
  except ImportError:
    return False


def _gen_header(cols):
  return ["C" + str(c) for c in range(1, cols + 1, 1)]

def _check_lists_of_lists(python_obj):
  # all items in the list must be a list too
  lol_all = all(isinstance(l, (tuple, list)) for l in python_obj)
  # All items in the list must be a list!
  if not lol_all:
    raise ValueError("`python_obj` is a mixture of nested lists and other types.")

  # in fact, we must have a list of flat lists!
  for l in python_obj:
    if any(isinstance(ll, (tuple, list)) for ll in l):
      raise ValueError("`python_obj` is not a list of flat lists!")

def _handle_python_lists(python_obj):
  cols = len(python_obj)  # cols will be len(python_obj) if not a list of lists
  lol = _is_list_of_lists(python_obj)  # do we have a list of lists: [[...], ..., [...]] ?
  if lol:
    _check_lists_of_lists(python_obj)  # must be a list of flat lists, raise ValueError if not
  else:
    cols = 1
    python_obj = [python_obj]

  # create the header
  header = _gen_header(cols)
  # shape up the data for csv.DictWriter
  rows = map(list, itertools.izip_longest(*python_obj))
  data_to_write = [dict(zip(header,row)) for row in rows]
  return header, data_to_write

def _is_list(l):
  return isinstance(l, (tuple, list))

def _is_str_list(l):
  return isinstance(l, (tuple, list)) and all([isinstance(i,basestring) for i in l])

def _is_num_list(l):
  return isinstance(l, (tuple, list)) and all([isinstance(i,(float,int)) for i in l])

def _is_list_of_lists(o):
  return any(isinstance(l, (list, tuple)) for l in o)

def _handle_numpy_array(python_obj):
  return _handle_python_lists(python_obj=python_obj.tolist())

def _handle_pandas_data_frame(python_obj):
  return _handle_numpy_array(python_obj=python_obj.as_matrix())

def _handle_python_dicts(python_obj):
  header = python_obj.keys()
  is_valid = all([re.match(r'^[a-zA-Z_][a-zA-Z0-9_.]*$', col) for col in header])  # is this a valid header?
  if not is_valid:
    raise ValueError("Did not get a valid set of column names! Must match the regular expression: ^[a-zA-Z_][a-zA-Z0-9_.]*$ ")
  for k in python_obj:  # check that each value entry is a flat list/tuple
    v = python_obj[k]
    if isinstance(v, (tuple, list)):  # if value is a tuple/list, then it must be flat
      if _is_list_of_lists(v):
        raise ValueError("Values in the dictionary must be flattened!")

  rows = map(list, itertools.izip_longest(*python_obj.values()))
  data_to_write = [dict(zip(header, row)) for row in rows]
  return header, data_to_write

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