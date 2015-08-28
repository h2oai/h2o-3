from .transform_base import H2OTransformer
class H2OScaler(H2OTransformer):
  """
  Standardize an H2OFrame by demeaning and scaling each column.

  The default scaling will result in an H2OFrame with columns
  having zero mean and unit variance. Users may specify the
  centering and scaling values used in the standardization of
  the H2OFrame.
  """
  def __init__(self, center=True, scale=True):
    """
    :param center: A boolean or list of numbers. If True, then columns will be demeaned before scaling.
                    If False, then columns will not be demeaned before scaling.
                    If centers is an array of numbers, then len(centers) must match the number of
                    columns in the dataset. Each value is removed from the respective column
                    before scaling.
    :param scale: A boolean or list of numbers. If True, then columns will be scaled by the column's standard deviation.
                   If False, then columns will not be scaled.
                   If scales is an array, then len(scales) must match the number of columns in
                   the dataset. Each column is scaled by the respective value in this array.
    :return: An instance of H2OScaler.
    """
    self.parms = locals()
    self.parms = {k:v for k,v in self.parms.iteritems() if k!="self"}
    if center is None or scale is None: raise ValueError("centers and scales must not be None.")
    self._means=None
    self._stds=None

  @property
  def means(self): return self._means

  @property
  def stds(self): return self._stds

  def fit(self,X,y=None, **params):
    """
    Fit this object by computing the means and standard deviations used by the transform
    method.

    :param X: An H2OFrame; may contain NAs and/or categoricals.
    :param y: None (Ignored)
    :param params: Ignored
    :return: This H2OScaler instance
    """
    if isinstance(self.parms["center"],(tuple,list)): self._means = self.parms["center"]
    if isinstance(self.parms["scale"], (tuple,list)): self._stds  = self.parms["scale"]
    if self.means is None and self.parms["center"]:   self._means = X.mean()
    else:                                             self._means = False
    if self.stds  is None and self.parms["scale"]:    self._stds  = X.sd()
    else:                                             self._stds  = False
    return self

  def transform(self,X,y=None,**params):
    """
    Scale an H2OFrame with the fitted means and standard deviations.

    :param X: An H2OFrame; may contain NAs and/or categoricals.
    :param y: None (Ignored)
    :param params: (Ignored)
    :return: A scaled H2OFrame.
    """
    return X.scale(self.means,self.stds)._frame()

  def inverse_transform(self,X,y=None,**params):
    """
    Undo the scale transformation.

    :param X: An H2OFrame; may contain NAs and/or categoricals.
    :param y: None (Ignored)
    :param params: (Ignored)
    :return: An H2OFrame
    """
    for i in X.ncol:
      X[i] = self.means[i] + self.stds[i]*X[i]
    return X