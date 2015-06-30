"""
This file builds H2O model
"""

from connection import H2OConnection
from frame      import H2OFrame, H2OVec
from job        import H2OJob
import h2o

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
    if not isinstance(x,list):
      raise ValueError("`x` must be an H2OFrame or a list of H2OVecs. Got: " + str(type(x)))
    x = H2OFrame(vecs=x)
  if y is not None:
    if not isinstance(y,H2OVec):
      raise ValueError("`y` must be an H2OVec. Got: " + str(type(y)))
    for v in x._vecs:
      if y._name == v._name:
        raise ValueError("Found response "+y._name+" in training `x` data")
    x[response._name] = y
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
  if validation_x:
    validation_x = _check_frame(validation_x,validation_y,y)

  # Send frame descriptions to H2O cluster
  train_key = x.send_frame()
  kwargs['training_frame']=train_key
  if validation_x is not None:
    valid_key = validation_x.send_frame()
    kwargs['validation_frame']=valid_key

  if y:
    kwargs['response_column']=y._name

  kwargs = dict([(k, kwargs[k]) for k in kwargs if kwargs[k] is not None])

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

  else:
    print model_type
    raise NotImplementedError
  return model
