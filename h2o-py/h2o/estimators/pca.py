from .estimator_base import H2OEstimator


class H2OPCAEstimator(H2OEstimator):
  def __init__(self, model_id=None, n_components=None, transform=None, seed=None,
               max_iterations=None, use_all_factor_levels=None, pca_method=None):
    """
    Principal Components Analysis

    Parameters
    ----------
      model_id : str, optional
        The unique hex key assigned to the resulting model. Automatically generated if
        none is provided.
      n_components : int
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
      A new instance of H2OPCAEstimator.
    """
    super(H2OPCAEstimator, self).__init__()
    self.parms = locals()
    self.parms = {k:v for k,v in self.parms.iteritems() if k!="self"}
    self.parms["algo"] = "pca"