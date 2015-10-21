from .estimator_base import *


class H2OKMeansEstimator(H2OEstimator):
  def __init__(self, model_id=None, k=None, max_iterations=None,standardize=None,init=None,seed=None,
               nfolds=None,fold_assignment=None, user_points=None,ignored_columns=None,
               score_each_iteration=None, keep_cross_validation_predictions=None,
               ignore_const_cols=None,checkpoint=None):
    """
    Performs k-means clustering on an H2O dataset.

    Parameters
    ----------
      model_id : str, optional
        The unique id assigned to the resulting model. If none is given, an id will
        automatically be generated.
      k : int
        The number of clusters. Must be between 1 and 1e7 inclusive. k may be omitted
        if the user specifies the initial centers in the init parameter. If k is not
        omitted, in this case, then it should be equal to the number of user-specified
        centers.
      max_iterations : int
        The maximum number of iterations allowed. Must be between 0 and 1e6 inclusive.
      standardize : bool
        Indicates whether the data should be standardized before running k-means.
      init : str
        A character string that selects the initial set of k cluster centers. Possible
        values are
            "Random": for random initialization,
            "PlusPlus": for k-means plus initialization, or
            "Furthest": for initialization at the furthest point from each successive
                        center.

        Additionally, the user may specify a the initial centers as a matrix,
        data.frame, H2OFrame, or list of vectors. For matrices, data.frames,
        and H2OFrames, each row of the respective structure is an initial center. For
        lists of vectors, each vector is an initial center.
      seed : int, optional
        Random seed used to initialize the cluster centroids.
      nfolds : int, optional
        Number of folds for cross-validation. If nfolds >= 2, then validation must
        remain empty.
      fold_assignment : str
        Cross-validation fold assignment scheme, if fold_column is not specified
        Must be "AUTO", "Random" or "Modulo"

    :return: An instance of H2OClusteringModel.
    """
    super(H2OKMeansEstimator, self).__init__()
    self._parms = locals()
    self._parms = {k:v for k,v in self._parms.iteritems() if k!="self"}

  @property
  def k(self):
    return self.parms["k"]

  @k.setter
  def k(self, value):
    self.parms["k"] = value

  @property
  def max_iterations(self):
    return self.parms["max_iterations"]

  @max_iterations.setter
  def max_iterations(self, value):
    self.parms["max_iterations"] = value

  @property
  def standardize(self):
    return self.parms["standardize"]

  @standardize.setter
  def standardize(self, value):
    self.parms["standardize"] = value

  @property
  def init(self):
    return self.parms["init"]

  @init.setter
  def init(self, value):
    self.parms["init"] = value

  @property
  def seed(self):
    return self.parms["seed"]

  @seed.setter
  def seed(self, value):
    self.parms["seed"] = value

  @property
  def nfolds(self):
    return self.parms["nfolds"]

  @nfolds.setter
  def nfolds(self, value):
    self.parms["nfolds"] = value

  @property
  def fold_assignment(self):
    return self.parms["fold_assignment"]

  @fold_assignment.setter
  def fold_assignment(self, value):
    self.parms["fold_assignment"] = value

  @property
  def user_points(self):
    return self.parms["user_points"]

  @user_points.setter
  def user_points(self, value):
    self.parms["user_points"] = value

  @property
  def ignored_columns(self):
    return self.parms["ignored_columns"]

  @ignored_columns.setter
  def ignored_columns(self, value):
    self.parms["ignored_columns"] = value

  @property
  def score_each_iteration(self):
    return self.parms["score_each_iteration"]

  @score_each_iteration.setter
  def score_each_iteration(self, value):
    self.parms["score_each_iteration"] = value

  @property
  def keep_cross_validation_predictions(self):
    return self.parms["keep_cross_validation_predictions"]

  @keep_cross_validation_predictions.setter
  def keep_cross_validation_predictions(self, value):
    self.parms["keep_cross_validation_predictions"] = value

  @property
  def ignore_const_cols(self):
    return self.parms["ignore_const_cols"]

  @ignore_const_cols.setter
  def ignore_const_cols(self, value):
    self.parms["ignore_const_cols"] = value

  @property
  def checkpoint(self):
    return self.parms["checkpoint"]

  @checkpoint.setter
  def checkpoint(self, value):
    self.parms["checkpoint"] = value

