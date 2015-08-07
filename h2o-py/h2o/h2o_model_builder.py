"""
This file builds H2O model
"""

from connection import H2OConnection
from frame      import H2OFrame
from job        import H2OJob
from model.model_future import H2OModelFuture


# Response variable model building
def supervised_model_build(x,y,validation_x,validation_y,algo_url,kwargs):
  # Sanity check data frames
  if y is None:
    if algo_url=="deeplearning":
      if "autoencoder" in kwargs and kwargs["autoencoder"]:
        pass  # all good
    else:
      raise ValueError("Missing response training a supervised model")
  elif y is not None:
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
  x                                ._eager()
  if y is not None:        y       ._eager()
  if response is not None: response._eager()
  if not isinstance(x,H2OFrame):
    if not isinstance(x,list): raise ValueError("`x` must be an H2OFrame or a list. Got: " + str(type(x)))
  if y is not None and not isinstance(y,H2OFrame): raise ValueError("`y` must be an H2OFrame. Got: " + str(type(y)))
  if y is not None: x[response._col_names[0]] = y
  return x

def _add_extra_col(x, column): return _check_frame(x,column,column)

def _check_extra_col(x,validation_x,colname,kwargs):
  x=_add_extra_col(x, kwargs[colname])
  if validation_x is not None: validation_x = _add_extra_col(validation_x,kwargs[colname])
  kwargs[colname] = kwargs[colname]._col_names[0]
  return x, validation_x

# Build an H2O model
def _model_build(x,y,validation_x,validation_y,algo_url,kwargs):
  # Basic sanity checking
  if algo_url == "autoencoder":
    if "autoencoder" in kwargs.keys():
      if kwargs["autoencoder"]:
        if y is not None:
          raise ValueError("`y` should not be specified for autoencoder, remove `y` input.")
        algo_url="deeplearning"
  if x is None:  raise ValueError("Missing features")

  x = _check_frame(x,y,y)
  if validation_x is not None: validation_x = _check_frame(validation_x,validation_y,y)

  if "weights_column" in kwargs.keys(): x,validation_x = _check_extra_col(x,validation_x,"weights_column",kwargs)
  if "offset_column" in kwargs.keys():  x,validation_x = _check_extra_col(x,validation_x,"offset_column", kwargs)
  if "fold_column"   in kwargs.keys():  x,validation_x = _check_extra_col(x,validation_x,"fold_column",   kwargs)

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
  if model_type=="Binomial":
    from model.binomial import H2OBinomialModel
    model = H2OBinomialModel(future_model.job.dest_key,model_json)

  elif model_type=="Clustering":
    from model.clustering import H2OClusteringModel
    model = H2OClusteringModel(future_model.job.dest_key,model_json)

  elif model_type=="Regression":
    from model.regression import H2ORegressionModel
    model = H2ORegressionModel(future_model.job.dest_key,model_json)

  elif model_type=="Multinomial":
    from model.multinomial import H2OMultinomialModel
    model = H2OMultinomialModel(future_model.job.dest_key,model_json)

  elif model_type=="AutoEncoder":
    from model.autoencoder import H2OAutoEncoderModel
    model = H2OAutoEncoderModel(future_model.job.dest_key,model_json)

  elif model_type=="DimReduction":
    from model.dim_reduction import H2ODimReductionModel
    model = H2ODimReductionModel(future_model.job.dest_key,model_json)

  else:
    print model_type
    raise NotImplementedError
  return model
