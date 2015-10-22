from .estimator_base import H2OEstimator


class H2OGeneralizedLowRankEstimator(H2OEstimator):
  def __init__(self,k=None, max_iterations=None, transform=None, seed=None,
               ignore_const_cols=None,loss=None, multi_loss=None, loss_by_col=None,
               loss_by_col_idx=None, regularization_x=None, regularization_y=None,
               gamma_x=None, gamma_y=None, init_step_size=None, min_step_size=None,
               init=None, svd_method=None, user_x=None, user_y=None, recover_svd=None):
    """
    Builds a generalized low rank model of a H2O dataset.

    Parameters
    ----------
      k : int
        The rank of the resulting decomposition. This must be between 1 and the number of
        columns in the training frame inclusive.
      max_iterations : int
        The maximum number of iterations to run the optimization loop. Each iteration
        consists of an update of the X matrix, followed by an update of the Y matrix.
      transform : str
        A character string that indicates how the training data should be transformed
        before running GLRM. Possible values are
            "NONE": for no transformation,
            "DEMEAN": for subtracting the mean of each column,
            "DESCALE": for dividing by the standard deviation of each column,
            "STANDARDIZE": for demeaning and descaling, and
            "NORMALIZE": for demeaning and dividing each column by its range (max - min).
      seed : int, optional
        Random seed used to initialize the X and Y matrices.
      ignore_const_cols : bool, optional
        A logical value indicating whether to ignore constant columns in the training frame.
        A column is constant if all of its non-missing values are the same value.
      loss : str
        A character string indicating the default loss function for numeric columns.
        Possible values are
            "Quadratic" (default),
            "Absolute",
            "Huber",
            "Poisson",
            "Hinge", and
            "Logistic".
      multi_loss : str
        A character string indicating the default loss function for enum columns. Possible
        values are "Categorical" and "Ordinal".
      loss_by_col : str, optional
        A list of strings indicating the loss function for specific columns by
        corresponding index in loss_by_col_idx. Will override loss for numeric columns
        and multi_loss for enum columns.
      loss_by_col_idx : str, optional
        A list of column indices to which the corresponding loss functions in loss_by_col
        are assigned. Must be zero indexed.
      regularization_x : str
        A character string indicating the regularization function for the X matrix.
        Possible values are
            "None" (default),
            "Quadratic",
            "L2",
            "L1",
            "NonNegative",
            "OneSparse",
            "UnitOneSparse", and
            "Simplex".
      regularization_y : str
        A character string indicating the regularization function for the Y matrix.
        Possible values are
            "None" (default),
            "Quadratic",
            "L2",
            "L1",
            "NonNegative",
            "OneSparse",
            "UnitOneSparse", and
            "Simplex".
      gamma_x : float
        The weight on the X matrix regularization term.
      gamma_y : float
        The weight on the Y matrix regularization term.
      init_step_size : float
        Initial step size. Divided by number of columns in the training frame when
        calculating the proximal gradient update. The algorithm begins at init_step_size
        and decreases the step size at each iteration until a termination condition is
        reached.
      min_step_size : float
        Minimum step size upon which the algorithm is terminated.
      init : str
        A character string indicating how to select the initial X and Y matrices.
        Possible values are
            "Random": for initialization to a random array from the standard normal
                      distribution,
            "PlusPlus": for initialization using the clusters from k-means++
                        initialization,
            "SVD": for initialization using the first k (approximate) right singular
                   vectors, and
            "User": user-specified initial X and Y frames
                    (must set user_y and user_x arguments).
      svd_method : str
        A character string that indicates how SVD should be calculated during
        initialization. Possible values are
            "GramSVD": distributed computation of the Gram matrix followed by a local
                      SVD using the JAMA package,
            "Power": computation of the SVD using the power iteration method,
            "Randomized": approximate SVD by projecting onto a random subspace.
      user_x : H2OFrame, optional
        An H2OFrame object specifying the initial X matrix. Only used when init = "User".
      user_y : H2OFrame, optional
        An H2OFrame object specifying the initial Y matrix. Only used when init = "User".
      recover_svd : bool
        A logical value indicating whether the singular values and eigenvectors should be
        recovered during post-processing of the generalized low rank decomposition.

    Returns
    -------
      A new H2OGeneralizedLowRankEstimator instance.
    """
    super(H2OGeneralizedLowRankEstimator, self).__init__()
    self._parms = locals()
    self._parms = {k:v for k,v in self._parms.iteritems() if k!="self"}
    self._parms['_rest_version']=99

  @property
  def max_iterations(self):
    return self._parms["max_iterations"]

  @max_iterations.setter
  def max_iterations(self, value):
    self._parms["max_iterations"] = value

  @property
  def transform(self):
    return self._parms["transform"]

  @transform.setter
  def transform(self, value):
    self._parms["transform"] = value

  @property
  def seed(self):
    return self._parms["seed"]

  @seed.setter
  def seed(self, value):
    self._parms["seed"] = value

  @property
  def ignore_const_cols(self):
    return self._parms["ignore_const_cols"]

  @ignore_const_cols.setter
  def ignore_const_cols(self, value):
    self._parms["ignore_const_cols"] = value

  @property
  def loss(self):
    return self._parms["loss"]

  @loss.setter
  def loss(self, value):
    self._parms["loss"] = value

  @property
  def multi_loss(self):
    return self._parms["multi_loss"]

  @multi_loss.setter
  def multi_loss(self, value):
    self._parms["multi_loss"] = value

  @property
  def loss_by_col(self):
    return self._parms["loss_by_col"]

  @loss_by_col.setter
  def loss_by_col(self, value):
    self._parms["loss_by_col"] = value

  @property
  def loss_by_col_idx(self):
    return self._parms["loss_by_col_idx"]

  @loss_by_col_idx.setter
  def loss_by_col_idx(self, value):
    self._parms["loss_by_col_idx"] = value

  @property
  def regularization_x(self):
    return self._parms["regularization_x"]

  @regularization_x.setter
  def regularization_x(self, value):
    self._parms["regularization_x"] = value

  @property
  def regularization_y(self):
    return self._parms["regularization_y"]

  @regularization_y.setter
  def regularization_y(self, value):
    self._parms["regularization_y"] = value

  @property
  def gamma_x(self):
    return self._parms["gamma_x"]

  @gamma_x.setter
  def gamma_x(self, value):
    self._parms["gamma_x"] = value

  @property
  def gamma_y(self):
    return self._parms["gamma_y"]

  @gamma_y.setter
  def gamma_y(self, value):
    self._parms["gamma_y"] = value

  @property
  def init_step_size(self):
    return self._parms["init_step_size"]

  @init_step_size.setter
  def init_step_size(self, value):
    self._parms["init_step_size"] = value

  @property
  def min_step_size(self):
    return self._parms["min_step_size"]

  @min_step_size.setter
  def min_step_size(self, value):
    self._parms["min_step_size"] = value

  @property
  def init(self):
    return self._parms["init"]

  @init.setter
  def init(self, value):
    self._parms["init"] = value

  @property
  def svd_method(self):
    return self._parms["svd_method"]

  @svd_method.setter
  def svd_method(self, value):
    self._parms["svd_method"] = value

  @property
  def user_x(self):
    return self._parms["user_x"]

  @user_x.setter
  def user_x(self, value):
    self._parms["user_x"] = value

  @property
  def user_y(self):
    return self._parms["user_y"]

  @user_y.setter
  def user_y(self, value):
    self._parms["user_y"] = value

  @property
  def recover_svd(self):
    return self._parms["recover_svd"]

  @recover_svd.setter
  def recover_svd(self, value):
    self._parms["recover_svd"] = value

