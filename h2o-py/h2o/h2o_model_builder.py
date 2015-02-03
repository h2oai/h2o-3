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
  if not y:  raise ValueError("Missing response training a supervised model")
  if validation_x:
    if not validation_y:  raise ValueError("Missing response validating a supervised model")
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
  if y:
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
  if not x:  raise ValueError("Missing features")
  x = _check_frame(x,y,y)
  if validation_x:
    validation_x = _check_frame(validation_x,validation_y,y)
      
  # Send frame descriptions to H2O cluster  
  train_key = x.send_frame()
  kwargs['training_frame']=train_key
  if validation_x:
    valid_key = validation_x.send_frame()
    kwargs['validation_frame']=valid_key

  if y:
    kwargs['response_column']=y._name

  # launch the job and poll
  job = H2OJob(H2OConnection.post_json("ModelBuilders/"+algo_url, **kwargs), job_type=(algo_url+" Model Build")).poll()
  model_json = H2OConnection.get_json("Models/"+job.dest_key)["models"][0]
  model_type = model_json["output"]["model_category"]
  if model_type=="Binomial":
    from model.binomial import H2OBinomialModel
    model = H2OBinomialModel(job.dest_key,model_json)

  elif model_type=="Clustering":
    from model.clustering import H2OClusteringModel
    model = H2OClusteringModel(job.dest_key,model_json)

  else:
    print model_type
    raise NotImplementedError

  # Cleanup
  h2o.remove(train_key)
  if validation_x:
    h2o.remove(valid_key)

  return model

