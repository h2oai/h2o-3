from ..model.model_base import ModelBase
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

  def fit(self,X,y=None,**params): raise EstimatorAttributeError(self,"fit")

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