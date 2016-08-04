#!/usr/bin/env python
# -*- encoding: utf-8 -*-
#
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
from __future__ import division, print_function, absolute_import, unicode_literals
# noinspection PyUnresolvedReferences
from h2o.utils.compatibility import *

from ..model.model_base import ModelBase
from ..model.autoencoder import H2OAutoEncoderModel
from ..model.binomial import H2OBinomialModel
from ..model.clustering import H2OClusteringModel
from ..model.dim_reduction import H2ODimReductionModel
from ..model.multinomial import H2OMultinomialModel
from ..model.regression import H2ORegressionModel
from ..model.metrics_base import *
from h2o.utils.shared_utils import quoted
from h2o.utils.typechecks import is_int
import h2o
from h2o.job import H2OJob
from h2o.frame import H2OFrame
import inspect
import warnings
import types


class EstimatorAttributeError(AttributeError):
  def __init__(self,obj,method):
    super(AttributeError, self).__init__("No {} method for {}".format(method,obj.__class__.__name__))


class H2OEstimator(ModelBase):
  """H2O Estimators

  H2O Estimators implement the following methods for model construction:
    * start - Top-level user-facing API for asynchronous model build
    * join  - Top-level user-facing API for blocking on async model build
    * train - Top-level user-facing API for model building.
    * fit - Used by scikit-learn.

  Because H2OEstimator instances are instances of ModelBase, these objects can use the
  H2O model API.
  """

  def start(self,x,y=None,training_frame=None,offset_column=None,fold_column=None,weights_column=None,validation_frame=None,**params):
    """Asynchronous model build by specifying the predictor columns, response column, and any
    additional frame-specific values.

    To block for results, call join.

    Parameters
    ----------
      x : list
        A list of column names or indices indicating the predictor columns.

      y : str
        An index or a column name indicating the response column.

      training_frame : H2OFrame
        The H2OFrame having the columns indicated by x and y (as well as any
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
    """
    self._future=True
    self.train(x=x,
               y=y,
               training_frame=training_frame,
               offset_column=offset_column,
               fold_column=fold_column,
               weights_column=weights_column,
               validation_frame=validation_frame,
               **params)

  def join(self):
    self._future=False
    self._job.poll()
    self._job=None

  def train(self,x,y=None,training_frame=None,offset_column=None,fold_column=None,weights_column=None,validation_frame=None,max_runtime_secs=None,**params):
    """Train the H2O model by specifying the predictor columns, response column, and any
    additional frame-specific values.

    Parameters
    ----------
      x : list
        A list of column names or indices indicating the predictor columns.

      y : str | unicode
        An index or a column name indicating the response column.

      training_frame : H2OFrame
        The H2OFrame having the columns indicated by x and y (as well as any
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

      max_runtime_secs : float
        Maximum allowed runtime in seconds for model training. Use 0 to disable.
    """
    algo_params = locals()
    parms = self._parms.copy()
    if '__class__' in parms:  # FIXME: hackt for PY3
      del parms['__class__']
    parms.update({k:v for k, v in algo_params.items() if k not in ["self","params", "algo_params", "parms"] })
    y = algo_params["y"]
    tframe = algo_params["training_frame"]
    if tframe is None: raise ValueError("Missing training_frame")
    if y is not None:
      if isinstance(y, (list, tuple)):
        if len(y) == 1: parms["y"] = y[0]
        else: raise ValueError('y must be a single column reference')
      self._estimator_type = "classifier" if tframe[y].isfactor() else "regressor"
    self.build_model(parms)

  def build_model(self, algo_params):
    if algo_params["training_frame"] is None: raise ValueError("Missing training_frame")
    x = algo_params.pop("x")
    y = algo_params.pop("y",None)
    training_frame = algo_params.pop("training_frame")
    validation_frame = algo_params.pop("validation_frame",None)
    is_auto_encoder = (algo_params is not None) and ("autoencoder" in algo_params and algo_params["autoencoder"])
    algo = self._compute_algo()
    is_unsupervised = is_auto_encoder or algo == "pca" or algo == "svd" or algo == "kmeans" or algo == "glrm"
    if is_auto_encoder and y is not None: raise ValueError("y should not be specified for autoencoder.")
    if not is_unsupervised and y is None: raise ValueError("Missing response")
    self._model_build(x, y, training_frame, validation_frame, algo_params)

  def _model_build(self, x, y, tframe, vframe, kwargs):
    kwargs['training_frame'] = tframe
    if vframe is not None: kwargs["validation_frame"] = vframe
    if is_int(y): y = tframe.names[y]
    if y is not None: kwargs['response_column'] = y
    if not isinstance(x, (list,tuple)): x=[x]
    if is_int(x[0]):
      x = [tframe.names[i] for i in x]
    offset = kwargs["offset_column"]
    folds  = kwargs["fold_column"]
    weights= kwargs["weights_column"]
    ignored_columns = list(set(tframe.names) - set(x + [y,offset,folds,weights]))
    kwargs["ignored_columns"] = None if ignored_columns==[] else [quoted(col) for col in ignored_columns]
    kwargs["interactions"] = None if ("interactions" not in kwargs or kwargs["interactions"] is None) else [quoted(col) for col in kwargs["interactions"]]
    kwargs = dict([(k, H2OEstimator._keyify_if_H2OFrame(kwargs[k])) for k in kwargs])  # gruesome one-liner
    rest_ver = kwargs.pop("_rest_version") if "_rest_version" in kwargs else 3
    algo = self._compute_algo()

    model = H2OJob(h2o.api("POST /%d/ModelBuilders/%s" % (rest_ver, algo), data=kwargs),
                   job_type=(algo + " Model Build"))

    if self._future:
      self._job = model
      return

    model.poll()
    model_json = h2o.api("GET /%d/Models/%s" % (rest_ver, model.dest_key))["models"][0]
    self._resolve_model(model.dest_key, model_json)

  @staticmethod
  def _keyify_if_H2OFrame(item):
    if isinstance(item, H2OFrame):
      return item.frame_id
    elif isinstance(item, list) and all(i is None or isinstance(i, H2OFrame) for i in item):
      return [quoted(i) if i is None else quoted(i.frame_id) for i in item]
    else:
      return item

  def _resolve_model(self, model_id, model_json):
    metrics_class, model_class = H2OEstimator._metrics_class(model_json)
    m = model_class()
    m._id = model_id
    m._model_json = model_json
    m._metrics_class = metrics_class
    m._parms = self._parms
    m._estimator_type = self._estimator_type

    if model_id is not None and model_json is not None and metrics_class is not None:
      # build Metric objects out of each metrics
      for metric in ["training_metrics", "validation_metrics", "cross_validation_metrics"]:
        if metric in model_json["output"]:
          if model_json["output"][metric] is not None:
            if metric=="cross_validation_metrics":
              m._is_xvalidated=True
            model_json["output"][metric] = metrics_class(model_json["output"][metric],metric,model_json["algo"])

      if m._is_xvalidated: m._xval_keys= [i["name"] for i in model_json["output"]["cross_validation_models"]]

      # build a useful dict of the params
      for p in m._model_json["parameters"]:
        m.parms[p["name"]]=p
    H2OEstimator.mixin(self,model_class)
    self.__dict__.update(m.__dict__.copy())

  def _compute_algo(self):
    name = self.__class__.__name__
    if name == "H2ODeepLearningEstimator":       return "deeplearning"
    if name == "H2OAutoEncoderEstimator":        return "deeplearning"
    if name == "H2OGradientBoostingEstimator":   return "gbm"
    if name == "H2OGeneralizedLinearEstimator":  return "glm"
    if name == "H2OGeneralizedLowRankEstimator": return "glrm"
    if name == "H2OKMeansEstimator":             return "kmeans"
    if name == "H2ONaiveBayesEstimator":         return "naivebayes"
    if name == "H2ORandomForestEstimator":       return "drf"
    if name == "H2OPCA":                         return "pca"
    if name == "H2OSVD":                         return "svd"

  @staticmethod
  def mixin(obj,cls):
    for name in cls.__dict__:
      if name.startswith('__') and name.endswith('__') or not type(cls.__dict__[name])==types.FunctionType:
        continue
      obj.__dict__[name]=cls.__dict__[name].__get__(obj)

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
      The current instance of H2OEstimator for method chaining.
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
    self.train(X, y, training_frame, **params)
    return self

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
    out = dict()
    for key,value in self.parms.items():
      if deep and isinstance(value, H2OEstimator):
        deep_items = list(value.get_params().items())
        out.update((key + '__' + k, val) for k, val in deep_items)
      out[key] = value
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
    self._parms.update(parms)
    return self

  @staticmethod
  def _metrics_class(model_json):
    model_type = model_json["output"]["model_category"]
    if model_type=="Binomial":       metrics_class = H2OBinomialModelMetrics;      model_class = H2OBinomialModel
    elif model_type=="Clustering":   metrics_class = H2OClusteringModelMetrics;    model_class = H2OClusteringModel
    elif model_type=="Regression":   metrics_class = H2ORegressionModelMetrics;    model_class = H2ORegressionModel
    elif model_type=="Multinomial":  metrics_class = H2OMultinomialModelMetrics;   model_class = H2OMultinomialModel
    elif model_type=="AutoEncoder":  metrics_class = H2OAutoEncoderModelMetrics;   model_class = H2OAutoEncoderModel
    elif model_type=="DimReduction": metrics_class = H2ODimReductionModelMetrics;  model_class = H2ODimReductionModel
    else: raise NotImplementedError(model_type)
    return [metrics_class,model_class]
