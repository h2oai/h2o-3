import warnings
warnings.simplefilter('always', DeprecationWarning)
import os
import functools
import os.path
import re
import urllib
import urllib2
import imp
import tabulate
from connection import H2OConnection
from job import H2OJob
from expr import ExprNode
from frame import H2OFrame, _py_tmp_key
from model import H2OBinomialModel,H2OAutoEncoderModel,H2OClusteringModel,H2OMultinomialModel,H2ORegressionModel
import h2o_model_builder


__PROGRESS_BAR__ = True  # display & update progress bar while polling


def lazy_import(path):
  """
  Import a single file or collection of files.

  :param path: A path to a data file (remote or local).
  :return: A new H2OFrame
  """
  if isinstance(path,(list,tuple)): return [_import(p)[0] for p in path]
  elif os.path.isdir(path):         return _import(path)
  else:                             return [_import(path)[0]]

def _import(path):
  j = H2OConnection.get_json(url_suffix="ImportFiles", path=path)
  if j['fails']: raise ValueError("ImportFiles of " + path + " failed on " + str(j['fails']))
  return j['destination_frames']

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


def import_file(path=None):
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
  Used in conjunction with lazy_import and parse_setup in order to make alterations before parsing.

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
  fr._keep = True
  return fr

def _quoted(key):
  if key == None: return "\"\""
  is_quoted = len(re.findall(r'\"(.+?)\"', key)) != 0
  key = key if is_quoted  else "\"" + key + "\""
  return key

def assign(data,id):
  if data._computed:
    rapids(data._id,id)
  data._id = id
  data._keep=True  # named things are always safe
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
  if   model_type=="Binomial":    return H2OBinomialModel(model_id, model_json)
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

def ou():
  """
  Where is my baguette!?

  :return: the name of the baguette. oh uhr uhr huhr
  """
  from inspect import stack
  return stack()[2][1]

def no_progress():
  """
  Disable the progress bar from flushing to stdout. The completed progress bar is printed
  when a job is complete so as to demarcate a log file.

  :return: None
  """
  global __PROGRESS_BAR__
  __PROGRESS_BAR__=False

def do_progress():
  """
  Enable the progress bar. (Progress bar is enabled by default).

  :return: None
  """
  global __PROGRESS_BAR__
  __PROGRESS_BAR__=True

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
  return H2OConnection.post_json("Rapids", ast=urllib.quote(expr), _rest_version=99) if id is None else H2OConnection.post_json("Rapids", ast=urllib.quote(expr), id=id, _rest_version=99)

def ls():
  """
  List Keys on an H2O Cluster

  :return: Returns a list of keys in the current H2O instance
  """
  return H2OFrame(expr=ExprNode("ls")).as_data_frame()


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

def download_pojo(model,path="", get_jar=True):
  """
  Download the POJO for this model to the directory specified by path (no trailing slash!).
  If path is "", then dump to screen.

  :param model: Retrieve this model's scoring POJO.
  :param path:  An absolute path to the directory where POJO should be saved.
  :param get_jar: Retrieve the h2o genmodel jar also.
  :return: None
  """
  java = H2OConnection.get( "Models.java/"+model._id )
  file_path = path + "/" + model._id + ".java"
  if path == "": print java.text
  else:
    with open(file_path, 'w') as f:
      f.write(java.text)
  if get_jar and path!="":
    url = H2OConnection.make_url("h2o-genmodel.jar")
    filename = path + "/" + "h2o-genmodel.jar"
    response = urllib2.urlopen(url)
    with open(filename, "w") as f:
      f.write(response.read())

def download_csv(data, filename):
  """
  Download an H2O data set to a CSV file on the local disk.

  Warning: Files located on the H2O server may be very large! Make sure you have enough hard drive space to accommodate the entire file.

  :param data: An H2OFrame object to be downloaded.
  :param filename: A string indicating the name that the CSV file should be should be saved to.
  :return: None
  """
  data._eager()
  if not isinstance(data, H2OFrame): raise(ValueError, "`data` argument must be an H2OFrame, but got " + type(data))
  url = "http://{}:{}/3/DownloadDataset?frame_id={}".format(H2OConnection.ip(),H2OConnection.port(),data._id)
  with open(filename, 'w') as f: f.write(urllib2.urlopen(url).read())

def download_all_logs(dirname=".",filename=None):
  """
  Download H2O Log Files to Disk

  :param dirname: (Optional) A character string indicating the directory that the log file should be saved in.
  :param filename: (Optional) A string indicating the name that the CSV file should be
  :return: path of logs written (as a string)
  """
  url = 'http://{}:{}/Logs/download'.format(H2OConnection.ip(),H2OConnection.port())
  response = urllib2.urlopen(url)

  if not os.path.exists(dirname): os.mkdir(dirname)
  if filename == None:
    for h in response.headers.headers:
      if 'filename=' in h:
        filename = h.split("filename=")[1].strip()
        break
  path = os.path.join(dirname,filename)

  print "Writing H2O logs to " + path
  with open(path, 'w') as f: f.write(urllib2.urlopen(url).read())
  return path

def save_model(model, path="", force=False):
  """
  Save an H2O Model Object to Disk.

  :param model: The model object to save.
  :param path: A path to save the model at (hdfs, s3, local)
  :param force: Overwrite destination directory in case it exists or throw exception if set to false.
  :return: the path of the saved model (string)
  """
  path=os.path.join(os.getcwd() if path=="" else path,model._id)
  return H2OConnection.get_json("Models.bin/"+model._id,dir=path,force=force,_rest_version=99)["dir"]

def load_model(path):
  """
  Load a saved H2O model from disk.
  Example:
      >>> path = h2o.save_model(my_model,dir=my_path)
      >>> h2o.load_model(path)                         # use the result of save_model

  :param path: The full path of the H2O Model to be imported.
  :return: the model
  """
  res = H2OConnection.post_json("Models.bin/",dir=path,_rest_version=99)
  return get_model(res['models'][0]['model_id']['name'])

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
         license=None, max_mem_size_GB=None, min_mem_size_GB=None, ice_root=None, strict_version_check=False):
  """
  Initiate an H2O connection to the specified ip and port.

  :param ip: A string representing the hostname or IP address of the server where H2O is running.
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
  frame._eager()
  H2OJob(H2OConnection.get_json("Frames/"+frame._id+"/export/"+path+"/overwrite/"+("true" if force else "false")), "Export File").poll()

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

def deeplearning(x,y=None,validation_x=None,validation_y=None,training_frame=None,model_id=None,
                 overwrite_with_best_model=None,validation_frame=None,checkpoint=None,autoencoder=None,
                 use_all_factor_levels=None,activation=None,hidden=None,epochs=None,train_samples_per_iteration=None,
                 seed=None,adaptive_rate=None,rho=None,epsilon=None,rate=None,rate_annealing=None,rate_decay=None,
                 momentum_start=None,momentum_ramp=None,momentum_stable=None,nesterov_accelerated_gradient=None,
                 input_dropout_ratio=None,hidden_dropout_ratios=None,l1=None,l2=None,max_w2=None,initial_weight_distribution=None,
                 initial_weight_scale=None,loss=None,distribution=None,tweedie_power=None,score_interval=None,score_training_samples=None,
                 score_validation_samples=None,score_duty_cycle=None,classification_stop=None,regression_stop=None,quiet_mode=None,
                 max_confusion_matrix_size=None,max_hit_ratio_k=None,balance_classes=None,class_sampling_factors=None,
                 max_after_balance_size=None,score_validation_sampling=None,diagnostics=None,variable_importances=None,
                 fast_mode=None,ignore_const_cols=None,force_load_balance=None,replicate_training_data=None,single_node_mode=None,
                 shuffle_training_data=None,sparse=None,col_major=None,average_activation=None,sparsity_beta=None,
                 max_categorical_features=None,reproducible=None,export_weights_and_biases=None,offset_column=None,weights_column=None,
                 nfolds=None,fold_column=None,fold_assignment=None,keep_cross_validation_predictions=None):
  """
  Build a supervised Deep Learning model
  Performs Deep Learning neural networks on an H2OFrame

  :param x: An H2OFrame containing the predictors in the model.
  :param y: An H2OFrame of the response variable in the model.
  :param training_frame: (Optional) An H2OFrame. Only used to retrieve weights, offset, or nfolds columns, if they aren't already provided in x.
  :param model_id: (Optional) The unique id assigned to the resulting model. If none is given, an id will automatically be generated.
  :param overwrite_with_best_model: Logical. If True, overwrite the final model with the best model found during training. Defaults to True.
  :param validation_frame: (Optional) An H2OFrame object indicating the validation dataset used to construct the confusion matrix. If left blank, this defaults to the training data when nfolds = 0
  :param checkpoint: "Model checkpoint (either key or H2ODeepLearningModel) to resume training with."
  :param autoencoder: Enable auto-encoder for model building.
  :param use_all_factor_levels: Logical. Use all factor levels of categorical variance. Otherwise the first factor level is omitted (without loss of accuracy). Useful for variable importances and auto-enabled for autoencoder.
  :param activation: A string indicating the activation function to use. Must be either "Tanh", "TanhWithDropout", "Rectifier", "RectifierWithDropout", "Maxout", or "MaxoutWithDropout"
  :param hidden: Hidden layer sizes (e.g. c(100,100))
  :param epochs: How many times the dataset should be iterated (streamed), can be fractional
  :param train_samples_per_iteration: Number of training samples (globally) per MapReduce iteration. Special values are: 0 one epoch; -1 all available data (e.g., replicated training data); or -2 auto-tuning (default)
  :param seed: Seed for random numbers (affects sampling) - Note: only reproducible when running single threaded
  :param adaptive_rate: Logical. Adaptive learning rate (ADAELTA)
  :param rho: Adaptive learning rate time decay factor (similarity to prior updates)
  :param epsilon: Adaptive learning rate parameter, similar to learn rate annealing during initial training phase. Typical values are between 1.0e-10 and 1.0e-4
  :param rate: Learning rate (higher => less stable, lower => slower convergence)
  :param rate_annealing: Learning rate annealing: \eqn{(rate)/(1 + rate_annealing*samples)
  :param rate_decay: Learning rate decay factor between layers (N-th layer: \eqn{rate*\alpha^(N-1))
  :param momentum_start: Initial momentum at the beginning of training (try 0.5)
  :param momentum_ramp: Number of training samples for which momentum increases
  :param momentum_stable: Final momentum after the amp is over (try 0.99)
  :param nesterov_accelerated_gradient: Logical. Use Nesterov accelerated gradient (recommended)
  :param input_dropout_ratio: A fraction of the features for each training row to be omitted from training in order to improve generalization (dimension sampling).
  :param hidden_dropout_ratios: Input layer dropout ratio (can improve generalization) specify one value per hidden layer, defaults to 0.5
  :param l1: L1 regularization (can add stability and improve generalization, causes many weights to become 0)
  :param l2: L2 regularization (can add stability and improve generalization, causes many weights to be small)
  :param max_w2: Constraint for squared sum of incoming weights per unit (e.g. Rectifier)
  :param initial_weight_distribution: Can be "Uniform", "UniformAdaptive", or "Normal"
  :param initial_weight_scale: Uniform: -value ... value, Normal: stddev
  :param loss: Loss function: "Automatic", "CrossEntropy" (for classification only), "MeanSquare", "Absolute" (experimental) or "Huber" (experimental)
  :param distribution: A character string. The distribution function of the response. Must be "AUTO", "bernoulli", "multinomial", "poisson", "gamma", "tweedie", "laplace", "huber" or "gaussian"
  :param tweedie_power: Tweedie power (only for Tweedie distribution, must be between 1 and 2)
  :param score_interval: Shortest time interval (in secs) between model scoring
  :param score_training_samples: Number of training set samples for scoring (0 for all)
  :param score_validation_samples: Number of validation set samples for scoring (0 for all)
  :param score_duty_cycle: Maximum duty cycle fraction for scoring (lower: more training, higher: more scoring)
  :param classification_stop: Stopping criterion for classification error fraction on training data (-1 to disable)
  :param regression_stop: Stopping criterion for regression error (MSE) on training data (-1 to disable)
  :param quiet_mode: Enable quiet mode for less output to standard output
  :param max_confusion_matrix_size: Max. size (number of classes) for confusion matrices to be shown
  :param max_hit_ratio_k: Max number (top K) of predictions to use for hit ratio computation(for multi-class only, 0 to disable)
  :param balance_classes: Balance training data class counts via over/under-sampling (for imbalanced data)
  :param class_sampling_factors: Desired over/under-sampling ratios per class (in lexicographic order). If not specified, sampling factors will be automatically computed to obtain class balance during training. Requires balance_classes.
  :param max_after_balance_size: Maximum relative size of the training data after balancing class counts (can be less than 1.0)
  :param score_validation_sampling: Method used to sample validation dataset for scoring
  :param diagnostics: Enable diagnostics for hidden layers
  :param variable_importances: Compute variable importances for input features (Gedeon method) - can be slow for large networks)
  :param fast_mode: Enable fast mode (minor approximations in back-propagation)
  :param ignore_const_cols: Ignore constant columns (no information can be gained anyway)
  :param force_load_balance: Force extra load balancing to increase training speed for small datasets (to keep all cores busy)
  :param replicate_training_data: Replicate the entire training dataset onto every node for faster training
  :param single_node_mode: Run on a single node for fine-tuning of model parameters
  :param shuffle_training_data: Enable shuffling of training data (recommended if training data is replicated and train_samples_per_iteration is close to \eqn{numRows*numNodes
  :param sparse: Sparse data handling (Experimental)
  :param col_major: Use a column major weight matrix for input layer. Can speed up forward propagation, but might slow down backpropagation (Experimental)
  :param average_activation: Average activation for sparse auto-encoder (Experimental)
  :param sparsity_beta: Sparsity regularization (Experimental)
  :param max_categorical_features: Max. number of categorical features, enforced via hashing Experimental)
  :param reproducible: Force reproducibility on small data (will be slow - only uses 1 thread)
  :param export_weights_and_biases: Whether to export Neural Network weights and biases to H2O Frames"
  :param offset_column: Specify the offset column.
  :param weights_column: Specify the weights column.
  :param nfolds: (Optional) Number of folds for cross-validation. If nfolds >= 2, then validation must remain empty.
  :param fold_column: (Optional) Column with cross-validation fold index assignment per observation
  :param fold_assignment: Cross-validation fold assignment scheme, if fold_column is not specified Must be "AUTO", "Random" or "Modulo"
  :param keep_cross_validation_predictions: Whether to keep the predictions of the cross-validation models
  :return: Return a new classifier or regression model.
  """
  parms = {k:v for k,v in locals().items() if k in ["y","training_frame", "validation_frame", "validation_x", "validation_y", "offset_column", "weights_column", "fold_column"] or v is not None}
  parms["algo"]="deeplearning"
  return h2o_model_builder.supervised(parms)

def autoencoder(x,training_frame=None,model_id=None,overwrite_with_best_model=None,checkpoint=None,
                use_all_factor_levels=None,activation=None,hidden=None,epochs=None,train_samples_per_iteration=None,
                seed=None,adaptive_rate=None,rho=None,epsilon=None,rate=None,rate_annealing=None,rate_decay=None,
                momentum_start=None,momentum_ramp=None,momentum_stable=None,nesterov_accelerated_gradient=None,
                input_dropout_ratio=None,hidden_dropout_ratios=None,l1=None,l2=None,max_w2=None,initial_weight_distribution=None,
                initial_weight_scale=None,loss=None,distribution=None,tweedie_power=None,score_interval=None,score_training_samples=None,
                score_duty_cycle=None,classification_stop=None,regression_stop=None,quiet_mode=None,
                max_confusion_matrix_size=None,max_hit_ratio_k=None,balance_classes=None,class_sampling_factors=None,
                max_after_balance_size=None,diagnostics=None,variable_importances=None,
                fast_mode=None,ignore_const_cols=None,force_load_balance=None,replicate_training_data=None,single_node_mode=None,
                shuffle_training_data=None,sparse=None,col_major=None,average_activation=None,sparsity_beta=None,
                max_categorical_features=None,reproducible=None,export_weights_and_biases=None):
  """
  Build unsupervised auto encoder using H2O Deeplearning

  :param x: An H2OFrame containing the predictors in the model.
  :param training_frame: (Optional) An H2OFrame. Only used to retrieve weights, offset, or nfolds columns, if they aren't already provided in x.
  :param model_id: (Optional) The unique id assigned to the resulting model. If none is given, an id will automatically be generated.
  :param overwrite_with_best_model: Logical. If True, overwrite the final model with the best model found during training. Defaults to True.
  :param checkpoint: "Model checkpoint (either key or H2ODeepLearningModel) to resume training with."
  :param use_all_factor_levels: Logical. Use all factor levels of categorical variance. Otherwise the first factor level is omitted (without loss of accuracy). Useful for variable importances and auto-enabled for autoencoder.
  :param activation: A string indicating the activation function to use. Must be either "Tanh", "TanhWithDropout", "Rectifier", "RectifierWithDropout", "Maxout", or "MaxoutWithDropout"
  :param hidden: Hidden layer sizes (e.g. c(100,100))
  :param epochs: How many times the dataset should be iterated (streamed), can be fractional
  :param train_samples_per_iteration: Number of training samples (globally) per MapReduce iteration. Special values are: 0 one epoch; -1 all available data (e.g., replicated training data); or -2 auto-tuning (default)
  :param seed: Seed for random numbers (affects sampling) - Note: only reproducible when running single threaded
  :param adaptive_rate: Logical. Adaptive learning rate (ADAELTA)
  :param rho: Adaptive learning rate time decay factor (similarity to prior updates)
  :param epsilon: Adaptive learning rate parameter, similar to learn rate annealing during initial training phase. Typical values are between 1.0e-10 and 1.0e-4
  :param rate: Learning rate (higher => less stable, lower => slower convergence)
  :param rate_annealing: Learning rate annealing: \eqn{(rate)/(1 + rate_annealing*samples)
  :param rate_decay: Learning rate decay factor between layers (N-th layer: \eqn{rate*\alpha^(N-1))
  :param momentum_start: Initial momentum at the beginning of training (try 0.5)
  :param momentum_ramp: Number of training samples for which momentum increases
  :param momentum_stable: Final momentum after the amp is over (try 0.99)
  :param nesterov_accelerated_gradient: Logical. Use Nesterov accelerated gradient (recommended)
  :param input_dropout_ratio: A fraction of the features for each training row to be omitted from training in order to improve generalization (dimension sampling).
  :param hidden_dropout_ratios: Input layer dropout ratio (can improve generalization) specify one value per hidden layer, defaults to 0.5
  :param l1: L1 regularization (can add stability and improve generalization, causes many weights to become 0)
  :param l2: L2 regularization (can add stability and improve generalization, causes many weights to be small)
  :param max_w2: Constraint for squared sum of incoming weights per unit (e.g. Rectifier)
  :param initial_weight_distribution: Can be "Uniform", "UniformAdaptive", or "Normal"
  :param initial_weight_scale: Uniform: -value ... value, Normal: stddev
  :param loss: Loss function: "Automatic", "CrossEntropy" (for classification only), "MeanSquare", "Absolute" (experimental) or "Huber" (experimental)
  :param distribution: A character string. The distribution function of the response. Must be "AUTO", "bernoulli", "multinomial", "poisson", "gamma", "tweedie", "laplace", "huber" or "gaussian"
  :param tweedie_power: Tweedie power (only for Tweedie distribution, must be between 1 and 2)
  :param score_interval: Shortest time interval (in secs) between model scoring
  :param score_training_samples: Number of training set samples for scoring (0 for all)
  :param score_duty_cycle: Maximum duty cycle fraction for scoring (lower: more training, higher: more scoring)
  :param classification_stop: Stopping criterion for classification error fraction on training data (-1 to disable)
  :param regression_stop: Stopping criterion for regression error (MSE) on training data (-1 to disable)
  :param quiet_mode: Enable quiet mode for less output to standard output
  :param max_confusion_matrix_size: Max. size (number of classes) for confusion matrices to be shown
  :param max_hit_ratio_k: Max number (top K) of predictions to use for hit ratio computation(for multi-class only, 0 to disable)
  :param balance_classes: Balance training data class counts via over/under-sampling (for imbalanced data)
  :param class_sampling_factors: Desired over/under-sampling ratios per class (in lexicographic order). If not specified, sampling factors will be automatically computed to obtain class balance during training. Requires balance_classes.
  :param max_after_balance_size: Maximum relative size of the training data after balancing class counts (can be less than 1.0)
  :param diagnostics: Enable diagnostics for hidden layers
  :param variable_importances: Compute variable importances for input features (Gedeon method) - can be slow for large networks)
  :param fast_mode: Enable fast mode (minor approximations in back-propagation)
  :param ignore_const_cols: Ignore constant columns (no information can be gained anyway)
  :param force_load_balance: Force extra load balancing to increase training speed for small datasets (to keep all cores busy)
  :param replicate_training_data: Replicate the entire training dataset onto every node for faster training
  :param single_node_mode: Run on a single node for fine-tuning of model parameters
  :param shuffle_training_data: Enable shuffling of training data (recommended if training data is replicated and train_samples_per_iteration is close to \eqn{numRows*numNodes
  :param sparse: Sparse data handling (Experimental)
  :param col_major: Use a column major weight matrix for input layer. Can speed up forward propagation, but might slow down backpropagation (Experimental)
  :param average_activation: Average activation for sparse auto-encoder (Experimental)
  :param sparsity_beta: Sparsity regularization (Experimental)
  :param max_categorical_features: Max. number of categorical features, enforced via hashing Experimental)
  :param reproducible: Force reproducibility on small data (will be slow - only uses 1 thread)
  :param export_weights_and_biases: Whether to export Neural Network weights and biases to H2O Frames"
  :return: Return a new autoencoder
  """
  parms = {k:v for k,v in locals().items() if k in ["training_frame", "validation_frame", "validation_x", "validation_y", "offset_column", "weights_column", "fold_column"] or v is not None}
  parms["algo"]="deeplearning"
  parms["autoencoder"]=True
  return h2o_model_builder.unsupervised(parms)


def gbm(x,y,validation_x=None,validation_y=None,training_frame=None,model_id=None,
        distribution=None,tweedie_power=None,ntrees=None,max_depth=None,min_rows=None,
        learn_rate=None,nbins=None,nbins_cats=None,validation_frame=None,
        balance_classes=None,max_after_balance_size=None,seed=None,build_tree_one_node=None,
        nfolds=None,fold_column=None,fold_assignment=None,keep_cross_validation_predictions=None,
        score_each_iteration=None,offset_column=None,weights_column=None,do_future=None,checkpoint=None):
  """
  Builds gradient boosted classification trees, and gradient boosted regression trees on a parsed data set.
  The default distribution function will guess the model type based on the response column typerun properly the
  response column must be an numeric for "gaussian" or an enum for "bernoulli" or "multinomial".

  :param x: An H2OFrame containing the predictors in the model.
  :param y: An H2OFrame of the response variable in the model.
  :param training_frame: (Optional) An H2OFrame. Only used to retrieve weights, offset, or nfolds columns, if they aren't already provided in x.
  :param model_id: (Optional) The unique id assigned to the resulting model. If none is given, an id will automatically be generated.
  :param distribution: A character string. The distribution function of the response. Must be "AUTO", "bernoulli", "multinomial", "poisson", "gamma", "tweedie" or "gaussian"
  :param tweedie_power: Tweedie power (only for Tweedie distribution, must be between 1 and 2)
  :param ntrees: A non-negative integer that determines the number of trees to grow.
  :param max_depth: Maximum depth to grow the tree.
  :param min_rows: Minimum number of rows to assign to terminal nodes.
  :param learn_rate: An integer from 0.0 to 1.0
  :param nbins: For numerical columns (real/int), build a histogram of this many bins, then split at the best point
  :param nbins_cats: For categorical columns (enum), build a histogram of this many bins, then split at the best point. Higher values can lead to more overfitting.
  :param validation_frame: An H2OFrame object indicating the validation dataset used to contruct the confusion matrix. If left blank, this defaults to the training data when nfolds = 0
  :param balance_classes: logical, indicates whether or not to balance training data class counts via over/under-sampling (for imbalanced data)
  :param max_after_balance_size: Maximum relative size of the training data after balancing class counts (can be less than 1.0)
  :param seed: Seed for random numbers (affects sampling when balance_classes=T)
  :param build_tree_one_node: Run on one node only; no network overhead but fewer cpus used.  Suitable for small datasets.
  :param nfolds: (Optional) Number of folds for cross-validation. If nfolds >= 2, then validation must remain empty.
  :param fold_column: (Optional) Column with cross-validation fold index assignment per observation
  :param fold_assignment: Cross-validation fold assignment scheme, if fold_column is not specified Must be "AUTO", "Random" or "Modulo"
  :param keep_cross_validation_predictions: Whether to keep the predictions of the cross-validation models
  :param score_each_iteration: Attempts to score each tree.
  :param offset_column: Specify the offset column.
  :param weights_column: Specify the weights column.
  :return: A new classifier or regression model.
  """
  parms = {k:v for k,v in locals().items() if k in ["training_frame", "validation_frame", "validation_x", "validation_y", "offset_column", "weights_column", "fold_column"] or v is not None}
  parms["algo"]="gbm"
  return h2o_model_builder.supervised(parms)


def glm(x,y,validation_x=None,validation_y=None,training_frame=None,model_id=None,validation_frame=None,
        max_iterations=None,beta_epsilon=None,solver=None,standardize=None,family=None,link=None,
        tweedie_variance_power=None,tweedie_link_power=None,alpha=None,prior=None,lambda_search=None,
        nlambdas=None,lambda_min_ratio=None,beta_constraints=None,offset_column=None,weights_column=None,
        nfolds=None,fold_column=None,fold_assignment=None,keep_cross_validation_predictions=None,
        intercept=None, Lambda=None, max_active_predictors=None, do_future=None, checkpoint=None):
  """
  Build a Generalized Linear Model
  Fit a generalized linear model, specified by a response variable, a set of predictors, and a description of the error
  distribution.

  :param x: An H2OFrame containing the predictors in the model.
  :param y: An H2OFrame of the response variable in the model.
  :param training_frame: (Optional) An H2OFrame. Only used to retrieve weights, offset, or nfolds columns, if they aren't already provided in x.
  :param model_id: (Optional) The unique id assigned to the resulting model. If none is given, an id will automatically be generated.
  :param validation_frame: An H2OFrame object containing the variables in the model.
  :param max_iterations: A non-negative integer specifying the maximum number of iterations.
  :param beta_epsilon: A non-negative number specifying the magnitude of the maximum difference between the coefficient estimates from successive iterations. Defines the convergence criterion for h2o.glm.
  :param solver: A character string specifying the solver used: IRLSM (supports more features), L_BFGS (scales better for datasets with many columns)
  :param standardize: A logical value indicating whether the numeric predictors should be standardized to have a mean of 0 and a variance of 1 prior to training the models.
  :param family: A character string specifying the distribution of the model:  gaussian, binomial, poisson, gamma, tweedie.
  :param link: A character string specifying the link function. The default is the canonical link for the family.
  The supported links for each of the family specifications are:
          "gaussian": "identity", "log", "inverse"
          "binomial": "logit", "log"
          "poisson": "log", "identity"
          "gamma": "inverse", "log", "identity"
          "tweedie": "tweedie"
  :param tweedie_variance_power: A numeric specifying the power for the variance function when family = "tweedie".
  :param tweedie_link_power: A numeric specifying the power for the link function when family = "tweedie".
  :param alpha: A numeric in [0, 1] specifying the elastic-net mixing parameter.
  The elastic-net penalty is defined to be:
  eqn{P(\alpha,\beta) = (1-\alpha)/2||\beta||_2^2 + \alpha||\beta||_1 = \sum_j [(1-\alpha)/2 \beta_j^2 + \alpha|\beta_j|],
  making alpha = 1 the lasso penalty and alpha = 0 the ridge penalty.
  :param Lambda: A non-negative shrinkage parameter for the elastic-net, which multiplies \eqn{P(\alpha,\beta) in the objective function. When Lambda = 0, no elastic-net penalty is applied and ordinary generalized linear models are fit.
  :param prior: (Optional) A numeric specifying the prior probability of class 1 in the response when family = "binomial". The default prior is the observational frequency of class 1.
  :param lambda_search: A logical value indicating whether to conduct a search over the space of lambda values starting from the lambda max, given lambda is interpreted as lambda min.
  :param nlambdas: The number of lambda values to use when lambda_search = TRUE.
  :param lambda_min_ratio: Smallest value for lambda as a fraction of lambda.max. By default if the number of
  observations is greater than the the number of variables then lambda_min_ratio = 0.0001; if the number of
  observations is less than the number of variables then lambda_min_ratio = 0.01.
  :param beta_constraints: A data.frame or H2OParsedData object with the columns ["names", "lower_bounds",
  "upper_bounds", "beta_given"], where each row corresponds to a predictor in the GLM. "names" contains the predictor
  names, "lower"/"upper_bounds", are the lower and upper bounds of beta, and "beta_given" is some supplied starting
  values for the
  :param offset_column: Specify the offset column.
  :param weights_column: Specify the weights column.
  :param nfolds: (Optional) Number of folds for cross-validation. If nfolds >= 2, then validation must remain empty.
  :param fold_column: (Optional) Column with cross-validation fold index assignment per observation
  :param fold_assignment: Cross-validation fold assignment scheme, if fold_column is not specified Must be "AUTO", "Random" or "Modulo"
  :param keep_cross_validation_predictions: Whether to keep the predictions of the cross-validation models
  :param intercept: Logical, include constant term (intercept) in the model
  :param max_active_predictors: (Optional) Convergence criteria for number of predictors when using L1 penalty.
  :return: A subclass of ModelBase is returned. The specific subclass depends on the machine learning task at hand (if
  it's binomial classification, then an H2OBinomialModel is returned, if it's regression then a H2ORegressionModel is
  returned). The default print-out of the models is shown, but further GLM-specifc information can be queried out of
  the object.
  Upon completion of the GLM, the resulting object has coefficients, normalized coefficients, residual/null deviance,
  aic, and a host of model metrics including MSE, AUC (for logistic regression), degrees of freedom, and confusion
  matrices.
  """
  parms = {k.lower():v for k,v in locals().items() if k in ["training_frame", "validation_frame", "validation_x", "validation_y", "offset_column", "weights_column", "fold_column"] or v is not None}
  if "alpha" in parms and not isinstance(parms["alpha"], (list,tuple)): parms["alpha"] = [parms["alpha"]]
  parms["algo"]="glm"
  return h2o_model_builder.supervised(parms)

def start_glm_job(x,y,validation_x=None,validation_y=None,**kwargs):
  """
  Build a Generalized Linear Model
  Note: this function is the same as glm(), but it doesn't block on model-build. Instead, it returns and H2OModelFuture
  object immediately. The model can be retrieved from the H2OModelFuture object with get_future_model().

  :return: H2OModelFuture
  """
  kwargs["do_future"] = True
  return glm(x,y,validation_x,validation_y,**kwargs)

def kmeans(x,validation_x=None,k=None,model_id=None,max_iterations=None,standardize=None,init=None,seed=None,
           nfolds=None,fold_column=None,fold_assignment=None,training_frame=None,validation_frame=None,
           user_points=None,ignored_columns=None,score_each_iteration=None,keep_cross_validation_predictions=None,
           ignore_const_cols=None,checkpoint=None):
  """
  Performs k-means clustering on an H2O dataset.

  :param x: (Optional) A vector containing the data columns on which k-means operates.
  :param k: The number of clusters. Must be between 1 and 1e7 inclusive. k may be omitted if the user specifies the
  initial centers in the init parameter. If k is not omitted, in this case, then it should be equal to the number of
  user-specified centers.
  :param model_id: (Optional) The unique id assigned to the resulting model. If none is given, an id will automatically be generated.
  :param max_iterations: The maximum number of iterations allowed. Must be between 0 and 1e6 inclusive.
  :param standardize: Logical, indicates whether the data should be standardized before running k-means.
  :param init: A character string that selects the initial set of k cluster centers. Possible values are "Random": for
  random initialization, "PlusPlus": for k-means plus initialization, or "Furthest": for initialization at the furthest
  point from each successive center. Additionally, the user may specify a the initial centers as a matrix, data.frame,
  H2OFrame, or list of vectors. For matrices, data.frames, and H2OFrames, each row of the respective structure is an
  initial center. For lists of vectors, each vector is an initial center.
  :param seed: (Optional) Random seed used to initialize the cluster centroids.
  :param nfolds: (Optional) Number of folds for cross-validation. If nfolds >= 2, then validation must remain empty.
  :param fold_column: (Optional) Column with cross-validation fold index assignment per observation
  :param fold_assignment: Cross-validation fold assignment scheme, if fold_column is not specified Must be "AUTO", "Random" or "Modulo"
  :return: Returns an object of class H2OClusteringModel.
  """
  parms = {k:v for k,v in locals().items() if k in ["training_frame", "validation_frame", "validation_x", "validation_y", "offset_column", "weights_column", "fold_column"] or v is not None}
  parms["algo"]="kmeans"
  return h2o_model_builder.unsupervised(parms)


def random_forest(x,y,validation_x=None,validation_y=None,training_frame=None,model_id=None,mtries=None,sample_rate=None,
                  build_tree_one_node=None,ntrees=None,max_depth=None,min_rows=None,nbins=None,nbins_cats=None,
                  binomial_double_trees=None,validation_frame=None,balance_classes=None,max_after_balance_size=None,
                  seed=None,offset_column=None,weights_column=None,nfolds=None,fold_column=None,fold_assignment=None,
                  keep_cross_validation_predictions=None,checkpoint=None):
  """
  Build a Big Data Random Forest Model
  Builds a Random Forest Model on an H2OFrame

  :param x: An H2OFrame containing the predictors in the model.
  :param y: An H2OFrame of the response variable in the model.
  :param training_frame: (Optional) An H2OFrame. Only used to retrieve weights, offset, or nfolds columns, if they aren't already provided in x.
  :param model_id: (Optional) The unique id assigned to the resulting model. If none is given, an id will automatically be generated.
  :param mtries: Number of variables randomly sampled as candidates at each split. If set to -1, defaults to sqrt{p} for classification, and p/3 for regression, where p is the number of predictors.
  :param sample_rate: Sample rate, from 0 to 1.0.
  :param build_tree_one_node: Run on one node only; no network overhead but fewer cpus used.  Suitable for small datasets.
  :param ntrees: A nonnegative integer that determines the number of trees to grow.
  :param max_depth: Maximum depth to grow the tree.
  :param min_rows: Minimum number of rows to assign to teminal nodes.
  :param nbins: For numerical columns (real/int), build a histogram of this many bins, then split at the best point.
  :param nbins_cats: For categorical columns (enum), build a histogram of this many bins, then split at the best point. Higher values can lead to more overfitting.
  :param binomial_double_trees: For binary classification: Build 2x as many trees (one per class) - can lead to higher accuracy.
  :param validation_frame: An H2OFrame object containing the variables in the model.
  :param balance_classes: logical, indicates whether or not to balance training data class counts via over/under-sampling (for imbalanced data)
  :param max_after_balance_size: Maximum relative size of the training data after balancing class counts (can be less than 1.0)
  :param seed: Seed for random numbers (affects sampling) - Note: only reproducible when running single threaded
  :param offset_column: Specify the offset column.
  :param weights_column: Specify the weights column.
  :param nfolds: (Optional) Number of folds for cross-validation. If nfolds >= 2, then validation must remain empty.
  :param fold_column: (Optional) Column with cross-validation fold index assignment per observation
  :param fold_assignment: Cross-validation fold assignment scheme, if fold_column is not specified Must be "AUTO", "Random" or "Modulo"
  :param keep_cross_validation_predictions: Whether to keep the predictions of the cross-validation models
  :return: A new classifier or regression model.
  """
  parms = {k:v for k,v in locals().items() if k in ["training_frame", "validation_frame", "validation_x", "validation_y", "offset_column", "weights_column", "fold_column"] or v is not None}
  parms["algo"]="drf"
  return h2o_model_builder.supervised(parms)


def prcomp(x,validation_x=None,k=None,model_id=None,max_iterations=None,transform=None,seed=None,use_all_factor_levels=None,
           training_frame=None,validation_frame=None,pca_method=None):
  """
  Principal components analysis of a H2O dataset using the power method
  to calculate the singular value decomposition of the Gram matrix.

  :param k: The number of principal components to be computed. This must be between 1 and min(ncol(training_frame), nrow(training_frame)) inclusive.
  :param model_id: (Optional) The unique hex key assigned to the resulting model. Automatically generated if none is provided.
  :param max_iterations: The maximum number of iterations to run each power iteration loop. Must be between 1 and 1e6 inclusive.
  :param transform: A character string that indicates how the training data should be transformed before running PCA.
  Possible values are "NONE": for no transformation, "DEMEAN": for subtracting the mean of each column, "DESCALE":
  for dividing by the standard deviation of each column, "STANDARDIZE": for demeaning and descaling, and "NORMALIZE":
  for demeaning and dividing each column by its range (max - min).
  :param seed: (Optional) Random seed used to initialize the right singular vectors at the beginning of each power method iteration.
  :param use_all_factor_levels: (Optional) A logical value indicating whether all factor levels should be included
  in each categorical column expansion. If FALSE, the indicator column corresponding to the first factor level of
  every categorical variable will be dropped. Defaults to FALSE.
  :return: a new dim reduction model
  """
  parms = {k:v for k,v in locals().items() if k in ["training_frame", "validation_frame", "validation_x", "validation_y", "offset_column", "weights_column", "fold_column"] or v is not None}
  parms["algo"]="pca"
  return h2o_model_builder.unsupervised(parms)

def svd(x,validation_x=None,nv=None,max_iterations=None,transform=None,seed=None,use_all_factor_levels=None,
        training_frame=None, validation_frame=None):
  """
  Singular value decomposition of a H2O dataset using the power method.

  :param nv: The number of right singular vectors to be computed. This must be between 1 and min(ncol(training_frame), snrow(training_frame)) inclusive.
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
  parms = {k:v for k,v in locals().items() if k in ["training_frame", "validation_frame", "validation_x", "validation_y", "offset_column", "weights_column", "fold_column"] or v is not None}
  parms["algo"]="svd"
  parms['_rest_version']=99
  return h2o_model_builder.unsupervised(parms)

def naive_bayes(x,y,validation_x=None,validation_y=None,training_frame=None,validation_frame=None,
                laplace=None,threshold=None,eps=None,compute_metrics=None,offset_column=None,weights_column=None,
                balance_classes=None,max_after_balance_size=None, nfolds=None,fold_column=None,fold_assignment=None,
                keep_cross_validation_predictions=None,checkpoint=None):
  """
  The naive Bayes classifier assumes independence between predictor variables conditional on the response, and a
  Gaussian distribution of numeric predictors with mean and standard deviation computed from the training dataset.
  When building a naive Bayes classifier, every row in the training dataset that contains at least one NA will be
  skipped completely. If the test dataset has missing values, then those predictors are omitted in the probability
  calculation during prediction.

  :param laplace: A positive number controlling Laplace smoothing. The default zero disables smoothing.
  :param threshold: The minimum standard deviation to use for observations without enough data. Must be at least 1e-10.
  :param eps: A threshold cutoff to deal with numeric instability, must be positive.
  :param compute_metrics: A logical value indicating whether model metrics should be computed. Set to FALSE to reduce the runtime of the algorithm.
  :param training_frame: Training Frame
  :param validation_frame: Validation Frame
  :param offset_column: Specify the offset column.
  :param weights_column: Specify the weights column.
  :param nfolds: (Optional) Number of folds for cross-validation. If nfolds >= 2, then validation must remain empty.
  :param fold_column: (Optional) Column with cross-validation fold index assignment per observation
  :param fold_assignment: Cross-validation fold assignment scheme, if fold_column is not specified Must be "AUTO", "Random" or "Modulo"
  :param keep_cross_validation_predictions: Whether to keep the predictions of the cross-validation models
  :return: Returns an H2OBinomialModel if the response has two categorical levels, H2OMultinomialModel otherwise.
  """
  parms = {k:v for k,v in locals().items() if k in ["training_frame", "validation_frame", "validation_x", "validation_y", "offset_column", "weights_column", "fold_column"] or v is not None}
  parms["algo"]="naivebayes"
  return h2o_model_builder.supervised(parms)


def create_frame(id = None, rows = 10000, cols = 10, randomize = True, value = 0, real_range = 100,
                 categorical_fraction = 0.2, factors = 100, integer_fraction = 0.2, integer_range = 100,
                 binary_fraction = 0.1, binary_ones_fraction = 0.02, missing_fraction = 0.01, response_factors = 2,
                 has_response = False, seed=None):
  """
  Data Frame Creation in H2O.
  Creates a data frame in H2O with real-valued, categorical, integer, and binary columns specified by the user.

  :param id: A string indicating the destination key. If empty, this will be auto-generated by H2O.
  :param rows: The number of rows of data to generate.
  :param cols: The number of columns of data to generate. Excludes the response column if has_response == True.
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
  factors = [data.names[n] if isinstance(n,int) else n for n in factors]
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
  rapids(ExprNode._collapse_sb(ExprNode("setTimeZone", tz)._eager()))

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


#  ALL DEPRECATED METHODS BELOW #

def h2o_deprecated(newfun=None):
  def o(fun):
    if newfun is not None: m = "{} is deprecated. Use {}.".format(fun.__name__,newfun.__name__)
    else:                  m = "{} is deprecated.".format(fun.__name__)
    @functools.wraps(fun)
    def i(*args, **kwargs):
      print
      print
      warnings.warn(m, category=DeprecationWarning, stacklevel=2)
      return fun(*args, **kwargs)
    return i
  return o

@h2o_deprecated(import_file)
def import_frame(path=None):
  """
  Deprecated for import_file.

  :param path: A path specifiying the location of the data to import.
  :return: A new H2OFrame
  """
  warnings.warn("deprecated: Use import_file", DeprecationWarning)
  return import_file(path)
