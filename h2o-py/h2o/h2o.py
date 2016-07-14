from __future__ import print_function
from __future__ import absolute_import
import warnings

warnings.simplefilter('always', DeprecationWarning)
import os, sys
import os.path
from future.standard_library import install_aliases
from past.builtins import basestring
try:
  install_aliases()
except AttributeError:
  if os.path.exists(os.path.join(sys.path[0], "test.py")):
    print("File named `test` is conflicting with python module `test` used by the future library.")

import re
from six import PY3
import copy
from .utils.shared_utils import _quoted, _is_list_of_lists, _gen_header, _py_tmp_key, quote, urlopen
from .connection import H2OConnection
from .expr import ExprNode
from .job import H2OJob
from .frame import H2OFrame
from .estimators.estimator_base import H2OEstimator
from .estimators.deeplearning import H2OAutoEncoderEstimator
from .estimators.deeplearning import H2ODeepLearningEstimator
from .estimators.gbm import H2OGradientBoostingEstimator
from .estimators.glm import H2OGeneralizedLinearEstimator
from .estimators.glrm import H2OGeneralizedLowRankEstimator
from .estimators.kmeans import H2OKMeansEstimator
from .estimators.naive_bayes import H2ONaiveBayesEstimator
from .estimators.random_forest import H2ORandomForestEstimator
from .grid.grid_search import H2OGridSearch
from .transforms.decomposition import H2OPCA
from .transforms.decomposition import H2OSVD


def lazy_import(path):
  """Import a single file or collection of files.

  Parameters
  ----------
    path : str
      A path to a data file (remote or local).
  """
  return [_import(p)[0] for p in path] if isinstance(path, (list, tuple)) else _import(path)


def _import(path):
  j = H2OConnection.get_json(url_suffix="ImportFiles", path=path)
  if j['fails']: raise ValueError("ImportFiles of " + path + " failed on " + str(j['fails']))
  return j['destination_frames']


def upload_file(path, destination_frame="", header=(-1,0,1), sep="", col_names=None, col_types=None, na_strings=None):
  """Upload a dataset at the path given from the local machine to the H2O cluster. Does a single-threaded push to H2O.
  Also see import_file. 
  
  Parameters
  ----------
    path : str
      A path specifying the location of the data to upload.

    destination_frame : str, optional
      The unique hex key assigned to the imported file. If none is given, a key will
      automatically be generated.

    header : int, optional
      -1 means the first line is data, 0 means guess, 1 means first line is header.

    sep : str, optional
      The field separator character. Values on each line of the file are separated by
      this character. If sep = "", the parser will automatically detect the separator.

    col_names : list, optional
      A list of column names for the file.

    col_types : list or dict, optional
      A list of types or a dictionary of column names to types to specify whether columns
      should be forced to a certain type upon import parsing. If a list, the types for
      elements that are None will be guessed. The possible types a column may have are
      "unknown" - this will force the column to be parsed as all NA
      "uuid"    - the values in the column must be true UUID or will be parsed as NA
      "string"  - force the column to be parsed as a string
      "numeric" - force the column to be parsed as numeric. H2O will handle the
      compression of the numeric data in the optimal manner.
      "enum"    - force the column to be parsed as a categorical column.
      "time"    - force the column to be parsed as a time column. H2O will attempt to
      parse the following list of date time formats
      date      - "yyyy-MM-dd", "yyyy MM dd", "dd-MMM-yy", "dd MMM yy"
      time      - "HH:mm:ss", "HH:mm:ss:SSS", "HH:mm:ss:SSSnnnnnn", "HH.mm.ss" "HH.mm.ss.SSS",
      "HH.mm.ss.SSSnnnnnn"
      Times can also contain "AM" or "PM".

    na_strings : list or dict, optional
      A list of strings, or a list of lists of strings (one list per column), or a
      dictionary of column names to strings which are to be interpreted as missing values.

  Returns
  -------
    A new H2OFrame instance.

  Examples
  --------
    >>> import h2o as ml
    >>> ml.upload_file(path="/path/to/local/data", destination_frame="my_local_data")
    ...
  """
  return H2OFrame()._upload_parse(path, destination_frame, header, sep, col_names, col_types, na_strings)


def import_file(path=None, destination_frame="", parse=True, header=(-1, 0, 1), sep="",
                col_names=None, col_types=None, na_strings=None):
    """Have H2O import a dataset into memory. The path to the data must be a valid path for
    each node in the H2O cluster. If some node in the H2O cluster cannot see the file, then
    an exception will be thrown by the H2O cluster. Does a parallel/distributed multi-threaded pull 
    of the data. Also see upload_file.

    Parameters
    ----------
      path : str
        A path specifying the location of the data to import.

      destination_frame : str, optional
        The unique hex key assigned to the imported file. If none is given, a key will
        automatically be generated.

      parse : bool, optional
        A logical value indicating whether the file should be parsed after import.

      header : int, optional
        -1 means the first line is data, 0 means guess, 1 means first line is header.

      sep : str, optional
        The field separator character. Values on each line of the file are separated by this
        character. If sep = "", the parser will automatically detect the separator.

      col_names : list, optional
        A list of column names for the file.

      col_types : list or dict, optional
        A list of types or a dictionary of column names to types to specify whether columns
        should be forced to a certain type upon import parsing. If a list, the types for
        elements that are None will be guessed. The possible types a column may have are:
        "unknown" - this will force the column to be parsed as all NA
        "uuid"    - the values in the column must be true UUID or will be parsed as NA
        "string"  - force the column to be parsed as a string
        "numeric" - force the column to be parsed as numeric. H2O will handle the
        compression of the numeric data in the optimal manner.
        "enum"    - force the column to be parsed as a categorical column.
        "time"    - force the column to be parsed as a time column. H2O will attempt to
        parse the following list of date time formats
        date - "yyyy-MM-dd", "yyyy MM dd", "dd-MMM-yy", "dd MMM yy"
        time - "HH:mm:ss", "HH:mm:ss:SSS", "HH:mm:ss:SSSnnnnnn", "HH.mm.ss" "HH.mm.ss.SSS",
        "HH.mm.ss.SSSnnnnnn"
        Times can also contain "AM" or "PM".

      na_strings : list or dict, optional
        A list of strings, or a list of lists of strings (one list per column), or a
        dictionary of column names to strings which are to be interpreted as missing values.

    Returns
    -------
      A new H2OFrame instance.
    """
    if not parse:
      return lazy_import(path)

    return H2OFrame()._import_parse(path, destination_frame, header, sep, col_names,
                                    col_types, na_strings)

def import_sql_table(connection_url, table, username, password, columns=None, optimize=None):
  """Import SQL table to H2OFrame in memory. Assumes that the SQL table is not being updated and is stable.
  Runs multiple SELECT SQL queries concurrently for parallel ingestion. 
  Be sure to start the h2o.jar in the terminal with your downloaded JDBC driver in the classpath: 
    `java -cp <path_to_h2o_jar>:<path_to_jdbc_driver_jar> water.H2OApp`
  Also see h2o.import_sql_select.
  Currently supported SQL databases are MySQL, PostgreSQL, and MariaDB. Support for Oracle 12g and Microsoft SQL Server 
  is forthcoming.
  
  Parameters
  ----------
    connection_url : str
      URL of the SQL database connection as specified by the Java Database Connectivity (JDBC) Driver.
      For example, "jdbc:mysql://localhost:3306/menagerie?&useSSL=false"
      
    table : str
      Name of SQL table
      
    username : str
      Username for SQL server
      
    password : str
      Password for SQL server
      
    columns : list of strings, optional
      A list of column names to import from SQL table. Default is to import all columns.
      
    optimize : bool, optional, default is True
      Optimize import of SQL table for faster imports. Experimental.  
      
  Returns
  -------
    H2OFrame containing data of specified SQL table
  
  Examples
  --------
    >>> conn_url = "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false"
    >>> table = "citibike20k"
    >>> username = "root"
    >>> password = "abc123"
    >>> my_citibike_data = h2o.import_sql_table(conn_url, table, username, password)
  """
  if columns is not None: 
    if not isinstance(columns, list): raise ValueError("`columns` must be a list of column names")
    columns = ', '.join(columns)
  p = {}
  p.update({k:v for k,v in locals().items() if k is not "p"})
  p["_rest_version"] = 99
  j = H2OJob(H2OConnection.post_json(url_suffix="ImportSQLTable", **p), "Import SQL Table").poll()
  return get_frame(j.dest_key)

def import_sql_select(connection_url, select_query, username, password, optimize=None):
  """Imports the SQL table that is the result of the specified SQL query to H2OFrame in memory. 
  Creates a temporary SQL table from the specified sql_query.
  Runs multiple SELECT SQL queries on the temporary table concurrently for parallel ingestion, then drops the table. 
  Be sure to start the h2o.jar in the terminal with your downloaded JDBC driver in the classpath: 
    `java -cp <path_to_h2o_jar>:<path_to_jdbc_driver_jar> water.H2OApp`
  Also see h2o.import_sql_table.
  Currently supported SQL databases are MySQL, PostgreSQL, and MariaDB. Support for Oracle 12g and Microsoft SQL Server 
  is forthcoming.
  
  Parameters
  ----------
    connection_url : str
      URL of the SQL database connection as specified by the Java Database Connectivity (JDBC) Driver.
      For example, "jdbc:mysql://localhost:3306/menagerie?&useSSL=false"
      
    select_query : str
      SQL query starting with `SELECT` that returns rows from one or more database tables.
      
    username : str
      Username for SQL server
      
    password : str
      Password for SQL server
      
    optimize : bool, optional, default is True
      Optimize import of SQL table for faster imports. Experimental.  
      
  Returns
  -------
    H2OFrame containing data of specified SQL select query
    
  Examples
  --------
    >>> conn_url = "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false"
    >>> select_query = "SELECT bikeid from citibike20k"
    >>> username = "root"
    >>> password = "abc123"
    >>> my_citibike_data = h2o.import_sql_select(conn_url, select_query, username, password)
  """
  p = {}
  p.update({k:v for k,v in locals().items() if k is not "p"})
  p["_rest_version"] = 99
  j = H2OJob(H2OConnection.post_json(url_suffix="ImportSQLTable", **p), "Import SQL Table").poll()
  return get_frame(j.dest_key)

def parse_setup(raw_frames, destination_frame="", header=(-1,0,1), separator="", column_names=None, column_types=None, na_strings=None):
  """During parse setup, the H2O cluster will make several guesses about the attributes of
  the data. This method allows a user to perform corrective measures by updating the
  returning dictionary from this method. This dictionary is then fed into `parse_raw` to
  produce the H2OFrame instance.

  Parameters
  ----------
    raw_frames : H2OFrame
      A collection of imported file frames

    destination_frame : str, optional
      The unique hex key assigned to the imported file. If none is given, a key will
      automatically be generated.

    parse : bool, optional
      A logical value indicating whether the file should be parsed after import.

    header : int, optional
      -1 means the first line is data, 0 means guess, 1 means first line is header.

    sep : str, optional
      The field separator character. Values on each line of the file are separated by this
       character. If sep = "", the parser will automatically detect the separator.

    col_names : list, optional
      A list of column names for the file.

    col_types : list or dict, optional
        A list of types or a dictionary of column names to types to specify whether columns
        should be forced to a certain type upon import parsing. If a list, the types for
        elements that are None will be guessed. The possible types a column may have are:
        "unknown" - this will force the column to be parsed as all NA
        "uuid"    - the values in the column must be true UUID or will be parsed as NA
        "string"  - force the column to be parsed as a string
        "numeric" - force the column to be parsed as numeric. H2O will handle the
        compression of the numeric data in the optimal manner.
        "enum"    - force the column to be parsed as a categorical column.
        "time"    - force the column to be parsed as a time column. H2O will attempt to
        parse the following list of date time formats
        date - "yyyy-MM-dd", "yyyy MM dd", "dd-MMM-yy", "dd MMM yy"
        time - "HH:mm:ss", "HH:mm:ss:SSS", "HH:mm:ss:SSSnnnnnn", "HH.mm.ss" "HH.mm.ss.SSS",
        "HH.mm.ss.SSSnnnnnn"
        Times can also contain "AM" or "PM".

    na_strings : list or dict, optional
      A list of strings, or a list of lists of strings (one list per column), or a
      dictionary of column names to strings which are to be interpreted as missing values.

  Returns
  -------
    A dictionary is returned containing all of the guesses made by the H2O back end.
  """

  # The H2O backend only accepts things that are quoted
  if isinstance(raw_frames, basestring): raw_frames = [raw_frames]

  # temporary dictionary just to pass the following information to the parser: header, separator
  kwargs = {}
  # set header
  if header != (-1,0,1):
    if header not in (-1, 0, 1): raise ValueError("header should be -1, 0, or 1")
    kwargs["check_header"] = header

  # set separator
  if separator:
    if not isinstance(separator, basestring) or len(separator) != 1: raise ValueError("separator should be a single character string")
    kwargs["separator"] = ord(separator)

  j = H2OConnection.post_json(url_suffix="ParseSetup", source_frames=[_quoted(id) for id in raw_frames], **kwargs)
  if j['warnings']:
    for w in j['warnings']:
      warnings.warn(w)
  if destination_frame: j["destination_frame"] = destination_frame.replace("%",".").replace("&",".") # TODO: really should be url encoding...
  if column_names is not None:
    if not isinstance(column_names, list): raise ValueError("col_names should be a list")
    if len(column_names) != len(j["column_types"]): raise ValueError("length of col_names should be equal to the number of columns")
    j["column_names"] = column_names
  if column_types is not None:
    if isinstance(column_types, dict):
      #overwrite dictionary to ordered list of column types. if user didn't specify column type for all names, use type provided by backend
      if j["column_names"] is None:  # no colnames discovered! (C1, C2, ...)
        j["column_names"] = _gen_header(j["number_columns"])
      if not set(column_types.keys()).issubset(set(j["column_names"])): raise ValueError("names specified in col_types is not a subset of the column names")
      idx = 0
      column_types_list = []
      for name in j["column_names"]:
        if name in column_types:
          column_types_list.append(column_types[name])
        else:
          column_types_list.append(j["column_types"][idx])
        idx += 1
      column_types = column_types_list
    elif isinstance(column_types, list):
      if len(column_types) != len(j["column_types"]): raise ValueError("length of col_types should be equal to the number of columns")
      column_types = [column_types[i] if column_types[i] else j["column_types"][i] for i in range(len(column_types))]
    else:  # not dictionary or list
      raise ValueError("col_types should be a list of types or a dictionary of column names to types")
    j["column_types"] = column_types
  if na_strings is not None:
    if isinstance(na_strings, dict):
      #overwrite dictionary to ordered list of lists of na_strings
      if not j["column_names"]: raise ValueError("column names should be specified")
      if not set(na_strings.keys()).issubset(set(j["column_names"])): raise ValueError("names specified in na_strings is not a subset of the column names")
      j["na_strings"] = [[] for _ in range(len(j["column_names"]))]
      for name, na in na_strings.items():
        idx = j["column_names"].index(name)
        if isinstance(na, basestring): na = [na]
        for n in na: j["na_strings"][idx].append(_quoted(n))
    elif _is_list_of_lists(na_strings):
      if len(na_strings) != len(j["column_types"]): raise ValueError("length of na_strings should be equal to the number of columns")
      j["na_strings"] = [[_quoted(na) for na in col] if col is not None else [] for col in na_strings]
    elif isinstance(na_strings, list):
      j["na_strings"] = [[_quoted(na) for na in na_strings]] * len(j["column_types"])
    else:  # not a dictionary or list
      raise ValueError("na_strings should be a list, a list of lists (one list per column), or a dictionary of column "
                       "names to strings which are to be interpreted as missing values")

  #quote column names and column types also when not specified by user
  if j["column_names"]: j["column_names"] = list(map(_quoted, j["column_names"]))
  j["column_types"] = list(map(_quoted, j["column_types"]))
  return j


def parse_raw(setup, id=None, first_line_is_header=(-1,0,1)):
  """Used in conjunction with lazy_import and parse_setup in order to make alterations
  before parsing.

  Parameters
  ----------
    setup : dict
      Result of h2o.parse_setup

    id : str, optional
      An id for the frame.

    first_line_is_header : int, optional
      -1,0,1 if the first line is to be used as the header

  Returns
  -------
    H2OFrame
  """
  if id: setup["destination_frame"] = _quoted(id).replace("%",".").replace("&",".")
  if first_line_is_header != (-1,0,1):
    if first_line_is_header not in (-1, 0, 1): raise ValueError("first_line_is_header should be -1, 0, or 1")
    setup["check_header"] = first_line_is_header
  fr = H2OFrame()
  fr._parse_raw(setup)
  return fr


def assign(data,xid):
  if data.frame_id == xid: ValueError("Desination key must differ input frame")
  data._ex = ExprNode("assign",xid,data)._eval_driver(False)
  data._ex._cache._id = xid
  data._ex._children = None
  return data


def get_model(model_id):
  """Return the specified model

  Parameters
  ----------
    model_id : str
      The model identification in h2o

  Returns
  -------
    Subclass of H2OEstimator
  """
  model_json = H2OConnection.get_json("Models/"+model_id)["models"][0]
  algo = model_json["algo"]
  if   algo == "svd":          m = H2OSVD()
  elif algo == "pca":          m = H2OPCA()
  elif algo == "drf":          m = H2ORandomForestEstimator()
  elif algo == "naivebayes":   m = H2ONaiveBayesEstimator()
  elif algo == "kmeans":       m = H2OKMeansEstimator()
  elif algo == "glrm":         m = H2OGeneralizedLowRankEstimator()
  elif algo == "glm":          m = H2OGeneralizedLinearEstimator()
  elif algo == "gbm":          m = H2OGradientBoostingEstimator()
  elif algo == "deeplearning" and model_json["output"]["model_category"]=="AutoEncoder": m = H2OAutoEncoderEstimator()
  elif algo == "deeplearning":  m = H2ODeepLearningEstimator()
  else:
    raise ValueError("Unknown algo type: " + algo)
  m._resolve_model(model_id, model_json)
  return m

def get_grid(grid_id):
  """Return the specified grid

  Parameters
  ----------
    grid_id : str
      The grid identification in h2o

  Returns
  -------
    H2OGridSearch instance
  """
  grid_json = H2OConnection.get_json("Grids/"+grid_id, _rest_version=99)
  models = [get_model(key['name']) for key in grid_json['model_ids']]
  #get first model returned in list of models from grid search to get model class (binomial, multinomial, etc)
  first_model_json = H2OConnection.get_json("Models/"+grid_json['model_ids'][0]['name'])['models'][0]
  gs = H2OGridSearch(None, {}, grid_id)
  gs._resolve_grid(grid_id, grid_json, first_model_json)
  gs.models = models
  hyper_params = {param:set() for param in gs.hyper_names}
  for param in gs.hyper_names:
    for model in models:
      hyper_params[param].add(model.full_parameters[param][u'actual_value'][0])
  hyper_params = {str(param):list(vals) for param, vals in hyper_params.items()}
  gs.hyper_params = hyper_params
  gs.model = model.__class__()
  return gs
  

def get_frame(frame_id):
  """Obtain a handle to the frame in H2O with the frame_id key.

  Returns
  -------
    H2OFrame
  """
  return H2OFrame.get_frame(frame_id)


def ou():
  """Where is my baguette!?

  Returns
  -------
    The name of the baguette. oh uhr uhr huhr
  """
  from inspect import stack

  return stack()[2][1]


def no_progress():
  """Disable the progress bar from flushing to stdout. The completed progress bar is
  printed when a job is complete so as to demarcate a log file.
  """
  H2OJob.__PROGRESS_BAR__ = False


def show_progress():
  """Enable the progress bar. (Progress bar is enabled by default)."""
  H2OJob.__PROGRESS_BAR__ = True


def log_and_echo(message):
  """Log a message on the server-side logs
  This is helpful when running several pieces of work one after the other on a single H2O
  cluster and you want to make a notation in the H2O server side log where one piece of
  work ends and the next piece of work begins.

  Sends a message to H2O for logging. Generally used for debugging purposes.

  Parameters
  ----------
    message : str
      A character string with the message to write to the log.

  """
  if message is None: message = ""
  H2OConnection.post_json("LogAndEcho", message=message)


def remove(x):
  """Remove object(s) from H2O.

  Parameters
  ----------
    x : H2OFrame, H2OEstimator, or basestring, or a list/tuple of those things.
      The object(s) or unique id(s) pointing to the object(s) to be removed.
  """
  if not isinstance(x, (list, tuple)): x = (x,)
  for xi in x:
    if xi is None:
      raise ValueError("h2o.remove with no object is not supported, for your protection")
    if isinstance(xi, H2OFrame):
      xi_id = xi._ex._cache._id      # String or None
      if xi_id is None: return       # Lazy frame, never evaluated, nothing in cluster
      rapids("(rm {})".format(xi_id))
      xi._ex = None
    elif isinstance(xi, H2OEstimator):
      H2OConnection.delete("DKV/"+xi.model_id)
      xi._id = None
    elif isinstance(xi, basestring):
      # string may be a Frame key name part of a rapids session... need to call rm thru rapids here
      try:
        rapids("(rm {})".format(xi))
      except:
        H2OConnection.delete("DKV/"+xi)
    else:
      raise ValueError('input to h2o.remove must one of: H2OFrame, H2OEstimator, or basestring')


def remove_all():
  """Remove all objects from H2O."""
  H2OConnection.delete("DKV")


def rapids(expr):
  """Execute a Rapids expression.

  Parameters
  ----------
  expr : str
    The rapids expression (ascii string).

  Returns
  -------
    The JSON response (as a python dictionary) of the Rapids execution
  """
  return ExprNode.rapids(expr)


def ls():
  """List Keys on an H2O Cluster

  Returns
  -------
    A list of keys in the current H2O instance.
  """
  return H2OFrame._expr(expr=ExprNode("ls")).as_data_frame(use_pandas=True)


def frame(frame_id, exclude=""):
  """Retrieve metadata for an id that points to a Frame.

  Parameters
  ----------
  frame_id : str
    A pointer to a Frame in H2O.

  Returns
  -------
    Python dict containing the frame meta-information
  """
  return H2OConnection.get_json("Frames/" + frame_id + exclude)


def frames():
  """Retrieve all the Frames.

  Returns
  -------
    Meta information on the frames
  """
  return H2OConnection.get_json("Frames")


def download_pojo(model,path="", get_jar=True):
  """Download the POJO for this model to the directory specified by path (no trailing
  slash!). If path is "", then dump to screen.

  Parameters
  ----------
    model : H2OModel
      Retrieve this model's scoring POJO.

    path : str
      An absolute path to the directory where POJO should be saved.

    get_jar : bool
      Retrieve the h2o-genmodel.jar also.
  """
  java = H2OConnection.get( "Models.java/"+model.model_id )

  # HACK: munge model._id so that it conforms to Java class name. For example, change K-means to K_means.
  # TODO: clients should extract Java class name from header.
  regex = re.compile("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]")
  pojoname = regex.sub("_",model.model_id)

  filepath = path + "/" + pojoname + ".java"
  print("Filepath: {}".format(filepath))
  if path == "": print(java.text)
  else:
    with open(filepath, 'wb') as f:
      f.write(java.text.encode("utf-8"))
  if get_jar and path!="":
    url = H2OConnection.make_url("h2o-genmodel.jar")
    filename = path + "/" + "h2o-genmodel.jar"
    response = urlopen()(url)
    with open(filename, "wb") as f:
      f.write(response.read())


def download_csv(data, filename):
  """Download an H2O data set to a CSV file on the local disk.

  Warning: Files located on the H2O server may be very large! Make sure you have enough
  hard drive space to accommodate the entire file.

  Parameters
  ----------
    data : H2OFrame
      An H2OFrame object to be downloaded.

    filename : str
      A string indicating the name that the CSV file should be should be saved to.
  """
  if not isinstance(data, H2OFrame):
    raise ValueError
  url = H2OConnection.make_url("DownloadDataset",3) + "?frame_id={}&hex_string=false".format(data.frame_id)
  with open(filename, 'wb') as f:
    f.write(urlopen()(url).read())


def download_all_logs(dirname=".", filename=None):
  """Download H2O Log Files to Disk

  Parameters
  ----------
    dirname : str, optional
      A character string indicating the directory that the log file should be saved in.

    filename : str, optional
      A string indicating the name that the CSV file should be

  Returns
  -------
    Path of logs written.
  """
  url = 'http://{}:{}/3/Logs/download'.format(H2OConnection.ip(),H2OConnection.port())
  opener = urlopen()
  response = opener(url)

  if not os.path.exists(dirname): os.mkdir(dirname)
  if filename == None:
    if PY3: headers = [h[1] for h in response.headers._headers]
    else:   headers = response.headers.headers
    for h in headers:
      if 'filename=' in h:
        filename = h.split("filename=")[1].strip()
        break
  path = os.path.join(dirname,filename)
  response = opener(url).read()

  print("Writing H2O logs to " + path)
  with open(path, 'wb') as f: f.write(response)
  return path


def save_model(model, path="", force=False):
  """Save an H2O Model Object to Disk.

  Parameters
  ----------
    model :  H2OModel
      The model object to save.

    path : str
      A path to save the model at (hdfs, s3, local)

    force : bool
      Overwrite destination directory in case it exists or throw exception if set to false.

  Returns
  -------
    The path of the saved model (string)
  """
  path=os.path.join(os.getcwd() if path=="" else path,model.model_id)
  return H2OConnection.get_json("Models.bin/"+model.model_id,dir=path,force=force,_rest_version=99)["dir"]


def load_model(path):
  """
  Load a saved H2O model from disk.

  Parameters
  ----------
  path : str
    The full path of the H2O Model to be imported.

  Returns
  -------
    H2OEstimator object

  Examples
  --------
    >>> path = h2o.save_mode(my_model,dir=my_path)
    >>> h2o.load_model(path)
  """
  res = H2OConnection.post_json("Models.bin/",dir=path,_rest_version=99)
  return get_model(res['models'][0]['model_id']['name'])


def cluster_status():
  """This is possibly confusing because this can come back without warning,
  but if a user tries to do any remoteSend, they will get a "cloud sick warning"
  Retrieve information on the status of the cluster running H2O.
  """
  cluster_json = H2OConnection.get_json("Cloud?skip_ticks=true")

  print("Version: {0}".format(cluster_json['version']))
  print("Cloud name: {0}".format(cluster_json['cloud_name']))
  print("Cloud size: {0}".format(cluster_json['cloud_size']))
  if cluster_json['locked']: print("Cloud is locked\n")
  else: print("Accepting new members\n")
  if cluster_json['nodes'] == None or len(cluster_json['nodes']) == 0:
    print("No nodes found")
    return

  status = []
  for node in cluster_json['nodes']:
    for k, v in zip(node.keys(),node.values()):
      if k in ["h2o", "healthy", "last_ping", "num_cpus", "sys_load", 
               "mem_value_size", "free_mem", "pojo_mem", "swap_mem",
               "free_disk", "max_disk", "pid", "num_keys", "tcps_active",
               "open_fds", "rpcs_active"]: status.append(k+": {0}".format(v))
    print(', '.join(status))
    print()


def init(ip="localhost", port=54321, start_h2o=True, enable_assertions=True,
         license=None, nthreads=-1, max_mem_size=None, min_mem_size=None, ice_root=None,
         strict_version_check=True, proxy=None, https=False, insecure=False, username=None, 
         password=None, cluster_name=None, force_connect=False, max_mem_size_GB=None, min_mem_size_GB=None, proxies=None, size=None):
  """Initiate an H2O connection to the specified ip and port.

  Parameters
  ----------
  ip : str
    A string representing the hostname or IP address of the server where H2O is running.
  port : int
    A port, default is 54321
  start_h2o : bool
    A boolean dictating whether this module should start the H2O jvm. An attempt is made
    anyways if _connect fails.
  enable_assertions : bool
    If start_h2o, pass `-ea` as a VM option.
  license : str
    If not None, is a path to a license file.
  nthreads : int
    Number of threads in the thread pool. This relates very closely to the number of CPUs used. 
    -1 means use all CPUs on the host. A positive integer specifies the number of CPUs directly. 
    This value is only used when Python starts H2O.
  max_mem_size : int
    Maximum heap size (jvm option Xmx) in gigabytes.
  min_mem_size : int
    Minimum heap size (jvm option Xms) in gigabytes.
  ice_root : str
    A temporary directory (default location is determined by tempfile.mkdtemp()) to hold
    H2O log files.
  strict_version_check : bool 
    Setting this to False is unsupported and should only be done when advised by technical support.
  proxy : dict
    A dictionary with keys 'ftp', 'http', 'https' and values that correspond to a proxy path.
  https : bool
    Set this to True to use https instead of http.
  insecure : bool
    Set this to True to disable SSL certificate checking.
  username : str
    Username to login with.
  password : str
    Password to login with.
  cluster_name : str
    Cluster to login to.
  force_connect : bool
    When set to True, a connection to the cluster will attempt to be established regardless of its reported health.
  max_mem_size_GB : DEPRECATED
    Use max_mem_size instead.
  min_mem_size_GB : DEPRECATED
    Use min_mem_size instead.
  proxies : DEPRECATED
    Use proxy instead.
  size : DEPRECATED
    Size is deprecated.

  Examples
  --------
  Using the 'proxy' parameter

  >>> import h2o
  >>> import urllib
  >>> proxy_dict = urllib.getproxies()
  >>> h2o.init(proxy=proxy_dict)
  Starting H2O JVM and connecting: ............... Connection successful!

  """
  H2OConnection(ip=ip, port=port,start_h2o=start_h2o,enable_assertions=enable_assertions,license=license,
                nthreads=nthreads,max_mem_size=max_mem_size,min_mem_size=min_mem_size,ice_root=ice_root,
                strict_version_check=strict_version_check,proxy=proxy,https=https,insecure=insecure,username=username,
                password=password,cluster_name=cluster_name,force_connect=force_connect,max_mem_size_GB=max_mem_size_GB,min_mem_size_GB=min_mem_size_GB,proxies=proxies,size=size)
  return None


def export_file(frame,path,force=False):
  """Export a given H2OFrame to a path on the machine this python session is currently
  connected to. To view the current session, call h2o.cluster_info().

  Parameters
  ----------
    frame : H2OFrame
      The Frame to save to disk.
    path : str
      The path to the save point on disk.
    force : bool
      Overwrite any preexisting file with the same path
  """
  H2OJob(H2OConnection.get_json("Frames/"+frame.frame_id+"/export/"+path+"/overwrite/"+("true" if force else "false")), "Export File").poll()


def cluster_info():
  """Display the current H2O cluster information.
  """
  H2OConnection._cluster_info()


def shutdown(conn=None, prompt=True):
  """Shut down the specified instance. All data will be lost.
  This method checks if H2O is running at the specified IP address and port,
  and if it is, shuts down that H2O instance.

  Parameters
  ----------
    conn : H2OConnection
      An H2OConnection object containing the IP address and port of the server running H2O.

    prompt : bool
      A logical value indicating whether to prompt the user before shutting down the H2O server.
  """
  if conn is None: conn = H2OConnection.current_connection()
  H2OConnection._shutdown(conn=conn, prompt=prompt)


def create_frame(id = None, rows = 10000, cols = 10, randomize = True, value = 0, real_range = 100,
                 categorical_fraction = 0.2, factors = 100, integer_fraction = 0.2, integer_range = 100,
                 binary_fraction = 0.1, binary_ones_fraction = 0.02, time_fraction = 0, string_fraction = 0,
                 missing_fraction = 0.01, response_factors = 2, has_response = False, seed=None, seed_for_column_types=None):
    """Data Frame Creation in H2O.
    Creates a data frame in H2O with real-valued, categorical, integer,
    and binary columns specified by the user.

    Parameters
    ----------
      id : str
        A string indicating the destination key. If empty, this will be auto-generated
        by H2O.

      rows : int
        The number of rows of data to generate.

      cols : int
        The number of columns of data to generate. Excludes the response column if
        has_response == True.

      randomize : bool
        A logical value indicating whether data values should be randomly generated.
        This must be TRUE if either categorical_fraction or integer_fraction is non-zero.

      value : int
        If randomize == FALSE, then all real-valued entries will be set to this value.

      real_range : float
        The range of randomly generated real values.

      categorical_fraction : float
        The fraction of total columns that are categorical.

      factors : int
        The number of (unique) factor levels in each categorical column.

      integer_fraction : float
        The fraction of total columns that are integer-valued.

      integer_range : list
        The range of randomly generated integer values.

      binary_fraction : float
        The fraction of total columns that are binary-valued.

      binary_ones_fraction : float
        The fraction of values in a binary column that are set to 1.

      time_fraction : float
        The fraction of randomly created date/time columns

      string_fraction : float
        The fraction of randomly created string columns

      missing_fraction : float
        The fraction of total entries in the data frame that are set to NA.

      response_factors : int
        If has_response == TRUE, then this is the number of factor levels in the response
        column.

      has_response : bool
        A logical value indicating whether an additional response column should be
        pre-pended to the final H2O data frame. If set to TRUE, the total number
        of columns will be cols+1.

      seed : int
        A seed used to generate random values when randomize = TRUE.

      seed_for_column_types : int
        A seed used to generate random column types when randomize = TRUE.

    Returns
    -------
      H2OFrame
    """
    parms = {"dest": _py_tmp_key(append=H2OConnection.session_id()) if id is None else id,
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
         "time_fraction": time_fraction,
         "string_fraction": string_fraction,
         "missing_fraction": missing_fraction,
         "response_factors": response_factors,
         "has_response": has_response,
         "seed": -1 if seed is None else seed,
         "seed_for_column_types": -1 if seed_for_column_types is None else seed_for_column_types,
         }
    H2OJob(H2OConnection.post_json("CreateFrame", **parms), "Create Frame").poll()
    return get_frame(parms["dest"])



def interaction(data, factors, pairwise, max_factors, min_occurrence, destination_frame=None):
  """
  Categorical Interaction Feature Creation in H2O.
  Creates a frame in H2O with n-th order interaction features between categorical columns, as specified by
  the user.

  Parameters
  ----------
    data : H2OFrame
      the H2OFrame that holds the target categorical columns.

    factors : list
      factors Factor columns (either indices or column names).

    pairwise : bool
      Whether to create pairwise interactions between factors (otherwise create one
      higher-order interaction). Only applicable if there are 3 or more factors.

    max_factors : int
      Max. number of factor levels in pair-wise interaction terms (if enforced, one extra
      catch-all factor will be made)

    min_occurrence : int
      Min. occurrence threshold for factor levels in pair-wise interaction terms

    destination_frame : str
      A string indicating the destination key. If empty, this will be auto-generated by H2O.

  Returns
  -------
    H2OFrame
  """
  factors = [data.names[n] if isinstance(n,int) else n for n in factors]
  parms = {"dest": _py_tmp_key(append=H2OConnection.session_id()) if destination_frame is None else destination_frame,
           "source_frame": data.frame_id,
           "factor_columns": [_quoted(f) for f in factors],
           "pairwise": pairwise,
           "max_factors": max_factors,
           "min_occurrence": min_occurrence,
           }
  H2OJob(H2OConnection.post_json("Interaction", **parms), "Interactions").poll()
  return get_frame(parms["dest"])


def as_list(data, use_pandas=True):
  """Convert an H2O data object into a python-specific object.

  WARNING! This will pull all data local!

  If Pandas is available (and use_pandas is True), then pandas will be used to parse the
  data frame. Otherwise, a list-of-lists populated by character data will be returned (so
  the types of data will all be str).

  Parameters
  ----------
    data : H2OFrame
      An H2O data object.

    use_pandas : bool
      Try to use pandas for reading in the data.

  Returns
  -------
    List of list (Rows x Columns).
  """
  return H2OFrame.as_data_frame(data, use_pandas=use_pandas)


def network_test():
  res = H2OConnection.get_json(url_suffix="NetworkTest")
  res["table"].show()

def set_timezone(tz):
  """Set the Time Zone on the H2O Cloud

  Parameters
  ----------
    tz : str
      The desired timezone.
  """
  ExprNode("setTimeZone", tz)._eager_scalar()

def get_timezone():
  """Get the Time Zone on the H2O Cloud

  Returns
  -------
    The time zone (string)
  """
  return ExprNode("getTimeZone")._eager_scalar()


def list_timezones():
  """Get a list of all the timezones

  Returns
  -------
    The time zones (as an H2OFrame)
  """
  return H2OFrame._expr(expr=ExprNode("listTimeZones"))._frame()


#  ALL DEPRECATED METHODS BELOW #

# the @h2o_deprecated decorator
def h2o_deprecated(newfun=None):
  def o(fun):
    def i(*args, **kwargs):
      print('\n')
      if newfun is None: raise DeprecationWarning("{} is deprecated.".format(fun.__name__))
      warnings.warn("{} is deprecated. Use {}.".format(fun.__name__,newfun.__name__), category=DeprecationWarning, stacklevel=2)
      return newfun(*args, **kwargs)
    return i
  return o

@h2o_deprecated(import_file)
def import_frame():
  """Deprecated for import_file.

  Parameters
  ----------
    path : str
      A path specifying the location of the data to import.

  Returns
  -------
    A new H2OFrame
  """

@h2o_deprecated()
def parse():
  """
  External use of parse is deprecated. parse has been renamed H2OFrame._parse for internal
  use.

  Parameters
  ----------
    setup : dict
      The result of calling parse_setup.

  Returns
  -------
    A new H2OFrame
  """
