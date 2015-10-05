from ..model.model_base import ModelBase
from ..model import build_model

class EstimatorAttributeError(AttributeError):
  def __init__(self,obj,method):
    super(AttributeError, self).__init__("No {} method for {}".format(method,obj.__class__.__name__))


class H2OEstimator(ModelBase):
  """H2O Estimators

  H2O Estimators implement the following methods
    * fit

  Because H2OEstimator instances are instances of ModelBase, these objects can use the
  H2O model API.
  """

  def __init__(self):
    super(H2OEstimator, self).__init__(None, None, None)
    self.estimator=None
    self.parms=None

  def fit(self,X,y=None,training_frame=None,offset_column=None,fold_column=None,weights_column=None,validation_frame=None,**params):
    """Fit the H2O model by specifying the predictor columns, response column, and any
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
    raise EstimatorAttributeError(self,"fit")

  def get_params(self, deep=True):
    """
    Get parameters for this estimator.

    :param deep: (Optional) boolean; if True, return parameters of all subobjects that are estimators.
    :return: A dict of parameters.
    """
    out = dict()
    for key,value in self.parms.iteritems():
      if deep and isinstance(value, H2OEstimator):
        deep_items = value.get_params().items()
        out.update((key + '__' + k, val) for k, val in deep_items)
      out[key] = value
    return out

  def set_params(self, **parms):
    self.parms.update(parms)
    return self

  def model_build(self, algo_params):
    self.parms.update({k:v for k, v in algo_params.iteritems() if k not in ["self","params"] })
    y = algo_params["y"]
    tframe = algo_params["training_frame"]
    if tframe is None: raise ValueError("Missing training_frame")
    if y is not None:
      self._estimator_type = "classifier" if tframe[y].isfactor() else "regressor"
    self.__dict__=build_model(self.parms).__dict__.copy()