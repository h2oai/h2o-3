"""
This module provides all of the top level calls for models and various data transform methods.
By simply
"""

import os
import os.path
import re
import urllib
import urllib2
import json
import random
import tabulate
import numpy as np
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
  return j['destination_frames'][0]

def upload_file(path, destination_frame=""):
  """
  Upload a dataset at the path given from the local machine to the H2O cluster.

  :param path: A path specifying the location of the data to upload.
  :param destination_frame: The name of the H2O Frame in the H2O Cluster.
  :return: A new H2OFrame
  """
  fui = {"file": os.path.abspath(path)}
  destination_frame = H2OFrame.py_tmp_key() if destination_frame == "" else destination_frame
  H2OConnection.post_json(url_suffix="PostFile", file_upload_info=fui,destination_frame=destination_frame)
  return H2OFrame(text_key=destination_frame)


def import_frame(path=None, vecs=None):
  """
  Import a frame from a file (remote or local machine). If you run H2O on Hadoop, you can access to HDFS

  :param path: A path specifying the location of the data to import.
  :return: A new H2OFrame
  """
  return H2OFrame(vecs=vecs) if vecs else H2OFrame(remote_fname=path)


def parse_setup(raw_frames):
  """
  :param raw_frames: A collection of imported file frames
  :return: A ParseSetup "object"
  """

  # The H2O backend only accepts things that are quoted
  if isinstance(raw_frames, unicode): raw_frames = [raw_frames]
  j = H2OConnection.post_json(url_suffix="ParseSetup", source_frames=[_quoted(id) for id in raw_frames])
  return j


def parse(setup, h2o_name, first_line_is_header=(-1, 0, 1)):
  """
  Trigger a parse; blocking; removeFrame just keep the Vecs.

  :param setup: The result of calling parse_setup.
  :param h2o_name: The name of the H2O Frame on the back end.
  :param first_line_is_header: -1 means data, 0 means guess, 1 means header.
  :return: A new parsed object  
  """
  # Parse parameters (None values provided by setup)
  p = { 'destination_frame' : h2o_name,
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
    setup["na_strings"] = [[_quoted(na) for na in col] if col is not None else [] for col in setup["na_strings"]]
    p["na_strings"] = None


  # update the parse parameters with the parse_setup values
  p.update({k: v for k, v in setup.iteritems() if k in p})

  p["check_header"] = first_line_is_header

  # Extract only 'name' from each src in the array of srcs
  p['source_frames'] = [_quoted(src['name']) for src in setup['source_frames']]

  # Request blocking parse
  j = H2OJob(H2OConnection.post_json(url_suffix="Parse", **p), "Parse").poll()
  return j.jobs


def impute(data, column, method=["mean","median","mode"], # TODO: add "bfill","ffill"
           combine_method=["interpolate", "average", "low", "high"], by=None, inplace=True):

  """
  Impute a column in this H2OFrame.

  :param column: The column to impute
  :param method: How to compute the imputation value.
  :param combine_method: For even samples and method="median", how to combine quantiles.
  :param by: Columns to group-by for computing imputation value per groups of columns.
  :param inplace: Impute inplace?
  :return: the imputed frame.
  """
  return data.impute(column,method,combine_method,by,inplace)

def _quoted(key):
  if key == None: return "\"\""
  is_quoted = len(re.findall(r'\"(.+?)\"', key)) != 0
  key = key if is_quoted  else "\"" + key + "\""
  return key


def split_frame(data, ratios=[0.75], destination_frames=None):
  """
  Split a frame into distinct subsets of size determined by the given ratios.
  The number of subsets is always 1 more than the number of ratios given.
  :param data: The dataset to split.
  :param ratios: The fraction of rows for each split.
  :param destination_frames: names of the split frames
  :return: a list of frames
  """
  fr = data.send_frame()
  if destination_frames is None: destination_frames=""
  j = H2OConnection.post_json("SplitFrame", dataset=fr, ratios=ratios, destination_frames=destination_frames) #, "Split Frame").poll()
  splits = []
  for i in j["destination_frames"]:
    splits += [get_frame(i["name"])]
    removeFrameShallow(i["name"])
  removeFrameShallow(fr)
  return splits


def get_frame(frame_id):
  if frame_id is None:
    raise ValueError("frame_id must not be None")
  res = H2OConnection.get_json("Frames/"+frame_id)
  res = res["frames"][0]
  colnames = [v["label"] for v in res["columns"]]
  veckeys  = res["vec_ids"]
  vecs=H2OVec.new_vecs(zip(colnames, veckeys), res["rows"])
  return H2OFrame(vecs=vecs)

# res <- .h2o.__remoteSend(conn, paste0(.h2o.__FRAMES, "/", frame_id))$frames[[1]]
# cnames <- unlist(lapply(res$columns, function(c) c$label))
# .h2o.parsedData(conn, frame_id, res$rows, length(res$columns), cnames, linkToGC = linkToGC)

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


# HDFS helpers
def get_h2o_internal_hdfs_name_node():
  return "172.16.2.176"

def is_running_internal_to_h2o():
  url = "http://{0}:50070".format(get_h2o_internal_hdfs_name_node())
  try:
    urllib2.urlopen(urllib2.Request(url))
    internal = True
  except:
    internal = False
  return internal

def dim_check(data1, data2):
  """
  Check that the dimensions of the data1 and data2 are the same
  :param data1: an H2OFrame, H2OVec or Expr
  :param data2: an H2OFrame, H2OVec or Expr
  :return: None
  """
  data1_rows, data1_cols = data1.dim()
  data2_rows, data2_cols = data2.dim()
  assert data1_rows == data2_rows and data1_cols == data2_cols, \
    "failed dim check! data1_rows:{0} data2_rows:{1} data1_cols:{2} data2_cols:{3}".format(data1_rows, data2_rows,
                                                                                           data1_cols, data2_cols)
def np_comparison_check(h2o_data, np_data, num_elements):
  """
  Check values achieved by h2o against values achieved by numpy
  :param h2o_data: an H2OFrame, H2OVec or Expr
  :param np_data: a numpy array
  :param num_elements: number of elements to compare
  :return: None
  """
  rows, cols = h2o_data.dim()
  for i in range(num_elements):
    r = random.randint(0,rows-1)
    c = random.randint(0,cols-1)
    h2o_val = h2o_data[r,c] if isinstance(h2o_data,H2OFrame) else h2o_data[r]
    np_val = np_data[r,c] if len(np_data.shape) > 1 else np_data[r]
    assert np.absolute(h2o_val - np_val) < 1e-6, \
      "failed comparison check! h2o computed {0} and numpy computed {1}".format(h2o_val, np_val)

def value_check(h2o_data, local_data, num_elements, col=None):
  """
  Check that the values of h2o_data and local_data are the same. In a testing context, this could be used to check
  that an operation did not alter the original h2o_data.

  :param h2o_data: an H2OFrame, H2OVec or Expr
  :param local_data: a list of lists (row x col format)
  :param num_elements: number of elements to check
  :param col: an optional integer that specifies the particular column to check
  :return: None
  """
  rows, cols = h2o_data.dim()
  for i in range(num_elements):
    r = random.randint(0,np.minimum(99,rows-1))
    c = random.randint(0,cols-1) if not col else col
    h2o_val = as_list(h2o_data[r,c])
    h2o_val = h2o_val[0][0] if isinstance(h2o_val, list) else h2o_val
    local_val = local_data[r][c]
    assert h2o_val == local_val, "failed value check! h2o:{0} and local:{1}".format(h2o_val, local_val)

def run_test(sys_args, test_to_run):
  ip, port = sys_args[2].split(":")
  init(ip,port)
  num_keys = store_size()
  test_to_run(ip, port)
  if keys_leaked(num_keys):
    print "KEYS WERE LEAKED!!! CHECK H2O LOGS"

def ipy_notebook_exec(path,save_and_norun=False):
  notebook = json.load(open(path))
  program = ''
  for block in ipy_blocks(notebook):
    for line in ipy_lines(block):
      if "h2o.init" not in line:
        program += line if '\n' in line else line + '\n'
  if save_and_norun:
    with open(os.path.basename(path).split('ipynb')[0]+'py',"w") as f:
      f.write(program)
  else:
    exec(program, globals())

def ipy_blocks(notebook):
  if 'worksheets' in notebook.keys():
    return notebook['worksheets'][0]['cells'] # just take the first worksheet
  elif 'cells' in notebook.keys():
    return notebook['cells']
  else:
    raise NotImplementedError, "ipython notebook cell/block json format not handled"

def ipy_lines(block):
  if 'source' in block.keys():
    return block['source']
  elif 'input' in block.keys():
    return block['input']
  else:
    raise NotImplementedError, "ipython notebook source/line json format not handled"

def remove(object):
  """
  Remove object from H2O. This is a "hard" delete of the object. It removes all subparts.

  :param object: The object pointing to the object to be removed.
  :return: None
  """
  if object is None:
    raise ValueError("remove with no object is not supported, for your protection")

  if isinstance(object, H2OFrame):
    object._vecs=[]

  elif isinstance(object, H2OVec):
    H2OConnection.delete("DKV/"+str(object.key()))
    object._expr=None
    object=None

  else:
    H2OConnection.delete("DKV/" + object)
  #
  # else:
  #   raise ValueError("Can't remove objects of type: " + id.__class__)

def removeFrameShallow(key):
  """
  Do a shallow DKV remove of the frame (does not remove any internal Vecs)
  :param key: A Frame Key to be removed
  :return: None
  """
  rapids("(removeframe '"+key+"')")
  return None

def rapids(expr):
  """
  Fire off a Rapids expression.

  :param expr: The rapids expression (ascii string).
  :return: The JSON response of the Rapids execution
  """
  result = H2OConnection.post_json("Rapids", ast=urllib.quote(expr), _rest_version=99)
  if result['error'] is not None:
    raise EnvironmentError("rapids expression not evaluated: {0}".format(str(result['error'])))
  return result

def frame(frame_id):
  """
  Retrieve metadata for a id that points to a Frame.

  :param frame_id: A pointer to a Frame  in H2O.
  :return: Meta information on the frame
  """
  return H2OConnection.get_json("Frames/" + frame_id)


def frames():
  """
  Retrieve all the Frames.

  :return: Meta information on the frames
  """
  return H2OConnection.get_json("Frames")

def frame_summary(key):
  """
  Retrieve metadata and summary information for a key that points to a Frame/Vec

  :param key: A pointer to a Frame/Vec in H2O
  :return: Meta and summary info on the frame
  """
  # frames_meta = H2OConnection.get_json("Frames/" + key)
  frame_summary =  H2OConnection.get_json("Frames/" + key + "/summary")
  return frame_summary

def download_pojo(model,path=""):
  """
  Download the POJO for this model to the directory specified by path (no trailing slash!).
  If path is "", then dump to screen.
  :param model: Retrieve this model's scoring POJO.
  :param path:  An absolute path to the directory where POJO should be saved.
  :return: None
  """
  model_id = model._key

  java = H2OConnection.get( "Models/"+model_id+".java" )
  file_path = path + "/" + model_id + ".java"
  if path == "": print java.text
  else:
    with open(file_path, 'w') as f:
      f.write(java.text)

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
  cbind = "(= !" + fr + " (cbind %FALSE %"
  cbind += " %".join([vec._expr.eager() for vec in vecs]) + "))"
  rapids(cbind)

  j = frame(fr)
  fr = j['frames'][0]
  rows = fr['rows']
  vec_ids = fr['vec_ids']
  cols = fr['columns']
  colnames = [col['label'] for col in cols]
  result = H2OFrame(vecs=H2OVec.new_vecs(zip(colnames, vec_ids), rows))
  result.setNames(names)
  return result


def init(ip="localhost", port=54321, size=1, start_h2o=False, enable_assertions=False,
         license=None, max_mem_size_GB=None, min_mem_size_GB=None, ice_root=None, strict_version_check=False):
  """
  Initiate an H2O connection to the specified ip and port.

  :param ip: An IP address, default is "localhost"
  :param port: A port, default is 54321
  :param size: THe expected number of h2o instances (ignored if start_h2o is True)
  :param start_h2o: A boolean dictating whether this module should start the H2O jvm. An attempt is made anyways if _connect fails.
  :param enable_assertions: If start_h2o, pass `-ea` as a VM option.s
  :param license: If not None, is a path to a license file.
  :param max_mem_size_GB: Maximum heap size (jvm option Xmx) in gigabytes.
  :param min_mem_size_GB: Minimum heap size (jvm option Xms) in gigabytes.
  :param ice_root: A temporary directory (default location is determined by tempfile.mkdtemp()) to hold H2O log files.
  :return: None
  """
  H2OConnection(ip=ip, port=port,start_h2o=start_h2o,enable_assertions=enable_assertions,license=license,max_mem_size_GB=max_mem_size_GB,min_mem_size_GB=min_mem_size_GB,ice_root=ice_root,strict_version_check=strict_version_check)
  return None

def export_file(frame,path,force=False):
  """
  Export a given H2OFrame to a path on the machine this python session is currently connected to. To view the current session, call h2o.cluster_info().

  :param frame: The Frame to save to disk.
  :param path: The path to the save point on disk.
  :param force: Overwrite any preexisting file with the same path
  :return: None
  """
  fr = H2OFrame.send_frame(frame)
  f = "true" if force else "false"
  H2OConnection.get_json("Frames/"+str(fr)+"/export/"+path+"/overwrite/"+f)

def cluster_info():
  """
  Display the current H2O cluster information.

  :return: None
  """
  H2OConnection._cluster_info()

def deeplearning(x,y=None,validation_x=None,validation_y=None,**kwargs):
  """
  Build a supervised Deep Learning model (kwargs are the same arguments that you can find in FLOW)

  :return: Return a new classifier or regression model.
  """
  return h2o_model_builder.supervised_model_build(x,y,validation_x,validation_y,"deeplearning",kwargs)

def autoencoder(x,**kwargs):
  """
  Build an Autoencoder

  :param x: Columns with which to build an autoencoder
  :param kwargs: Additional arguments to pass to the autoencoder.
  :return: A new autoencoder model
  """
  return h2o_model_builder.unsupervised_model_build(x,None,"autoencoder",kwargs)

def gbm(x,y,validation_x=None,validation_y=None,**kwargs):
  """
  Build a Gradient Boosted Method model (kwargs are the same arguments that you can find in FLOW)

  :return: A new classifier or regression model.
  """
  return h2o_model_builder.supervised_model_build(x,y,validation_x,validation_y,"gbm",kwargs)

def glm(x,y,validation_x=None,validation_y=None,**kwargs):
  """
  Build a Generalized Linear Model (kwargs are the same arguments that you can find in FLOW)

  :return: A new regression or binomial classifier.
  """
  kwargs = dict([(k, kwargs[k]) if k != "Lambda" else ("lambda", kwargs[k]) for k in kwargs])
  return h2o_model_builder.supervised_model_build(x,y,validation_x,validation_y,"glm",kwargs)

def kmeans(x,validation_x=None,**kwargs):
  """
  Build a KMeans model (kwargs are the same arguments that you can find in FLOW)

  :return: A new clustering model
  """
  return h2o_model_builder.unsupervised_model_build(x,validation_x,"kmeans",kwargs)

def random_forest(x,y,validation_x=None,validation_y=None,**kwargs):
  """
  Build a Random Forest Model (kwargs are the same arguments that you can find in FLOW)

  :return: A new classifier or regression model.
  """
  return h2o_model_builder.supervised_model_build(x,y,validation_x,validation_y,"drf",kwargs)

def ddply(frame,cols,fun):
  return frame.ddply(cols,fun)

def group_by(frame,cols,aggregates):
  return frame.group_by(cols,aggregates)

def pca(x,validation_x=None,**kwargs):
  """
  :param k: The number of principal components to be computed. This must be between 1 and min(ncol(training_frame),
  nrow(training_frame)) inclusive.
  :param model_id: (Optional) The unique hex key assigned to the resulting model. Automatically generated if none
  is provided.
  :param max_iterations: The maximum number of iterations to run each power iteration loop. Must be between 1 and
  1e6 inclusive.
  :param transform: A character string that indicates how the training data should be transformed before running PCA.
  Possible values are "NONE": for no transformation, "DEMEAN": for subtracting the mean of each column, "DESCALE":
  for dividing by the standard deviation of each column, "STANDARDIZE": for demeaning and descaling, and "NORMALIZE":
  for demeaning and dividing each column by its range (max - min).
  :param seed: (Optional) Random seed used to initialize the right singular vectors at the beginning of each power
  method iteration.
  :param use_all_factor_levels: (Optional) A logical value indicating whether all factor levels should be included
  in each categorical column expansion. If FALSE, the indicator column corresponding to the first factor level of
  every categorical variable will be dropped. Defaults to FALSE.
  :return: a new dim reduction model
  """
  kwargs['_rest_version'] = 99
  return h2o_model_builder.unsupervised_model_build(x,validation_x,"pca",kwargs)


def svd(x,validation_x=None,**kwargs):
  """
  Singular value decomposition of a H2O dataset using the power method.

  :param nv: The number of right singular vectors to be computed. This must be between 1 and min(ncol(training_frame),
  nrow(training_frame)) inclusive.
  :param max_iterations: The maximum number of iterations to run each power iteration loop. Must be between 1 and
  1e6 inclusive.max_iterations The maximum number of iterations to run each power iteration loop. Must be between 1
  and 1e6 inclusive.
  :param transform: A character string that indicates how the training data should be transformed before running PCA.
  Possible values are "NONE": for no transformation, "DEMEAN": for subtracting the mean of each column, "DESCALE": for
  dividing by the standard deviation of each column, "STANDARDIZE": for demeaning and descaling, and "NORMALIZE": for
  demeaning and dividing each column by its range (max - min).
  :param seed: (Optional) Random seed used to initialize the right singular vectors at the beginning of each power
  method iteration.
  :param use_all_factor_levels: (Optional) A logical value indicating whether all factor levels should be included in
  each categorical column expansion. If FALSE, the indicator column corresponding to the first factor level of every
  categorical variable will be dropped. Defaults to TRUE.
  :return: a new dim reduction model
  """
  kwargs['_rest_version'] = 99
  return h2o_model_builder.unsupervised_model_build(x,validation_x,"svd",kwargs)


def naive_bayes(x,y,validation_x=None,validation_y=None,**kwargs):
  """
  The naive Bayes classifier assumes independence between predictor variables conditional on the response, and a
  Gaussian distribution of numeric predictors with mean and standard deviation computed from the training dataset.
  When building a naive Bayes classifier, every row in the training dataset that contains at least one NA will be
  skipped completely. If the test dataset has missing values, then those predictors are omitted in the probability
  calculation during prediction.

  :param laplace: A positive number controlling Laplace smoothing. The default zero disables smoothing.
  :param threshold: The minimum standard deviation to use for observations without enough data. Must be at least 1e-10.
  :param eps: A threshold cutoff to deal with numeric instability, must be positive.
  :param compute_metrics: A logical value indicating whether model metrics should be computed. Set to FALSE to reduce
  the runtime of the algorithm.
  :return: Returns an H2OBinomialModel if the response has two categorical levels, H2OMultinomialModel otherwise.
  """
  return h2o_model_builder.supervised_model_build(x,y,validation_x,validation_y,"naivebayes",kwargs)


def create_frame(id = None, rows = 10000, cols = 10, randomize = True, value = 0, real_range = 100,
                 categorical_fraction = 0.2, factors = 100, integer_fraction = 0.2, integer_range = 100,
                 binary_fraction = 0.1, binary_ones_fraction = 0.02, missing_fraction = 0.01, response_factors = 2,
                 has_response = False, seed=None):
  """
  Data Frame Creation in H2O.
  Creates a data frame in H2O with real-valued, categorical, integer, and binary columns specified by the user.

  :param id: A string indicating the destination key. If empty, this will be auto-generated by H2O.
  :param rows: The number of rows of data to generate.
  :param cols: The number of columns of data to generate. Excludes the response column if has_response == True}.
  :param randomize: A logical value indicating whether data values should be randomly generated. This must be TRUE if
  either categorical_fraction or integer_fraction is non-zero.
  :param value: If randomize == FALSE, then all real-valued entries will be set to this value.
  :param real_range: The range of randomly generated real values.
  :param categorical_fraction:  The fraction of total columns that are categorical.
  :param factors: The number of (unique) factor levels in each categorical column.
  :param integer_fraction: The fraction of total columns that are integer-valued.
  :param integer_range: The range of randomly generated integer values.
  :param binary_fraction: The fraction of total columns that are binary-valued.
  :param binary_ones_fraction: The fraction of values in a binary column that are set to 1.
  :param missing_fraction: The fraction of total entries in the data frame that are set to NA.
  :param response_factors: If has_response == TRUE, then this is the number of factor levels in the response column.
  :param has_response: A logical value indicating whether an additional response column should be pre-pended to the
  final H2O data frame. If set to TRUE, the total number of columns will be cols+1.
  :param seed: A seed used to generate random values when randomize = TRUE.
  :return: the H2OFrame that was created
  """
  parms = {"dest": _py_tmp_key() if id is None else id,
           "rows": rows,
           "cols": cols,
           "randomize": randomize,
           "value": value,
           "real_range": real_range,
           "categorical_fraction": categorical_fraction,
           "factors": factors,
           "integer_fraction": integer_fraction,
           "integer_range": integer_range,
           "binary_fraction": binary_fraction,
           "binary_ones_fraction": binary_ones_fraction,
           "missing_fraction": missing_fraction,
           "response_factors": response_factors,
           "has_response": has_response,
           "seed": -1 if seed is None else seed,
           }
  H2OJob(H2OConnection.post_json("CreateFrame", **parms), "Create Frame").poll()
  return get_frame(parms["dest"])


def interaction(data, factors, pairwise, max_factors, min_occurrence, destination_frame=None):
  """
  Categorical Interaction Feature Creation in H2O.
  Creates a frame in H2O with n-th order interaction features between categorical columns, as specified by
  the user.

  :param data: the H2OFrame that holds the target categorical columns.
  :param factors: factors Factor columns (either indices or column names).
  :param pairwise: Whether to create pairwise interactions between factors (otherwise create one
  higher-order interaction). Only applicable if there are 3 or more factors.
  :param max_factors: Max. number of factor levels in pair-wise interaction terms (if enforced, one extra catch-all
  factor will be made)
  :param min_occurrence: Min. occurrence threshold for factor levels in pair-wise interaction terms
  :param destination_frame: A string indicating the destination key. If empty, this will be auto-generated by H2O.
  :return: H2OFrame
  """
  data._eager()
  factors = [data.names()[n] if isinstance(n,int) else n for n in factors]
  parms = {"dest": _py_tmp_key() if destination_frame is None else destination_frame,
           "source_frame": data._id,
           "factor_columns": [_quoted(f) for f in factors],
           "pairwise": pairwise,
           "max_factors": max_factors,
           "min_occurrence": min_occurrence,
           }
  H2OJob(H2OConnection.post_json("Interaction", **parms), "Interactions").poll()
  return get_frame(parms["dest"])


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


def store_size():
  """
  Get the H2O store size (current count of keys).
  :return: number of keys in H2O cloud
  """
  return rapids("(store_size)")["result"]

def keys_leaked(num_keys):
  """
  Ask H2O if any keys leaked.
  @param num_keys: The number of keys that should be there.
  :return: A boolean True/False if keys leaked. If keys leaked, check H2O logs for further detail.
  """
  return rapids("keys_leaked #{})".format(num_keys))["result"]=="TRUE"

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
    if data.is_local(): return data._data
    if data.is_pending():
      data.eager()
      if data.is_local(): return [data._data] if isinstance(data._data, list) else [[data._data]]
    j = frame(data._data) # data is remote
    return map(list, zip(*[c['data'] for c in j['frames'][0]['columns'][:]]))
  if isinstance(data, H2OVec):
    if data._expr.is_local(): return data._expr._data
    if data._expr.is_pending():
      data._expr.eager()
      if data._expr.is_local(): return [[data._expr._data]]
    j = frame(data._expr._data) # data is remote
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

def logical_negation(data) : return data.logical_negation()

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
  Element-wise math operations on H2OFrame and H2OVec

  :param op: the math operation
  :param data: the H2OFrame or H2OVec object to operate on.
  :return: H2OFrame or H2oVec, with lazy operation
  """
  if isinstance(data, H2OFrame): return H2OFrame(vecs=[_simple_un_math_op(op,vec) for vec in data._vecs])
  if isinstance(data, H2OVec)  : return H2OVec(data._name, Expr(op, left=data, length=len(data)))
  raise ValueError, op + " only operates on H2OFrame or H2OVec objects"

# generic reducers: these are eager
def min(data)   : return data.min()
def max(data)   : return data.max()
def sum(data)   : return data.sum()
def sd(data)    : return data.sd()
def var(data)   : return data.var()
def mean(data)  : return data.mean()
def median(data): return data.median()


class H2ODisplay:
  """
  Pretty printing for H2O Objects;
  Handles both IPython and vanilla console display
  """
  THOUSANDS = "{:,}"
  def __init__(self,table=None,header=None,table_header=None,**kwargs):
    self.table_header=table_header
    self.header=header
    self.table=table
    self.kwargs=kwargs
    self.do_print=True

    # one-shot display... never return an H2ODisplay object (or try not to)
    # if holding onto a display object, then may have odd printing behavior
    # the __repr__ and _repr_html_ methods will try to save you from many prints,
    # but just be WARNED that your mileage may vary!

    if self.table_header is not None:
      print
      print self.table_header + ":"
      print
    if H2ODisplay._in_ipy():
      from IPython.display import display
      display(self)
      self.do_print=False
    else:
      self.pprint()
      self.do_print=False

  # for Ipython
  def _repr_html_(self):
    if self.do_print:
      return H2ODisplay._html_table(self.table,self.header)

  def pprint(self):
    r = self.__repr__()
    print r

  # for python REPL console
  def __repr__(self):
    if self.do_print or not H2ODisplay._in_ipy():
      if self.header is None:  # tabulate is picky; can't handle None for headers...
        return tabulate.tabulate(self.table,**self.kwargs)
      else:
        return tabulate.tabulate(self.table,headers=self.header,**self.kwargs)
    self.do_print=True
    return ""

  @staticmethod
  def _in_ipy():  # are we in ipy? then pretty print tables with _repr_html
    try:
      __IPYTHON__
      return True
    except NameError:
      return False

  # some html table builder helper things
  @staticmethod
  def _html_table(rows, header=None):
    table= "<div style=\"overflow:auto\"><table style=\"width:50%\">{}</table></div>"  # keep table in a div for scroll-a-bility
    table_rows=[]
    if header is not None:
      table_rows.append(H2ODisplay._html_row(header))
    for row in rows:
      table_rows.append(H2ODisplay._html_row(row))
    return table.format("\n".join(table_rows))

  @staticmethod
  def _html_row(row):
    res = "<tr>{}</tr>"
    entry = "<td>{}</td>"
    entries = "\n".join([entry.format(str(r)) for r in row])
    return res.format(entries)
