"""
Clustering Models
"""

from metrics_base import *


class H2OClusteringModel(ModelBase):

  def __init__(self, dest_key, model_json):
    super(H2OClusteringModel, self).__init__(dest_key, model_json,H2OClusteringModelMetrics)

  def size(self, train=False, valid=False):
    """
    Get the sizes of each cluster.

    :param train: If train is True, then return the sizes of clusters based on the training data. If both train and valid are False, then train=True is assumed.
    :param valid: If valid is True, then return the sizes of clusters based on the validation data. If both train and valid are True, then validation data is returned.
    :return: the sizes of clusters for either the training or validation dataset.
    """
    tm = ModelBase._get_metrics(self,*ModelBase._train_or_valid(train, valid))
    return [ v[2] for v in  tm._metric_json["centroid_stats"].cell_values]

  def num_iterations(self, train=False, valid=False):
    """
    Get the number of iterations that it took to converge or reach max iterations.

    :return: number of iterations (integer)
    """
    o = self._model_json["output"]
    return o["model_summary"].cell_values[0][o["model_summary"].col_header.index('number_of_iterations')]

  def betweenss(self, train, valid):
    """
    Get the between cluster sum of squares.

    :param train: If train is True, then return the average between cluster sum of squares of clusters based on the training data. If both train and valid are False, then train=True is assumed.
    :param valid: If valid is True, then return the average between cluster sum of squares of clusters based on the validation data. If both train and valid are True, then validation data is returned.
    :return: The average between cluster sum of squares for either the training or validation dataset.
    """
    tm = ModelBase._get_metrics(self,*ModelBase._train_or_valid(train, valid))
    return tm._metric_json["betweenss"]

  def totss(self, train=False, valid=False):
    """
    Get the total sum of squares to grand mean.

    :param train: If train is True, then return the average cluster sum of squares of clusters based on the training data. If both train and valid are False, then train=True is assumed.
    :param valid: If valid is True, then return the average cluster sum of squares of clusters based on the validation data. If both train and valid are True, then validation data is returned.
    :return: The average cluster sum of squares for either the training or validation dataset.
    """
    tm = ModelBase._get_metrics(self,*ModelBase._train_or_valid(train, valid))
    return tm._metric_json["avg_ss"]

  def tot_withinss(self, train=False, valid=False):
    """
    Get the total within cluster sum of squares.

    :param train: If train is True, then return the average within cluster sum of squares of clusters based on the training data. If both train and valid are False, then train=True is assumed.
    :param valid: If valid is True, then return the average within cluster sum of squares of clusters based on the validation data. If both train and valid are True, then validation data is returned.
    :return: The average within cluster sum of squares for either the training or validation dataset.
    """
    tm = ModelBase._get_metrics(self,*ModelBase._train_or_valid(train, valid))
    return tm._metric_json["avg_within_ss"]

  def withinss(self, train=False, valid=False):
    """
    Get the within cluster sum of squares for each cluster.

    :param train: If train is True, then return the within cluster sum of squares for each cluster based on the training data. If both train and valid are False, then train=True is assumed.
    :param valid: If valid is True, then return the within cluster sum of squares for each cluster based on the validation data. If both train and valid are True, then validation data is returned.
    :return: The within cluster sum of squares for each cluster on either the training or validation dataset.
    """
    tm = ModelBase._get_metrics(self,*ModelBase._train_or_valid(train, valid))
    return [ v[-1] for v in  tm._metric_json["centroid_stats"].cell_values]

  def centroid_stats(self,train=False,valid=False):
    """
    Get the centroid statistics for each cluster.

    :param train: If train is True, then return the centroid statistics based on the training data. If both train and valid are False, then train=True is assumed.
    :param valid: If valid is True, then return the centroid statistics based on the validation data. If both train and valid are True, then validation data is returned.
    :return: The centroid statistics on either the training or validation dataset.
    """
    tm = ModelBase._get_metrics(self,*ModelBase._train_or_valid(train, valid))
    return tm._metric_json["centroid_stats"]

  def centers(self):
    """
    :return: the centers for the kmeans model.
    """
    o = self._model_json["output"]
    cvals = o["centers"].cell_values
    centers = []
    for cidx, cval in enumerate(cvals):
      centers.append(list(cvals[cidx])[1:])
    return centers

  def centers_std(self):
    """
    :return: the standardized centers for the kmeans model.
    """
    o = self._model_json["output"]
    cvals = o["centers_std"].cell_values
    centers_std = []
    for cidx, cval in enumerate(cvals):
      centers_std.append(list(cvals[cidx])[1:])
    return centers_std
