"""
This file builds H2O model
"""

from connection import H2OConnection
from frame      import H2OFrame
from job        import H2OJob


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

# Add the column to the training and/or validation data
def _add_col(x, source, column):
  if not isinstance(column, str): raise ValueError("column must be a str but got {1}".format(type(column)))
  if not isinstance(column, str): raise ValueError("column must be a name in {0}, but got "
                                                   "{1}".format(source.col_names(), column))
  x[column] = source[column]
  return x

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

  # add weights_column to x/validation_x, if it isn't already there. have to grab it from the training/validation frame.
  if "weights_column" in kwargs.keys():
    # training_frame
    if kwargs["weights_column"] not in x._col_names:
      if "training_frame" not in kwargs.keys(): raise ValueError("must specify `training_frame` argument if `weights`"
                                                                 "not part of `x`")
      x = _add_col(x, kwargs["training_frame"], kwargs["weights_column"])
      assert kwargs["weights_column"] in x._col_names

    # validation_frame
    if validation_x is not None:
      if kwargs["weights_column"] not in validation_x._col_names:
        if "validation_frame" not in kwargs.keys(): raise ValueError("must specify `validation_frame` argument if "
                                                                     "`weights` not part of `validation_x`")
        x = _add_col(validation_x, kwargs["validation_frame"], kwargs["weights_column"])
        assert kwargs["weights_column"] in validation_x._col_names

  # add offset_column to x/validation_x, if it isn't already there. have to grab it from the training/validation frame.
  if "offset_column" in kwargs.keys():
    # training_frame
    if kwargs["offset_column"] not in x._col_names:
      if "training_frame" not in kwargs.keys(): raise ValueError("must specify `training_frame` argument if "
                                                                 "`offset_column` not part of `x`")
      x = _add_col(x, kwargs["training_frame"], kwargs["offset_column"])
      assert kwargs["offset_column"] in x._col_names

    # validation_frame
    if validation_x is not None:
      if kwargs["offset_column"] not in validation_x._col_names:
        if "validation_frame" not in kwargs.keys(): raise ValueError("must specify `validation_frame` argument if "
                                                                     "`offset_column` not part of `validation_x`")
        x = _add_col(validation_x, kwargs["validation_frame"], kwargs["offset_column"])
        assert kwargs["offset_column"] in validation_x._col_names

  # Send frame descriptions to H2O cluster
  kwargs['training_frame']=x._id
  if validation_x is not None: kwargs['validation_frame']=validation_x._id

  if y is not None: kwargs['response_column']=y._col_names[0]

  kwargs = dict([(k, kwargs[k]._frame()._id if isinstance(kwargs[k], H2OFrame) else kwargs[k]) for k in kwargs if kwargs[k] is not None])

  # launch the job and poll
  job = H2OJob(H2OConnection.post_json("ModelBuilders/"+algo_url, **kwargs), job_type=(algo_url+" Model Build")).poll()
  if '_rest_version' in kwargs.keys():
    model_json = H2OConnection.get_json("Models/"+job.dest_key, _rest_version=kwargs['_rest_version'])["models"][0]
  else:
    model_json = H2OConnection.get_json("Models/"+job.dest_key)["models"][0]

  model_type = model_json["output"]["model_category"]
  if model_type=="Binomial":
    from model.binomial import H2OBinomialModel
    model = H2OBinomialModel(job.dest_key,model_json)

  elif model_type=="Clustering":
    from model.clustering import H2OClusteringModel
    model = H2OClusteringModel(job.dest_key,model_json)

  elif model_type=="Regression":
    from model.regression import H2ORegressionModel
    model = H2ORegressionModel(job.dest_key,model_json)

  elif model_type=="Multinomial":
    from model.multinomial import H2OMultinomialModel
    model = H2OMultinomialModel(job.dest_key,model_json)

  elif model_type=="AutoEncoder":
    from model.autoencoder import H2OAutoEncoderModel
    model = H2OAutoEncoderModel(job.dest_key,model_json)

  elif model_type=="DimReduction":
    from model.dim_reduction import H2ODimReductionModel
    model = H2ODimReductionModel(job.dest_key,model_json)

  else:
    print model_type
    raise NotImplementedError
  return model
