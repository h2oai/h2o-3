from ..estimators.estimator_base import H2OEstimator


class H2OPCA(H2OEstimator):
  """
  Principal Component Analysis
  """

  def __init__(self, model_id=None, k=None, max_iterations=None, seed=None,
               transform=("NONE","DEMEAN","DESCALE","STANDARDIZE","NORMALIZE"),
               use_all_factor_levels=False,
               pca_method=("GramSVD", "Power", "GLRM")):
    """
    Principal Components Analysis

    Parameters
    ----------
      model_id : str, optional
        The unique hex key assigned to the resulting model. Automatically generated if
        none is provided.
      k : int
        The number of principal components to be computed. This must be between 1 and
        min(ncol(training_frame), nrow(training_frame)) inclusive.
      transform : str
        A character string that indicates how the training data should be transformed
        before running PCA. Possible values are
          "NONE": for no transformation,
          "DEMEAN": for subtracting the mean of each column,
          "DESCALE": for dividing by the standard deviation of each column,
          "STANDARDIZE": for demeaning and descaling, and
          "NORMALIZE": for demeaning and dividing each column by its range (max - min).
      seed : int, optional
         Random seed used to initialize the right singular vectors at the beginning of
         each power method iteration.
      max_iterations : int, optional
        The maximum number of iterations when pca_method is "Power"
      use_all_factor_levels : bool, optional
        A logical value indicating whether all factor levels should be included in each
        categorical column expansion. If False, the indicator column corresponding to the
        first factor level of every categorical variable will be dropped. Default False.
      pca_method : str
        A character string that indicates how PCA should be calculated. Possible values
        are
          "GramSVD": distributed computation of the Gram matrix followed by a local SVD
                     using the JAMA package,
          "Power": computation of the SVD using the power iteration method,
          "GLRM": fit a generalized low rank model with an l2 loss function
                  (no regularization) and solve for the SVD using local matrix algebra.

    Returns
    -------
      A new instance of H2OPCA.
    """
    super(H2OPCA, self).__init__()
    self._parms = locals()
    self._parms = {k: v for k, v in self._parms.iteritems() if k != "self"}
    self._parms["pca_method"] = "GramSVD" if isinstance(pca_method, tuple) else pca_method
    self._parms["transform"] = "NONE" if isinstance(transform, tuple) else transform

  def fit(self, X,y=None,  **params):
    return super(H2OPCA, self).fit(X)

  def transform(self, X, y=None, **params):
    """
    Transform the given H2OFrame with the fitted PCA model.

    Parameters
    ----------
      X : H2OFrame
        May contain NAs and/or categorical data.
      y : H2OFrame
        Ignored for PCA. Should be None.
      params : dict
        Ignored.

    Returns
    -------
      The input H2OFrame transformed by the Principal Components
    """
    return self.predict(X)


class H2OSVD(H2OEstimator):
  """Singular Value Decomposition"""

  def __init__(self, nv=None, max_iterations=None, transform=None, seed=None,
               use_all_factor_levels=None, svd_method=None):
    """
    Singular value decomposition of an H2OFrame.

    Parameters
    ----------
      nv : int
        The number of right singular vectors to be computed. This must be between 1 and
        min(ncol(training_frame), snrow(training_frame)) inclusive.
      max_iterations : int
        The maximum number of iterations to run each power iteration loop. Must be
        between 1 and 1e6 inclusive.
      transform : str
        A character string that indicates how the training data should be transformed
        before running SVD. Possible values are
          "NONE": for no transformation,
          "DEMEAN": for subtracting the mean of each column,
          "DESCALE": for dividing by the standard deviation of each column,
          "STANDARDIZE": for demeaning and descaling, and
          "NORMALIZE": for demeaning and dividing each column by its range (max - min).
      seed : int, optional
        Random seed used to initialize the right singular vectors at the beginning of each
        power method iteration.
      use_all_factor_levels : bool, optional
        A logical value indicating whether all factor levels should be included in each
        categorical column expansion.
        If FALSE, the indicator column corresponding to the first factor level of every
        categorical variable will be dropped. Defaults to TRUE.
      svd_method : str
        A character string that indicates how SVD should be calculated.
        Possible values are
          "GramSVD": distributed computation of the Gram matrix followed by a local SVD
                     using the JAMA package,
        "Power": computation of the SVD using the power iteration method,
        "Randomized": approximate SVD by projecting onto a random subspace.

    Returns
    -------
      Return a new H2OSVD
    """
    super(H2OSVD, self).__init__()
    self._parms = locals()
    self._parms = {k: v for k, v in self._parms.iteritems() if k != "self"}
    self._parms["svd_method"] = "GramSVD" if isinstance(svd_method, tuple) else svd_method
    self._parms["transform"] = "NONE" if isinstance(transform, tuple) else transform
    self._parms["algo"] = "svd"
    self._parms['_rest_version']=99

  def fit(self, X,y=None,  **params):
    return super(H2OSVD, self).fit(X)

  def transform(self, X, y=None, **params):
    """
    Transform the given H2OFrame with the fitted SVD model.

    Parameters
    ----------
      X : H2OFrame
        May contain NAs and/or categorical data.
      y : H2OFrame
        Ignored for SVD. Should be None.
      params : dict
        Ignored.

    Returns
    -------
      The input H2OFrame transformed by the SVD.
    """
    return self.predict(X)