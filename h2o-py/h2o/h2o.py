"""
This module provides all of the top level calls for models and various data transform methods.
By simply
"""

import os
import os.path
import re
import urllib
import csv
import imp
import urllib2
import json
import imp
import random
import tabulate
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

def parse_raw(setup, id=None, first_line_is_header=(-1,0,1)):
  """
  Used in conjunction with import_file and parse_setup in order to make alterations before parsing.
  :param setup: Result of h2o.parse_setup
  :param id: An optional id for the frame.
  :param first_line_is_header: -1,0,1 if the first line is to be used as the header
  :return: An H2OFrame object
  """
  if id is None: id = H2OFrame.py_tmp_key()
  parsed = parse(setup, id, first_line_is_header)
  veckeys = parsed['vec_ids']
  rows = parsed['rows']
  cols = parsed['column_names'] if parsed["column_names"] else ["C" + str(x) for x in range(1,len(veckeys)+1)]
  vecs = H2OVec.new_vecs(zip(cols, veckeys), rows)
  return H2OFrame(vecs=vecs)

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
  test_a=None
  yes_a =None
  no_a  =None

  test_tmp = None
  yes_tmp  = None
  no_tmp   = None

  if isinstance(test, bool): test_a = "%TRUE" if test else "%FALSE"
  else:
    if isinstance(test,H2OVec): test_tmp = test._expr.eager()
    else:                       test_tmp = test.key()
    test_a = "'"+test_tmp+"'"
  if isinstance(yes, (int,float)): yes_a = "#{}".format(str(yes))
  elif yes is None:                yes_a = "#NaN"
  else:
    if isinstance(yes,H2OVec): yes_tmp = yes._expr.eager()
    else:                      yes_tmp = yes.key()
    yes_a = "'"+yes_tmp+"'"
  if isinstance(no, (int,float)): no_a = "#{}".format(str(no))
  elif no is None:                no_a = "#NaN"
  else:
    if isinstance(no,H2OVec): no_tmp = no._expr.eager()
    else:                     no_tmp = no.key()
    no_a = "'"+no_tmp+"'"

  tmp_key = H2OFrame.py_tmp_key()
  expr = "(= !{} (ifelse {} {} {}))".format(tmp_key,test_a,yes_a,no_a)
  rapids(expr)
  j = frame(tmp_key) # Fetch the frame as JSON
  fr = j['frames'][0]    # Just the first (only) frame
  rows = fr['rows']      # Row count
  veckeys = fr['vec_ids']# List of h2o vec keys
  cols = fr['columns']   # List of columns
  colnames = [col['label'] for col in cols]
  vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows) # Peel the Vecs out of the returned Frame
  removeFrameShallow(tmp_key)
  if yes_tmp is not  None: removeFrameShallow(str(yes_tmp))
  if no_tmp is not   None: removeFrameShallow(str(no_tmp))
  if test_tmp is not None: removeFrameShallow(str(test_tmp))
  return H2OFrame(vecs=vecs)

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

def get_model(model_id):
  """
  Return the specified model

  :param model_id: The model identification in h2o
  """
  model_json = H2OConnection.get_json("Models/"+model_id)["models"][0]
  model_type = model_json["output"]["model_category"]
  if model_type=="Binomial":
    from model.binomial import H2OBinomialModel
    model = H2OBinomialModel(model_id, model_json)

  elif model_type=="Clustering":
    from model.clustering import H2OClusteringModel
    model = H2OClusteringModel(model_id, model_json)

  elif model_type=="Regression":
    from model.regression import H2ORegressionModel
    model = H2ORegressionModel(model_id, model_json)

  elif model_type=="Multinomial":
    from model.multinomial import H2OMultinomialModel
    model = H2OMultinomialModel(model_id, model_json)

  elif model_type=="AutoEncoder":
    from model.autoencoder import H2OAutoEncoderModel
    model = H2OAutoEncoderModel(model_id, model_json)

  else:
    print model_type
    raise NotImplementedError

  return model

def get_frame(frame_id):
  if frame_id is None:
    raise ValueError("frame_id must not be None")
  res = H2OConnection.get_json("Frames/"+urllib.quote(frame_id))
  res = res["frames"][0]
  colnames = [v["label"] for v in res["columns"]]
  veckeys  = res["vec_ids"]
  vecs=H2OVec.new_vecs(zip(colnames, veckeys), res["rows"])
  return H2OFrame(vecs=vecs)

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

  if isinstance(object, H2OFrame):
    fr = H2OFrame.send_frame(object)
    remove(fr)
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

def ls():
  """
  List Keys on an H2O Cluster
  :return: Returns a list of keys in the current H2O instance
  """
  tmp_key = H2OFrame.py_tmp_key()
  expr = "(= !{} (ls ))".format(tmp_key)
  rapids(expr)
  j = frame(tmp_key)
  fr = j['frames'][0]
  rows = fr['rows']
  veckeys = fr['vec_ids']
  cols = fr['columns']
  colnames = [col['label'] for col in cols]
  vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows)
  fr = H2OFrame(vecs=vecs)
  fr.setNames(["keys"])
  print "First 10 Keys: "
  fr.show()
  return as_list(fr, use_pandas=False)


def frame(frame_id):
  """
  Retrieve metadata for a id that points to a Frame.

  :param frame_id: A pointer to a Frame  in H2O.
  :return: Meta information on the frame
  """
  return H2OConnection.get_json("Frames/" + urllib.quote(frame_id))

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
  frame_summary =  H2OConnection.get_json("Frames/" + urllib.quote(key) + "/summary")
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

  java = H2OConnection.get( "Models.java/"+model_id )
  file_path = path + "/" + model_id + ".java"
  if path == "": print java.text
  else:
    with open(file_path, 'w') as f:
      f.write(java.text)

def download_csv(data, filename):
  '''
  Download an H2O data set to a CSV file on the local disk.
  Warning: Files located on the H2O server may be very large! Make
  sure you have enough hard drive space to accomodate the entire file.
  :param data: an H2OFrame object to be downloaded.
  :param filename:A string indicating the name that the CSV file should be
  should be saved to.
  :return: None
  '''
  if not isinstance(data, H2OFrame): raise(ValueError, "`data` argument must be an H2OFrame, but got "
                                                       "{0}".format(type(data)))
  url = 'http://' + H2OConnection.ip() + ':' + str(H2OConnection.port()) + '/3/DownloadDataset?frame_id=' + \
        data.send_frame()
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

def ddply(frame,cols,fun):
  return frame.ddply(cols,fun)

def group_by(frame,cols,aggregates):
  return frame.group_by(cols,aggregates)

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

  # check to see if we can use pandas
  found_pandas=False
  try:
    imp.find_module('pandas')  # if have pandas, use this to eat a frame
    found_pandas = True
  except ImportError:
    found_pandas = False

  # if frame, download the frame and jam into lol or pandas df
  if isinstance(data, H2OFrame):
    fr = H2OFrame.send_frame(data)
    res = _as_data_frame(fr, use_pandas and found_pandas)
    removeFrameShallow(fr)
    return res

  if isinstance(data, Expr):
    if data.is_local(): return data._data
    if data.is_pending():
      data.eager()
      if data.is_local(): return [data._data] if isinstance(data._data, list) else [[data._data]]
    return _as_data_frame(data._data, use_pandas and found_pandas)

  if isinstance(data, H2OVec):
    if data._expr.is_local(): return data._expr._data
    if data._expr.is_pending():
      data._expr.eager()
      if data._expr.is_local(): return [[data._expr._data]]

    return as_list(H2OFrame(vecs=[data]), use_pandas)

def _as_data_frame(id, use_pandas):
  url = 'http://' + H2OConnection.ip() + ':' + str(H2OConnection.port()) + "/3/DownloadDataset?frame_id=" + urllib.quote(id) + "&hex_string=false"
  response = urllib2.urlopen(url)
  if use_pandas:
    import pandas
    return pandas.read_csv(response, low_memory=False)
  else:
    cr = csv.reader(response)
    rows = []
    for row in cr: rows.append(row)
    return rows

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
def all(data)     : return _simple_un_math_op("all", data)

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

def asnumeric(data) : return data.asnumeric()
def transpose(data) : return data.transpose()
def anyfactor(data) : return data.anyfactor()

# munging ops with args
def signif(data, digits=6)       : return data.signif(digits=digits)
def round(data, digits=0)        : return data.round(digits=digits)
def match(data, table, nomatch=0): return data.match(table, nomatch=nomatch)
def table(data1,data2=None):
  if not data2: return data1.table()
  else: return data1.table(data2=data2)
def scale(data,center=True,scale=True): return data.scale(center=center, scale=scale)
def setLevel(data, level)  : return data.setLevel(level=level)
def setLevels(data, levels): return data.setLevels(levels=levels)
def levels(data, col=0)    : return data.levels(col=col)
def nlevels(data, col=0)   : return data.nlevels(col=col)
def as_date(data, format)  : return data.as_date(format=format)

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
