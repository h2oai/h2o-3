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


def supervised_model_build(x=None,y=None,vx=None,vy=None,algo="",offsets=None,weights=None,fold_column=None,kwargs=None):
  is_auto_encoder = kwargs is not None and "autoencoder" in kwargs and kwargs["autoencoder"] is not None
  if is_auto_encoder and y is not None: raise ValueError("y should not be specified for autoencoder.")
  if not is_auto_encoder and y is None: raise ValueError("Missing response")
  if vx is not None and vy is None:     raise ValueError("Missing response validating a supervised model")
  return _model_build(x,y,vx,vy,algo,offsets,weights,fold_column,kwargs)

def supervised(kwargs):
  x =_frame_helper(kwargs["x"],kwargs["training_frame"])
  y =_frame_helper(kwargs["y"],kwargs["training_frame"])
  vx=_frame_helper(kwargs["validation_x"],kwargs["validation_frame"])
  vy=_frame_helper(kwargs["validation_y"],kwargs["validation_frame"])
  offsets    = _ow("offset_column", kwargs)
  weights    = _ow("weights_column",kwargs)
  fold_column= _ow("fold_column",   kwargs)
  algo  = kwargs["algo"]
  parms={k:v for k,v in kwargs.items() if (k not in ["x","y","validation_x","validation_y","algo"] and v is not None) or k=="validation_frame"}
  return supervised_model_build(x,y,vx,vy,algo,offsets,weights,fold_column,parms)

def unsupervised_model_build(x,validation_x,algo_url,kwargs): return _model_build(x,None,validation_x,None,algo_url,None,None,None,kwargs)
def unsupervised(kwargs):
  x = _frame_helper(kwargs["x"],kwargs["training_frame"])  # y is just None
  vx=_frame_helper(kwargs["validation_x"],kwargs["validation_frame"])
  algo=kwargs["algo"]
  parms={k:v for k,v in kwargs.items() if k not in ["x","validation_x","algo"] and v is not None}
  return unsupervised_model_build(x,vx,algo,parms)

def _frame_helper(col,fr):
  if col is None: return None
  if not isinstance(col,H2OFrame):
    if fr is None: raise ValueError("Missing training_frame")
  return fr[col] if not isinstance(col,H2OFrame) else col

def _ow(name,kwargs):  # for checking offsets and weights, c is column, fr is frame
  c=kwargs[name]
  fr=kwargs["training_frame"]
  if c is None or isinstance(c,H2OFrame): res=c
  else:
    if fr is None: raise ValueError("offsets/weights given, but missing training_frame")
    res=fr[c]
  kwargs[name] = None if res is None else res.col_names()[0]
  if res is not None and kwargs["validation_x"] is not None and kwargs["validation_frame"] is None:  # validation frame must have any offsets, weights, folds, etc.
    raise ValueError("offsets/weights given, but missing validation_frame")
  return res

def _check_frame(x,y,response):  # y and response are only ever different for validation
  if x is None: return None
  x._eager()
  if y is not None:
    y._eager()
    response._eager()
    x[response._col_names[0]] = y
  return x

def _check_col(x,vx,vfr,col):
  x=_check_frame(x,col,col)
  vx= None if vfr is None else _check_frame(vx,vfr[col.names()[0]],vfr[col.names()[0]])
  return x,vx

def _model_build(x,y,vx,vy,algo,offsets,weights,fold_column,kwargs):
  if x is None:  raise ValueError("Missing features")
  x =_check_frame(x,y,y)
  vx=_check_frame(vx,vy,y)
  if offsets     is not None: x,vx=_check_col(x,vx,kwargs["validation_frame"],offsets)
  if weights     is not None: x,vx=_check_col(x,vx,kwargs["validation_frame"],weights)
  if fold_column is not None: x,vx=_check_col(x,vx,kwargs["validation_frame"],fold_column)

  kwargs['training_frame']=x._id
  if vx is not None: kwargs['validation_frame']=vx._id
  if y is not None:  kwargs['response_column']=y._col_names[0]

  kwargs = dict([(k, kwargs[k]._frame()._id if isinstance(kwargs[k], H2OFrame) else kwargs[k]) for k in kwargs if kwargs[k] is not None])

  do_future = kwargs.pop("do_future") if "do_future" in kwargs else False
  future_model = H2OModelFuture(H2OJob(H2OConnection.post_json("ModelBuilders/"+algo, **kwargs), job_type=(algo+" Model Build")), x)
  return future_model if do_future else _resolve_model(future_model, **kwargs)

def _resolve_model(future_model, **kwargs):
  future_model.poll()
  if '_rest_version' in kwargs.keys(): model_json = H2OConnection.get_json("Models/"+future_model.job.dest_key, _rest_version=kwargs['_rest_version'])["models"][0]
  else:                                model_json = H2OConnection.get_json("Models/"+future_model.job.dest_key)["models"][0]

  model_type = model_json["output"]["model_category"]
  if   model_type=="Binomial":     model = H2OBinomialModel(    future_model.job.dest_key,model_json)
  elif model_type=="Clustering":   model = H2OClusteringModel(  future_model.job.dest_key,model_json)
  elif model_type=="Regression":   model = H2ORegressionModel(  future_model.job.dest_key,model_json)
  elif model_type=="Multinomial":  model = H2OMultinomialModel( future_model.job.dest_key,model_json)
  elif model_type=="AutoEncoder":  model = H2OAutoEncoderModel( future_model.job.dest_key,model_json)
  elif model_type=="DimReduction": model = H2ODimReductionModel(future_model.job.dest_key,model_json)
  else: raise NotImplementedError(model_type)
  return model
