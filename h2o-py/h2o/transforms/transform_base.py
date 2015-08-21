class TransformAttributeError(AttributeError):
  def __init__(self,obj,method):
    super(AttributeError, self).__init__("No {} method for {}".format(method,obj.__class__.__name__))


class H2OTransformer(object):
  """H2O Transforms

  H2O Transforms implement the following methods
    * fit
    * transform
    * fit_transform
    * inverse_transform
    * export
  """

  def fit(self,X,y=None,**params):               raise TransformAttributeError(self,"fit")
  def transform(self,X,y=None,**params):         raise TransformAttributeError(self,"transform")
  def inverse_transform(self,X,y=None,**params): raise TransformAttributeError(self,"inverse_transform")
  def export(self,X,y,**params):                 raise TransformAttributeError(self,"export")
  def fit_transform(self, X, y=None, **params):
      return self.fit(X, y, **params).transform(X)