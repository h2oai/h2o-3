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
import imp
import random
import tabulate
from connection import H2OConnection
from job import H2OJob
from expr import ExprNode
from frame import H2OFrame, _py_tmp_key
from model import H2OBinomialModel,H2OAutoEncoderModel,H2OClusteringModel,H2OMultinomialModel,H2ORegressionModel
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
  destination_frame = _py_tmp_key() if destination_frame == "" else destination_frame
  H2OConnection.post_json(url_suffix="PostFile", file_upload_info=fui,destination_frame=destination_frame)
  return H2OFrame(raw_id=destination_frame)


def import_frame(path=None):
  """
  Import a frame from a file (remote or local machine). If you run H2O on Hadoop, you can access to HDFS

  :param path: A path specifying the location of the data to import.
  :return: A new H2OFrame
  """
  return H2OFrame(file_path=path)


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
        'blocking' : False,
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

def parse_raw(setup, id=None, first_line_is_header=(-1,0,1)):
  """
  Used in conjunction with import_file and parse_setup in order to make alterations before parsing.

  :param setup: Result of h2o.parse_setup
  :param id: An optional id for the frame.
  :param first_line_is_header: -1,0,1 if the first line is to be used as the header
  :return: An H2OFrame object
  """
  id = setup["destination_frame"]
  fr = H2OFrame()
  parsed = parse(setup, id, first_line_is_header)
  fr._nrows = parsed['rows']
  fr._col_names = parsed['column_names']
  fr._ncols = len(fr._col_names)
  fr._computed = True
  fr._id = id
  return fr

def _quoted(key):
  if key == None: return "\"\""
  is_quoted = len(re.findall(r'\"(.+?)\"', key)) != 0
  key = key if is_quoted  else "\"" + key + "\""
  return key

def assign(data,id):
  rapids(ExprNode(",", ExprNode("gput", id, data), ExprNode("removeframe", data))._eager())
  data._id = id
  return data

def which(condition):
  """
  :param condition: A conditional statement.
  :return: A H2OFrame of 1 column filled with 0-based indices for which the condition is True
  """
  return H2OFrame(expr=ExprNode("h2o.which",condition,False))._frame()

def ifelse(test,yes,no):
  """
  Semantically equivalent to R's ifelse.
  Based on the booleans in the test vector, the output has the values of the yes and no
  vectors interleaved (or merged together).

  :param test: A "test" H2OFrame
  :param yes:  A "yes" H2OFrame
  :param no:   A "no"  H2OFrame
  :return: An H2OFrame
  """
  return H2OFrame(expr=ExprNode("ifelse",test,yes,no))._frame()

def get_future_model(future_model):
  """
  Waits for the future model to finish building, and then returns the model.

  :param future_model: an H2OModelFuture object
  :return: a resolved model (i.e. an H2OBinomialModel, H2ORegressionModel, H2OMultinomialModel, ...)
  """
  return h2o_model_builder._resolve_model(future_model)

def get_model(model_id):
  """
  Return the specified model

  :param model_id: The model identification in h2o
  """
  model_json = H2OConnection.get_json("Models/"+model_id)["models"][0]
  model_type = model_json["output"]["model_category"]
  if model_type=="Binomial":      return H2OBinomialModel(model_id, model_json)
  elif model_type=="Clustering":  return H2OClusteringModel(model_id, model_json)
  elif model_type=="Regression":  return H2ORegressionModel(model_id, model_json)
  elif model_type=="Multinomial": return H2OMultinomialModel(model_id, model_json)
  elif model_type=="AutoEncoder": return H2OAutoEncoderModel(model_id, model_json)
  else:                           raise NotImplementedError(model_type)


def get_frame(frame_id):
  """
  Obtain a handle to the frame in H2O with the frame_id key.

  :return: An H2OFrame
  """
  return H2OFrame.get_frame(frame_id)

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




# TODO/FIXME: need to create an internal testing framework for python ... internal IP addresses should NOT be published as part of package!
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

def check_models(model1, model2, use_validation=False, op='e'):
  """
  Check that the given models are equivalent
  :param model1:
  :param model2:
  :param use_validation: boolean. if True, use validation metrics to determine model equality. Otherwise, use
  training metrics.
  :param op: comparison operator to use. 'e':==, 'g':>, 'ge':>=
  :return: None. Throw meaningful error messages if the check fails
  """
  # 1. Check model types
  model1_type = type(model1)
  model2_type = type(model2)
  assert model1_type == model2_type, "The model types differ. The first model is of type {0} and the second " \
                                     "models is of type {1}.".format(model1_type, model2_type)

  # 2. Check model metrics
  if isinstance(model1,H2OBinomialModel): #   2a. Binomial
    # F1
    f1_1 = model1.F1(valid=use_validation)
    f1_2 = model2.F1(valid=use_validation)
    if op == 'e': assert f1_1 == f1_2, "The first model has an F1 of {0} and the second model has an F1 of " \
                                       "{1}. Expected the first to be == to the second.".format(f1_1, f1_2)
    elif op == 'g': assert f1_1 > f1_2, "The first model has an F1 of {0} and the second model has an F1 of " \
                                        "{1}. Expected the first to be > than the second.".format(f1_1, f1_2)
    elif op == 'ge': assert f1_1 >= f1_2, "The first model has an F1 of {0} and the second model has an F1 of " \
                                          "{1}. Expected the first to be >= than the second.".format(f1_1, f1_2)
  elif isinstance(model1,H2ORegressionModel): #   2b. Regression
    # MSE
    mse1 = model1.mse(valid=use_validation)
    mse2 = model2.mse(valid=use_validation)
    if op == 'e': assert mse1 == mse2, "The first model has an MSE of {0} and the second model has an MSE of " \
                                       "{1}. Expected the first to be == to the second.".format(mse1, mse2)
    elif op == 'g': assert mse1 > mse2, "The first model has an MSE of {0} and the second model has an MSE of " \
                                        "{1}. Expected the first to be > than the second.".format(mse1, mse2)
    elif op == 'ge': assert mse1 >= mse2, "The first model has an MSE of {0} and the second model has an MSE of " \
                                          "{1}. Expected the first to be >= than the second.".format(mse1, mse2)
  elif isinstance(model1,H2OMultinomialModel): #   2c. Multinomial
    # hit-ratio
    pass
  elif isinstance(model1,H2OClusteringModel): #   2d. Clustering
    # totss
    totss1 = model1.totss(valid=use_validation)
    totss2 = model2.totss(valid=use_validation)
    if op == 'e': assert totss1 == totss2, "The first model has an TOTSS of {0} and the second model has an " \
                                           "TOTSS of {1}. Expected the first to be == to the second.".format(totss1,
                                                                                                             totss2)
    elif op == 'g': assert totss1 > totss2, "The first model has an TOTSS of {0} and the second model has an " \
                                            "TOTSS of {1}. Expected the first to be > than the second.".format(totss1,
                                                                                                               totss2)
    elif op == 'ge': assert totss1 >= totss2, "The first model has an TOTSS of {0} and the second model has an " \
                                              "TOTSS of {1}. Expected the first to be >= than the second." \
                                              "".format(totss1, totss2)

def check_dims_values(python_obj, h2o_frame, rows, cols):
  """
  Check that the dimensions and values of the python object and H2OFrame are equivalent. Assumes that the python object
  conforms to the rules specified in the h2o frame documentation.

  :param python_obj: a (nested) list, tuple, dictionary, numpy.ndarray, ,or pandas.DataFrame
  :param h2o_frame: an H2OFrame
  :param rows: number of rows
  :param cols: number of columns
  :return: None
  """
  h2o_rows, h2o_cols = h2o_frame.dim()
  assert h2o_rows == rows and h2o_cols == cols, "failed dim check! h2o_rows:{0} rows:{1} h2o_cols:{2} cols:{3}" \
                                                "".format(h2o_rows, rows, h2o_cols, cols)
  if isinstance(python_obj, (list, tuple)):
    for r in range(rows):
      for c in range(cols):
        pval = python_obj[r][c] if rows > 1 else python_obj[c]
        hval = h2o_frame[r,c]
        assert pval == hval, "expected H2OFrame to have the same values as the python object for row {0} and column " \
                             "{1}, but h2o got {2} and python got {3}.".format(r, c, hval, pval)
  elif isinstance(python_obj, dict):
    for r in range(rows):
      for k in python_obj.keys():
        pval = python_obj[k][r] if hasattr(python_obj[k],'__iter__') else python_obj[k]
        hval = h2o_frame[r,k]
        assert pval == hval, "expected H2OFrame to have the same values as the python object for row {0} and column " \
                             "{1}, but h2o got {2} and python got {3}.".format(r, k, hval, pval)

def np_comparison_check(h2o_data, np_data, num_elements):
  """
  Check values achieved by h2o against values achieved by numpy

  :param h2o_data: an H2OFrame or H2OVec
  :param np_data: a numpy array
  :param num_elements: number of elements to compare
  :return: None
  """
  # Check for numpy
  try:
    imp.find_module('numpy')
  except ImportError:
    assert False, "failed comparison check because unable to import numpy"

  import numpy as np
  rows, cols = h2o_data.dim()
  for i in range(num_elements):
    r = random.randint(0,rows-1)
    c = random.randint(0,cols-1)
    h2o_val = h2o_data[r,c] if isinstance(h2o_data,H2OFrame) else h2o_data[r]
    np_val = np_data[r,c] if len(np_data.shape) > 1 else np_data[r]
    if isinstance(np_val, np.bool_): np_val = bool(np_val)  # numpy haz special bool type :(
    assert np.absolute(h2o_val - np_val) < 1e-6, \
      "failed comparison check! h2o computed {0} and numpy computed {1}".format(h2o_val, np_val)

def run_test(sys_args, test_to_run):
  import pkg_resources
  ver = pkg_resources.get_distribution("h2o").version
  print "H2O PYTHON PACKAGE VERSION: " + str(ver)
  ip, port = sys_args[2].split(":")
  init(ip,port)
  log_and_echo("------------------------------------------------------------")
  log_and_echo("")
  log_and_echo("STARTING TEST: "+str(ou()))
  log_and_echo("")
  log_and_echo("------------------------------------------------------------")
  num_keys = store_size()
  test_to_run(ip, port)
  if keys_leaked(num_keys): print "Leaked Keys!"

def ou():
  """
  Where is my baguette!?

  :return: the name of the baguette. oh uhr uhr huhr
  """
  from inspect import stack
  return stack()[2][1]

def log_and_echo(message):
  """
  Log a message on the server-side logs
  This is helpful when running several pieces of work one after the other on a single H2O
  cluster and you want to make a notation in the H2O server side log where one piece of
  work ends and the next piece of work begins.

  Sends a message to H2O for logging. Generally used for debugging purposes.

  :param message: A character string with the message to write to the log.
  :return: None
  """
  if message is None: message = ""
  H2OConnection.post_json("LogAndEcho", message=message)

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
    d={}
    exec program in d  # safe, but horrible (exec is horrible)

def ipy_blocks(notebook):
  if 'worksheets' in notebook.keys():
    return notebook['worksheets'][0]['cells']  # just take the first worksheet
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

  if isinstance(object, H2OFrame): H2OConnection.delete("DKV/"+object._id)
  if isinstance(object, str):      H2OConnection.delete("DKV/"+object)

def remove_all():
  """
  Remove all objects from H2O.

  :return None
  """
  H2OConnection.delete("DKV")

def removeFrameShallow(key):
  """
  Do a shallow DKV remove of the frame (does not remove any internal Vecs).
  This is a "soft" delete. Just removes the top level pointer, but all big data remains!

  :param key: A Frame Key to be removed
  :return: None
  """
  rapids("(removeframe '"+key+"')")
  return None

def rapids(expr, id=None):
  """
  Fire off a Rapids expression.

  :param expr: The rapids expression (ascii string).
  :return: The JSON response of the Rapids execution
  """
  if isinstance(expr, list): expr = ExprNode._collapse_sb(expr)
  expr = "(= !{} {})".format(id,expr) if id is not None else expr
  result = H2OConnection.post_json("Rapids", ast=urllib.quote(expr), _rest_version=99)
  if result['error'] is not None:
    raise EnvironmentError("rapids expression not evaluated: {0}".format(str(result['error'])))
  return result

def ls():
  """
  List Keys on an H2O Cluster

  :return: Returns a list of keys in the current H2O instance
  """
  return H2OFrame(expr=ExprNode("ls"))._frame().as_data_frame()


def frame(frame_id, exclude=""):
  """
  Retrieve metadata for a id that points to a Frame.

  :param frame_id: A pointer to a Frame  in H2O.
  :return: Meta information on the frame
  """
  return H2OConnection.get_json("Frames/" + urllib.quote(frame_id+exclude))

def frames():
  """
  Retrieve all the Frames.

  :return: Meta information on the frames
  """
  return H2OConnection.get_json("Frames")

def download_pojo(model,path=""):
  """
  Download the POJO for this model to the directory specified by path (no trailing slash!).
  If path is "", then dump to screen.

  :param model: Retrieve this model's scoring POJO.
  :param path:  An absolute path to the directory where POJO should be saved.
  :return: None
  """
  java = H2OConnection.get( "Models.java/"+model._id )
  file_path = path + "/" + model._id + ".java"
  if path == "": print java.text
  else:
    with open(file_path, 'w') as f:
      f.write(java.text)


def download_csv(data, filename):
  """
  Download an H2O data set to a CSV file on the local disk.
  Warning: Files located on the H2O server may be very large! Make
  sure you have enough hard drive space to accommodate the entire file.

  :param data: an H2OFrame object to be downloaded.
  :param filename:A string indicating the name that the CSV file should be
  should be saved to.
  :return: None
  """
  if not isinstance(data, H2OFrame): raise(ValueError, "`data` argument must be an H2OFrame, but got " + type(data))
  url = "http://{}:{}/3/DownloadDataset?frame_id={}".format(H2OConnection.ip(),H2OConnection.port(),data._id)
  with open(filename, 'w') as f:
    response = urllib2.urlopen(url)
    f.write(response.read())
    f.close()


def download_all_logs(dirname=".",filename=None):
  """
  Download H2O Log Files to Disk
  :param dirname: (Optional) A character string indicating the directory that the log file should be saved in.
  :param filename: (Optional) A string indicating the name that the CSV file should be
  :return: path of logs written (as a string)
  """
  url = 'http://' + H2OConnection.ip() + ':' + str(H2OConnection.port()) + '/Logs/download'
  response = urllib2.urlopen(url)

  if not os.path.exists(dirname): os.mkdir(dirname)
  if filename == None:
    for h in response.headers.headers:
      if 'filename=' in h:
        filename = h.split("filename=")[1].strip()
        break
  path = os.path.join(dirname,filename)

  with open(path, 'w') as f:
    response = urllib2.urlopen(url)
    f.write(response.read())
    f.close()

  print "Writing H2O logs to " + path
  return path

def save_model(model, dir="", name="", filename="", force=False):
  """
  Save an H2O Model Object to Disk.
  In the case of existing files force = TRUE will overwrite the file. Otherwise, the operation will fail.
  :param dir: string indicating the directory the model will be written to.
  :param name: string name of the file.
  :param filename: full path to the file.
  :param force: logical, indicates how to deal with files that already exist
  :return: the path of the model (string)
  """
  if not isinstance(dir, str): raise ValueError("`dir` must be a character string")
  if dir == "": dir = os.getcwd()
  if not isinstance(name, str): raise ValueError("`name` must be a character string")
  if name == "": name = model._model_json['model_id']['name']
  if not isinstance(filename, str): raise ValueError("`filename` must be a character string")
  if not isinstance(force, bool): raise ValueError("`force` must be True or False")
  path = filename if filename != "" else os.path.join(dir, name)

  kwargs = dict([("dir",path), ("force",int(force)), ("_rest_version", 99)])
  H2OConnection.get("Models.bin/"+model._model_json['model_id']['name'], **kwargs)
  return path

def load_model(path):
  """
  Load a saved H2O model from disk.
  :param path: The full path of the H2O Model to be imported. For example, if the `dir` argument in h2o.saveModel was
  set to "/Users/UserName/Desktop" then the `path` argument in h2o.loadModel should be set to something like
  "/Users/UserName/Desktop/K-meansModel__a7cebf318ca5827185e209edf47c4052"
  :return: the model
  """
  if not isinstance(path, str): raise ValueError("`path` must be a non-empty character string")
  kwargs = dict([("dir",path), ("_rest_version", 99)])
  res = H2OConnection.post("Models.bin/", **kwargs)
  return get_model(res.json()['models'][0]['model_id']['name'])

def cluster_status():
  """
  TODO: This isn't really a cluster status... it's a node status check for the node we're connected to.
  This is possibly confusing because this can come back without warning,
  but if a user tries to do any remoteSend, they will get a "cloud sick warning"

  Retrieve information on the status of the cluster running H2O.
  :return: None
  """
  cluster_json = H2OConnection.get_json("Cloud?skip_ticks=true")

  print "Version: {0}".format(cluster_json['version'])
  print "Cloud name: {0}".format(cluster_json['cloud_name'])
  print "Cloud size: {0}".format(cluster_json['cloud_size'])
  if cluster_json['locked']: print "Cloud is locked\n"
  else: print "Accepting new members\n"
  if cluster_json['nodes'] == None or len(cluster_json['nodes']) == 0:
    print "No nodes found"
    return

  status = []
  for node in cluster_json['nodes']:
    for k, v in zip(node.keys(),node.values()):
      if k in ["h2o", "healthy", "last_ping", "num_cpus", "sys_load", "mem_value_size", "total_value_size",
               "free_mem", "tot_mem", "max_mem", "free_disk", "max_disk", "pid", "num_keys", "tcps_active",
               "open_fds", "rpcs_active"]: status.append(k+": {0}".format(v))
    print ', '.join(status)
    print


def init(ip="localhost", port=54321, size=1, start_h2o=False, enable_assertions=False,
         license=None, max_mem_size_GB=None, min_mem_size_GB=None, ice_root=None, strict_version_check=True):
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
  H2OConnection.get_json("Frames/"+frame._id+"/export/"+path+"/overwrite/"+("true" if force else "false"))


def cluster_info():
  """
  Display the current H2O cluster information.

  :return: None
  """
  H2OConnection._cluster_info()

def shutdown(conn=None, prompt=True):
  """
  Shut down the specified instance. All data will be lost.
  This method checks if H2O is running at the specified IP address and port, and if it is, shuts down that H2O instance.

  :param conn: An H2OConnection object containing the IP address and port of the server running H2O.
  :param prompt: A logical value indicating whether to prompt the user before shutting down the H2O server.
  :return: None
  """
  if conn == None: conn = H2OConnection.current_connection()
  H2OConnection._shutdown(conn=conn, prompt=prompt)

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

def start_glm_job(x,y,validation_x=None,validation_y=None,**kwargs):
  """
  Build a Generalized Linear Model (kwargs are the same arguments that you can find in FLOW).
  Note: this function is the same as glm(), but it doesn't block on model-build. Instead, it returns and H2OModelFuture
  object immediately. The model can be retrieved from the H2OModelFuture object with get_future_model().

  :return: H2OModelFuture
  """

  kwargs["do_future"] = True
  return glm(x,y,validation_x,validation_y,**kwargs)

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


def prcomp(x,validation_x=None,**kwargs):
  """
  Principal components analysis of a H2O dataset using the power method
  to calculate the singular value decomposition of the Gram matrix.

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
          raise ValueError("File not found: " + path)

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


def as_list(data, use_pandas=True):
  """
  Convert an H2O data object into a python-specific object.

  WARNING: This will pull all data local!

  If Pandas is available (and use_pandas is True), then pandas will be used to parse the data frame.
  Otherwise, a list-of-lists populated by character data will be returned (so the types of data will
  all be str).

  :param data: An H2O data object.
  :param use_pandas: Try to use pandas for reading in the data.
  :return: List of list (Rows x Columns).
  """
  return H2OFrame.as_data_frame(data, use_pandas)


def set_timezone(tz):
  """
  Set the Time Zone on the H2O Cloud

  :param tz: The desired timezone.
  :return: None
  """
  rapids(ExprNode("setTimeZone", tz)._eager())

def get_timezone():
  """
  Get the Time Zone on the H2O Cloud

  :return: the time zone (string)
  """
  return H2OFrame(expr=ExprNode("getTimeZone"))._scalar()

def list_timezones():
  """
  Get a list of all the timezones

  :return: the time zones (as an H2OFrame)
  """
  return H2OFrame(expr=ExprNode("listTimeZones"))._frame()


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
    #
    # In other words, it's better to just new one of these when you're ready to print out.

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
      if self.header is None: return tabulate.tabulate(self.table,**self.kwargs)
      else:                   return tabulate.tabulate(self.table,headers=self.header,**self.kwargs)
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

def can_use_pandas():
  try:
    imp.find_module('pandas')
    return True
  except ImportError:
    return False
