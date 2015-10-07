from ..model.model_base import ModelBase
from ..model import build_model
import inspect, warnings

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

  def __init__(self):
    super(H2OEstimator, self).__init__(None, None, None)
    self.estimator=None
    self.parms=None

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
        The H2OFrame having the columns indicated by X and y (as well as any additional columns specified by fold, offset, and weights).
      offset_column : str, optional
        The name or index of the column in training_frame that holds the offsets.
      fold_column : str, optional
        The name or index of the column in training_frame that holds the per-row fold assignments.
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
    self.__dict__=build_model(self.parms).__dict__.copy()


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
      warn = "sklearn" not in mod.__name__
      if not warn: break
    if warn:
      warnings.warn("\n\n\t`fit` is not recommended outside of the sklearn framework. Use `train` instead.", UserWarning, stacklevel=2)
    training_frame = X.cbind(y) if y is not None else X
    X = X.names
    y = y.names[0] if y is not None else None
    self.train(X, y, training_frame, **params)

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