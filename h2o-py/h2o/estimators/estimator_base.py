from ..model.metrics_base import *
from ..model.model_base import ModelBase
from ..h2o import H2OConnection, H2OJob, H2OFrame
from ..model.binomial import H2OBinomialModel
from ..model.multinomial import H2OMultinomialModel
from ..model.regression import H2ORegressionModel
from ..model.autoencoder import H2OAutoEncoderModel
import inspect
import warnings


class EstimatorAttributeError(AttributeError):
  def __init__(self,obj,method):
    super(AttributeError, self).__init__("No {} method for {}".format(method,obj.__class__.__name__))


class H2OEstimator(ModelBase):
  """H2O Estimators

  H2O Estimators implement the following methods for model construction:
    * train - Top-level user-facing API for model building.
    * fit - Used by scikit-learn.

  Because H2OEstimator instances are instances of ModelBase, these objects can use the
  H2O model API.
  """

  def train(self,X,y=None,training_frame=None,offset_column=None,fold_column=None,weights_column=None,validation_frame=None,**params):
    """Train the H2O model by specifying the predictor columns, response column, and any
    additional frame-specific values.

    Parameters
    ----------
      X : list
        A list of column names or indices indicating the predictor columns.
      y : str
        An index or a column name indicating the response column.
      training_frame : H2OFrame
        The H2OFrame having the columns indicated by X and y (as well as any
        additional columns specified by fold, offset, and weights).
      offset_column : str, optional
        The name or index of the column in training_frame that holds the offsets.
      fold_column : str, optional
        The name or index of the column in training_frame that holds the per-row fold
        assignments.
      weights_column : str, optional
        The name or index of the column in training_frame that holds the per-row weights.
      validation_frame : H2OFrame, optional
        H2OFrame with validation data to be scored on while training.

    Returns
    -------
      Returns self.
    """
    algo_params = locals()
    self.parms.update({k:v for k, v in algo_params.iteritems() if k not in ["self","params", "algo_params"] })
    y = algo_params["y"]
    tframe = algo_params["training_frame"]
    if tframe is None: raise ValueError("Missing training_frame")
    if y is not None:
      self._estimator_type = "classifier" if tframe[y].isfactor() else "regressor"
    self.build_model(self.parms)
    return self

  def build_model(self, algo_params):
    if algo_params["training_frame"] is None: raise ValueError("Missing training_frame")
    x = algo_params.pop("X")
    y = algo_params.pop("y",None)
    training_frame = algo_params.pop("training_frame")
    validation_frame = algo_params.pop("validation_frame",None)
    algo  = algo_params.pop("algo")
    is_auto_encoder = algo_params is not None and "autoencoder" in algo_params and algo_params["autoencoder"] is not None
    is_unsupervised = is_auto_encoder or algo == "pca" or algo == "kmeans"
    if is_auto_encoder and y is not None: raise ValueError("y should not be specified for autoencoder.")
    if not is_unsupervised and y is None: raise ValueError("Missing response")
    self._model_build(x, y, training_frame, validation_frame, algo, algo_params)

  def _model_build(self, x, y, tframe, vframe, algo, kwargs):
    kwargs['training_frame'] = tframe
    if vframe is not None: kwargs["validation_frame"] = vframe
    if y is not None:  kwargs['response_column'] = tframe[y].names[0]
    kwargs = dict([(k, (kwargs[k]._frame()).frame_id if isinstance(kwargs[k], H2OFrame) else kwargs[k]) for k in kwargs if kwargs[k] is not None])  # gruesome one-liner

    #### POLL MODEL FOR COMPLETION ####
    model = H2OJob(H2OConnection.post_json("ModelBuilders/"+algo, **kwargs), job_type=(algo+" Model Build")).poll()
    ###################################

    if '_rest_version' in kwargs.keys(): model_json = H2OConnection.get_json("Models/"+model.dest_key, _rest_version=kwargs['_rest_version'])["models"][0]
    else:                                model_json = H2OConnection.get_json("Models/"+model.dest_key)["models"][0]

    model_type = model_json["output"]["model_category"]
    if   model_type=="Binomial":     self._make_model(model.dest_key,model_json, H2OBinomialModelMetrics)
    elif model_type=="Clustering":   self._make_model(model.dest_key,model_json, H2OClusteringModelMetrics)
    elif model_type=="Regression":   self._make_model(model.dest_key,model_json, H2ORegressionModelMetrics)
    elif model_type=="Multinomial":  self._make_model(model.dest_key,model_json, H2OMultinomialModelMetrics)
    elif model_type=="AutoEncoder":  self._make_model(model.dest_key,model_json, H2OAutoEncoderModelMetrics)
    elif model_type=="DimReduction": self._make_model(model.dest_key,model_json, H2ODimReductionModelMetrics)
    else: raise NotImplementedError(model_type)

  def _make_model(self, key, model_json, metrics_class):
    self._id = key
    self._model_json = model_json
    self._metrics_class = metrics_class

    if key is not None and model_json is not None and metrics_class is not None:
      # build Metric objects out of each metrics
      for metric in ["training_metrics", "validation_metrics", "cross_validation_metrics"]:
        if metric in model_json["output"]:
          if model_json["output"][metric] is not None:
            if metric=="cross_validation_metrics":
              self._is_xvalidated=True
            model_json["output"][metric] = metrics_class(model_json["output"][metric],metric,model_json["algo"])

      if self._is_xvalidated: self._xval_keys= [i["name"] for i in model_json["output"]["cross_validation_models"]]

      # build a useful dict of the params
      for p in self._model_json["parameters"]: self.parms[p["label"]]=p


  ##### Scikit-learn Interface Methods #####
  def fit(self, X, y=None, **params):
    """Fit an H2O model as part of a scikit-learn pipeline or grid search.

    A warning will be issued if a caller other than sklearn attempts to use this method.

    Parameters
    ----------
      X : H2OFrame
        An H2OFrame consisting of the predictor variables.
      y : H2OFrame, optional
        An H2OFrame consisting of the response variable.
      params : optional
        Extra arguments.

    Returns
    -------
      None
    """
    stk = inspect.stack()[1:]
    warn = True
    for s in stk:
      mod = inspect.getmodule(s[0])
      if mod: 
        warn = "sklearn" not in mod.__name__
        if not warn: break
    if warn:
      warnings.warn("\n\n\t`fit` is not recommended outside of the sklearn framework. Use `train` instead.", UserWarning, stacklevel=2)
    training_frame = X.cbind(y) if y is not None else X
    X = X.names
    y = y.names[0] if y is not None else None
    return self.train(X, y, training_frame, **params)

  def get_params(self, deep=True):
    """Useful method for obtaining parameters for this estimator. Used primarily for
    sklearn Pipelines and sklearn grid search.

    Parameters
    ----------
      deep : bool, optional
        If True, return parameters of all sub-objects that are estimators.

    Returns
    -------
      A dict of parameters
    """
    algo = self.parms.pop("algo")
    out = dict()
    for key,value in self.parms.iteritems():
      if deep and isinstance(value, H2OEstimator):
        deep_items = value.get_params().items()
        out.update((key + '__' + k, val) for k, val in deep_items)
      out[key] = value
    self.parms["algo"] = algo
    return out

  def set_params(self, **parms):
    """Used by sklearn for updating parameters during grid search.

    Parameters
    ----------
      parms : dict
        A dictionary of parameters that will be set on this model.

    Returns
    -------
      Returns self, the current estimator object with the parameters all set as desired.
    """
    self.parms.update(parms)
    return self
