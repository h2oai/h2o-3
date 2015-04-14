"""
This module implements the communication REST layer for the python <-> H2O connection.
"""

import os
import re
import urllib
import json
from connection import H2OConnection
from job import H2OJob
from frame import H2OFrame, H2OVec
from expr import Expr
import h2o_model_builder


def import_file(path):
  """
  Import a single file or collection of files.

  :param path: A path to a data file (remote or local).
  :return: A new H2OFrame
  """
  paths = [path] if isinstance(path,str) else path
  return [ _import1(fname) for fname in paths ]

def _import1(path):
  j = H2OConnection.get_json(url_suffix="ImportFiles", path=path)
  if j['fails']:
    raise ValueError("ImportFiles of " + path + " failed on " + j['fails'])
  return j['keys'][0]

def upload_file(path, destination_key=""):
  """
  Upload a dataset at the path given from the local machine to the H2O cluster.

  :param path: A path specifying the location of the data to upload.
  :param destination_key: The name of the H2O Frame in the H2O Cluster.
  :return: A new H2OFrame
  """
  fui = {"file": os.path.abspath(path)}
  dest_key = H2OFrame.py_tmp_key() if destination_key == "" else destination_key
  H2OConnection.post_json(url_suffix="PostFile", file_upload_info=fui,destination_key=dest_key)
  return H2OFrame(text_key=dest_key)


def import_frame(path=None, vecs=None):
  """
  Import a frame from a file (remote or local machine). If you run H2O on Hadoop, you can access to HDFS

  :param path: A path specifying the location of the data to import.
  :return: A new H2OFrame
  """
  return H2OFrame(vecs=vecs) if vecs else H2OFrame(remote_fname=path)


def parse_setup(rawkey):
  """
  :param rawkey: A collection of imported file keys
  :return: A ParseSetup "object"
  """

  # So the st00pid H2O backend only accepts things that are quoted (nasty Java)
  if isinstance(rawkey, unicode): rawkey = [rawkey]
  j = H2OConnection.post_json(url_suffix="ParseSetup", source_keys=[_quoted(key) for key in rawkey])
  if not j['is_valid']:
    raise ValueError("ParseSetup not Valid", j)
  return j


def parse(setup, h2o_name, first_line_is_header=(-1, 0, 1)):
  """
  Trigger a parse; blocking; removeFrame just keep the Vec keys.

  :param setup: The result of calling parse_setup.
  :param h2o_name: The name of the H2O Frame on the back end.
  :param first_line_is_header: -1 means data, 0 means guess, 1 means header.
  :return: A new parsed object  
  """
  # Parse parameters (None values provided by setup)
  p = { 'destination_key' : h2o_name,
        'parse_type' : None,
        'separator' : None,
        'single_quotes' : None,
        'check_header'  : None,
        'number_columns' : None,
        'chunk_size'    : None,
        'delete_on_done' : True,
        'blocking' : True,
        'remove_frame' : True
  }
  if isinstance(first_line_is_header, tuple):
    first_line_is_header = setup["check_header"] 

  if setup["column_names"]:
    setup["column_names"] = [_quoted(name) for name in setup["column_names"]]
    p["column_names"] = None

  if setup["column_types"]:
    setup["column_types"] = [_quoted(name) for name in setup["column_types"]]
    p["column_types"] = None

  if setup["na_strings"]:
    setup["na_strings"] = [_quoted(name) for name in setup["na_strings"]]
    p["na_strings"] = None


  # update the parse parameters with the parse_setup values
  p.update({k: v for k, v in setup.iteritems() if k in p})

  p["check_header"] = first_line_is_header

  # Extract only 'name' from each src in the array of srcs
  p['source_keys'] = [_quoted(src['name']) for src in setup['source_keys']]

  # Request blocking parse
  j = H2OJob(H2OConnection.post_json(url_suffix="Parse", **p), "Parse").poll()
  return j.jobs


def _quoted(key):
  if key == None: return "\"\""
  is_quoted = len(re.findall(r'\"(.+?)\"', key)) != 0
  key = key if is_quoted  else "\"" + key + "\""
  return key

"""
Here are some testing utilities for running the pyunit tests in conjunction with run.py.

run.py issues an ip and port as a string:  "<ip>:<port>".
The expected value of sys_args[1] is "<ip>:<port>"
"""


"""
All tests MUST have the following structure:

import sys
sys.path.insert(1, "..")  # may vary depending on this test's position relative to h2o-py
import h2o


def my_test(ip=None, port=None):
  ...test filling...

if __name__ == "__main__":
  h2o.run_test(sys.argv, my_test)

So each test must have an ip and port
"""
def run_test(sys_args, test_to_run):
  ip, port = sys_args[2].split(":")
  test_to_run(ip, port)

def ipy_notebook_exec(path):
  notebook = json.load(open(path))
  for block in notebook["cells"]:
    cmd = ''
    for line in block["source"]:
      if "h2o.init" not in line: cmd += line
    exec(cmd)

def remove(key):
  """
  Remove key from H2O.

  :param key: The key pointing to the object to be removed.
  :return: Void
  """
  if key is None:
    raise ValueError("remove with no key is not supported, for your protection")

  H2OConnection.delete("DKV/" + key)

def rapids(expr):
  """
  Fire off a Rapids expression.

  :param expr: The rapids expression (ascii string).
  :return: The JSON response of the Rapids execution
  """
  result = H2OConnection.post_json("Rapids", ast=urllib.quote(expr))
  if result['error'] is not None:
    raise EnvironmentError("rapids expression not evaluated: {0}".format(str(result['error'])))
  return result

def frame(key):
  """
  Retrieve metadata for a key that points to a Frame.

  :param key: A pointer to a Frame  in H2O.
  :return: Meta information on the frame
  """
  return H2OConnection.get_json("Frames/" + key)


# Non-Mutating cbind
def cbind(left,right):
  """
  :param left: H2OFrame or H2OVec
  :param right: H2OFrame or H2OVec
  :return: new H2OFrame with left|right cbinded
  """
  # Check left and right data types
  vecs = []
  if isinstance(left,H2OFrame) and isinstance(right,H2OFrame):
    vecs = left._vecs + right._vecs
  elif isinstance(left,H2OFrame) and isinstance(right,H2OVec):
    [vecs.append(vec) for vec in left._vecs]
    vecs.append(right)
  elif isinstance(left,H2OVec) and isinstance(right,H2OVec):
    vecs = [left, right]
  elif isinstance(left,H2OVec) and isinstance(right,H2OFrame):
    vecs.append(left)
    [vecs.append(vec) for vec in right._vecs]
  else:
    raise ValueError("left and right data must be H2OVec or H2OFrame")
  names = [vec.name() for vec in vecs]

  fr = H2OFrame.py_tmp_key()
  cbind = "(= !" + fr + " (cbind %"
  cbind += " %".join([vec._expr.eager() for vec in vecs]) + "))"
  rapids(cbind)

  j = frame(fr)
  fr = j['frames'][0]
  rows = fr['rows']
  veckeys = fr['vec_keys']
  cols = fr['columns']
  colnames = [col['label'] for col in cols]
  result = H2OFrame(vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows))
  result.setNames(names)
  return result


def init(ip="localhost", port=54321):
  """
  Initiate an H2O connection to the specified ip and port.

  :param ip: A IP address, default is "localhost".
  :param port: A port, default is 54321.
  :return: None
  """
  H2OConnection(ip=ip, port=port)
  return None

def export_file(frame,path,force=False):
  fr = H2OFrame.send_frame(frame)
  f = "true" if force else "false"
  H2OConnection.get_json("Frames/"+str(fr)+"/export/"+path+"/overwrite/"+f)


def deeplearning(x,y,validation_x=None,validation_y=None,**kwargs):
  """
  Build a supervised Deep Learning model (kwargs are the same arguments that you can find in FLOW)
  """
  return h2o_model_builder.supervised_model_build(x,y,validation_x,validation_y,"deeplearning",kwargs)

def gbm(x,y,validation_x=None,validation_y=None,**kwargs):
  """
  Build a Gradient Boosted Method model (kwargs are the same arguments that you can find in FLOW)
  """
  return h2o_model_builder.supervised_model_build(x,y,validation_x,validation_y,"gbm",kwargs)

def glm(x,y,validation_x=None,validation_y=None,**kwargs):
  """
  Build a Generalized Linear Model (kwargs are the same arguments that you can find in FLOW)
  """
  kwargs = dict([(k, kwargs[k]) if k != "Lambda" else ("lambda", kwargs[k]) for k in kwargs])
  return h2o_model_builder.supervised_model_build(x,y,validation_x,validation_y,"glm",kwargs)

def kmeans(x,validation_x=None,**kwargs):
  """
  Build a KMeans model (kwargs are the same arguments that you can find in FLOW)
  """
  return h2o_model_builder.unsupervised_model_build(x,validation_x,"kmeans",kwargs)

def random_forest(x,y,validation_x=None,validation_y=None,**kwargs):
  """
  Build a Random Forest Model (kwargs are the same arguments that you can find in FLOW)
  """
  return h2o_model_builder.supervised_model_build(x,y,validation_x,validation_y,"drf",kwargs)

def ddply(frame,cols,fun):
  return frame.ddply(cols,fun)

def groupby(frame,cols,aggregates):
  return frame.groupby(cols,aggregates)

def network_test():
  res = H2OConnection.get_json(url_suffix="NetworkTest")
  res["table"].show()

def locate(path):
  """
  Search for a relative path and turn it into an absolute path.
  This is handy when hunting for data files to be passed into h2o and used by import file.
  Note: This function is for unit testing purposes only.

  :param path: Path to search for
  :return: Absolute path if it is found.  None otherwise.
  """

  tmp_dir = os.path.realpath(os.getcwd())
  possible_result = os.path.join(tmp_dir, path)
  while (True):
      if (os.path.exists(possible_result)):
          return possible_result

      next_tmp_dir = os.path.dirname(tmp_dir)
      if (next_tmp_dir == tmp_dir):
          return None

      tmp_dir = next_tmp_dir
      possible_result = os.path.join(tmp_dir, path)

def as_list(data):
  """
  If data is an Expr, then eagerly evaluate it and pull the result from h2o into the local environment. In the local
  environment an H2O Frame is represented as a list of lists (each element in the broader list represents a row).
  Note: This uses function uses h2o.frame(), which will return meta information on the H2O Frame and only the first
  100 rows. This function is only intended to be used within the testing framework. More robust functionality must
  be constructed for production conversion between H2O and python data types.
  :return: List of list (Rows x Columns).
  """
  if isinstance(data, Expr):
    x = data.eager()
    if data.is_local():
      return x
    j = frame(data._data)
    return map(list, zip(*[c['data'] for c in j['frames'][0]['columns'][:]]))
  if isinstance(data, H2OVec):
    x = data._expr.eager()
    if data._expr.is_local():
      return x
    j = frame(data._expr._data)
    return map(list, zip(*[c['data'] for c in j['frames'][0]['columns'][:]]))
  if isinstance(data, H2OFrame):
    vec_as_list = [as_list(v) for v in data._vecs]
    frm = []
    for row in range(len(vec_as_list[0])):
      tmp = []
      for col in range(len(vec_as_list)):
        tmp.append(vec_as_list[col][row][0])
      frm.append(tmp)
    return frm


def cos(data)     : return _simple_un_math_op("cos", data)
def sin(data)     : return _simple_un_math_op("sin", data)
def tan(data)     : return _simple_un_math_op("tan", data)
def acos(data)    : return _simple_un_math_op("acos", data)
def asin(data)    : return _simple_un_math_op("asin", data)
def atan(data)    : return _simple_un_math_op("atan", data)
def cosh(data)    : return _simple_un_math_op("cosh", data)
def sinh(data)    : return _simple_un_math_op("sinh", data)
def tanh(data)    : return _simple_un_math_op("tanh", data)
def acosh(data)   : return _simple_un_math_op("acosh", data)
def asinh(data)   : return _simple_un_math_op("asinh", data)
def atanh(data)   : return _simple_un_math_op("atanh", data)
def cospi(data)   : return _simple_un_math_op("cospi", data)
def sinpi(data)   : return _simple_un_math_op("sinpi", data)
def tanpi(data)   : return _simple_un_math_op("tanpi", data)
def abs(data)     : return _simple_un_math_op("abs", data)
def sign(data)    : return _simple_un_math_op("sign", data)
def sqrt(data)    : return _simple_un_math_op("sqrt", data)
def trunc(data)   : return _simple_un_math_op("trunc", data)
def ceil(data)    : return _simple_un_math_op("ceiling", data)
def floor(data)   : return _simple_un_math_op("floor", data)
def log(data)     : return _simple_un_math_op("log", data)
def log10(data)   : return _simple_un_math_op("log10", data)
def log1p(data)   : return _simple_un_math_op("log1p", data)
def log2(data)    : return _simple_un_math_op("log2", data)
def exp(data)     : return _simple_un_math_op("exp", data)
def expm1(data)   : return _simple_un_math_op("expm1", data)
def gamma(data)   : return _simple_un_math_op("gamma", data)
def lgamma(data)  : return _simple_un_math_op("lgamma", data)
def digamma(data) : return _simple_un_math_op("digamma", data)
def trigamma(data): return _simple_un_math_op("trigamma", data)

def _simple_un_math_op(op, data):
  """
  Element-wise math operations on H2OFrame, H2OVec, and Expr objects.

  :param op: the math operation
  :param data: the H2OFrame, H2OVec, or Expr object to operate on.
  :return: Expr'd data
  """
  if   isinstance(data, H2OFrame): return Expr(op, Expr(data.send_frame(), length=data.nrow()))
  elif isinstance(data, H2OVec)  : return Expr(op, data, length=len(data))
  elif isinstance(data, Expr)    : return Expr(op, data)
  else: raise ValueError, op + " only operates on H2OFrame, H2OVec, or Expr objects"

# generic reducers
def min(data)   : return data.min()
def max(data)   : return data.max()
def sum(data)   : return data.sum()
def sd(data)    : return data.sd()
def var(data)   : return data.var()
def mean(data)  : return data.mean()
def median(data): return data.median()
