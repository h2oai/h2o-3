"""
This file builds H2O model
"""

from connection import H2OConnection
from frame      import H2OFrame
from job        import H2OJob
from model.model_future import H2OModelFuture
from model.dim_reduction import H2ODimReductionModel
from model.autoencoder import H2OAutoEncoderModel
from model.multinomial import H2OMultinomialModel
from model.regression import H2ORegressionModel
from model.binomial import H2OBinomialModel
from model.clustering import H2OClusteringModel



# Response variable model building
def supervised_model_build(x,y,validation_x,validation_y,algo_url,kwargs):
  # Sanity check data frames
  if not y:
    if algo_url=="deeplearning":
      if "autoencoder" in kwargs and kwargs["autoencoder"]:
        pass  # all good
    else:
      raise ValueError("Missing response training a supervised model")
  elif y:
    if algo_url=="deeplearning":
      if "autoencoder" in kwargs and kwargs["autoencoder"]:
        raise ValueError("`y` should not be specified for autoencoder, remove `y` input.")
  if validation_x:
    if validation_y is None:  raise ValueError("Missing response validating a supervised model")
  return _model_build(x,y,validation_x,validation_y,algo_url,kwargs)

# No response variable model building
def unsupervised_model_build(x,validation_x,algo_url,kwargs):
  return _model_build(x,None,validation_x,None,algo_url,kwargs)


# Sanity check features and response variable.
def _check_frame(x,y,response):
  if not isinstance(x,H2OFrame):
    if not isinstance(x,list): raise ValueError("`x` must be an H2OFrame or a list. Got: " + str(type(x)))
  if y is not None and not isinstance(y,H2OFrame): raise ValueError("`y` must be an H2OFrame. Got: " + str(type(y)))
  if y is not None: x[response._col_names[0]] = y
  return x

def _add_col_to_x_and_validation_x(col_name,x,validation_x,kwargs,xval=False):
  """
  Add column to x/validation_x, if it isn't already there. Grabs the column from the training_frame or
  validation_frame parameters.

  :param col_name: the name of the column to add
  :param xval: if True, don't add folds_column to validation_x
  :return: x and validation_x, with the respective columns added
  """
  # training_frame
  if col_name not in x._col_names and not col_name is None:
    if "training_frame" not in kwargs.keys(): raise ValueError("must specify `training_frame` argument if `" +
                                                               col_name + "`not part of `x`")
    if not col_name in kwargs["training_frame"].col_names():
      raise ValueError("`" + col_name + "` wasn't found in the training_frame. Only these columns were found: "
                                        "{0}".format(kwargs["training_frame"].col_names()))
    x[col_name] = kwargs["training_frame"][col_name]

  # validation_frame
  if validation_x is not None and not xval:
    if col_name not in validation_x._col_names and not col_name is None:
      if "validation_frame" not in kwargs.keys(): raise ValueError("must specify `validation_frame` argument if "
                                                                   "`" + col_name + "` not part of `validation_x`")
      if not col_name in kwargs["validation_frame"].col_names():
        raise ValueError("`" + col_name + "` wasn't found in the validation_frame. Only these columns were found: "
                                          "{0}".format(kwargs["validation_frame"].col_names()))
      validation_x[col_name] = kwargs["validation_frame"][col_name]

  return x, validation_x

# Build an H2O model
def _model_build(x,y,validation_x,validation_y,algo_url,kwargs):
  # Basic sanity checking
  if algo_url == "autoencoder":
    if "autoencoder" in kwargs.keys():
      if kwargs["autoencoder"]:
        if y:
          raise ValueError("`y` should not be specified for autoencoder, remove `y` input.")
        algo_url="deeplearning"
  if not x:  raise ValueError("Missing features")
  x = _check_frame(x,y,y)
  if validation_x is not None: validation_x = _check_frame(validation_x,validation_y,y)

  if "weights_column" in kwargs.keys(): x, validation_x = _add_col_to_x_and_validation_x(kwargs["weights_column"],x, validation_x, kwargs)
  if "offset_column"  in kwargs.keys(): x, validation_x = _add_col_to_x_and_validation_x(kwargs["offset_column"], x, validation_x, kwargs)
  if "fold_column"   in kwargs.keys(): x, validation_x = _add_col_to_x_and_validation_x(kwargs["fold_column"],    x, validation_x, kwargs, xval=True)

  # Send frame descriptions to H2O cluster
  kwargs['training_frame']=x._id
  if validation_x is not None: kwargs['validation_frame']=validation_x._id

  if y is not None: kwargs['response_column']=y._col_names[0]

  kwargs = dict([(k, kwargs[k]._frame()._id if isinstance(kwargs[k], H2OFrame) else kwargs[k]) for k in kwargs if
                 kwargs[k] is not None])

  # launch the job (only resolve the model if do_future is False)
  do_future = "do_future" in kwargs.keys() and kwargs["do_future"]
  if "do_future" in kwargs.keys(): kwargs.pop("do_future")
  future_model = H2OModelFuture(H2OJob(H2OConnection.post_json("ModelBuilders/"+algo_url, **kwargs),
                                       job_type=(algo_url+" Model Build")), x)
  if do_future: return future_model
  else: return _resolve_model(future_model, **kwargs)

def _resolve_model(future_model, **kwargs):
  future_model.poll() # Wait for model-building to be complete
  if '_rest_version' in kwargs.keys():
    model_json = H2OConnection.get_json("Models/"+future_model.job.dest_key,
                                        _rest_version=kwargs['_rest_version'])["models"][0]
  else:
    model_json = H2OConnection.get_json("Models/"+future_model.job.dest_key)["models"][0]

  model_type = model_json["output"]["model_category"]
  if   model_type=="Binomial":     model = H2OBinomialModel(    future_model.job.dest_key,model_json)
  elif model_type=="Clustering":   model = H2OClusteringModel(  future_model.job.dest_key,model_json)
  elif model_type=="Regression":   model = H2ORegressionModel(  future_model.job.dest_key,model_json)
  elif model_type=="Multinomial":  model = H2OMultinomialModel( future_model.job.dest_key,model_json)
  elif model_type=="AutoEncoder":  model = H2OAutoEncoderModel( future_model.job.dest_key,model_json)
  elif model_type=="DimReduction": model = H2ODimReductionModel(future_model.job.dest_key,model_json)

  else:
    print model_type
    raise NotImplementedError
  return model
