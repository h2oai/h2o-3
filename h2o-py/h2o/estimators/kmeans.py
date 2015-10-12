from .estimator_base import *


class H2OKMeansEstimator(H2OEstimator,H2OBinomialModel,H2OMultinomialModel,H2ORegressionModel):
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
        values are "Random": for random initialization,
        "PlusPlus": for k-means plus initialization,
        or "Furthest": for initialization at the furthest point from each successive
        center. Additionally, the user may specify a the initial centers as a matrix,
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
    self.parms = locals()
    self.parms = {k:v for k,v in self.parms.iteritems() if k!="self"}
    self.parms["algo"] = "kmeans"