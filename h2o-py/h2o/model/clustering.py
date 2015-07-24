"""
Clustering Models
"""

from metrics_base import *


class H2OClusteringModel(ModelBase):

  def __init__(self, dest_key, model_json):
    super(H2OClusteringModel, self).__init__(dest_key, model_json,H2OClusteringModelMetrics)

  def size(self, train=False, valid=False, xval=False):
    """
    Get the sizes of each cluster.

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param train: If train is True, then return the between cluster sum of squares value for the training data.
    :param valid: If valid is True, then return the between cluster sum of squares value for the validation data.
    :param xval:  If xval is True, then return the between cluster sum of squares value for the cross validation data.
    :return: The between cluster sum of squares for this clustering model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else [ v[2] for v in  v._metric_json["centroid_stats"].cell_values]
    return m.values()[0] if len(m) == 1 else m

  def num_iterations(self):
    """
    Get the number of iterations that it took to converge or reach max iterations.

    :return: number of iterations (integer)
    """
    o = self._model_json["output"]
    return o["model_summary"].cell_values[0][o["model_summary"].col_header.index('number_of_iterations')]

  def betweenss(self, train=False, valid=False, xval=False):
    """
    Get the between cluster sum of squares.

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param train: If train is True, then return the between cluster sum of squares value for the training data.
    :param valid: If valid is True, then return the between cluster sum of squares value for the validation data.
    :param xval:  If xval is True, then return the between cluster sum of squares value for the cross validation data.
    :return: The between cluster sum of squares for this clustering model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v._metric_json["betweenss"]
    return m.values()[0] if len(m) == 1 else m

  def totss(self, train=False, valid=False, xval=False):
    """
    Get the total sum of squares to grand mean.

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param train: If train is True, then return the total sum of squares to grand mean value for the training data.
    :param valid: If valid is True, then return the total sum of squares to grand mean value for the validation data.
    :param xval:  If xval is True, then return the total sum of squares to grand mean value for the cross validation data.
    :return: The total sum of squares to grand mean for this clustering model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v._metric_json["avg_ss"]
    return m.values()[0] if len(m) == 1 else m

  def tot_withinss(self, train=False, valid=False, xval=False):
    """
    Get the total within cluster sum of squares.

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param train: If train is True, then return the total within cluster sum of squares value for the training data.
    :param valid: If valid is True, then return the total within cluster sum of squares value for the validation data.
    :param xval:  If xval is True, then return the total within cluster sum of squares value for the cross validation data.
    :return: The total within cluster sum of squares for this clustering model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v._metric_json["avg_within_ss"]
    return m.values()[0] if len(m) == 1 else m

  def withinss(self, train=False, valid=False, xval=False):
    """
    Get the within cluster sum of squares for each cluster.

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param train: If train is True, then return the within cluster sum of squares value for the training data.
    :param valid: If valid is True, then return the within cluster sum of squares value for the validation data.
    :param xval:  If xval is True, then return the within cluster sum of squares value for the cross validation data.
    :return: The within cluster sum of squares for this clustering model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else [ z[-1] for z in  v._metric_json["centroid_stats"].cell_values]
    return m.values()[0] if len(m) == 1 else m

  def centroid_stats(self, train=False, valid=False, xval=False):
    """
    Get the centroid statistics for each cluster.

    If all are False (default), then return the training metric value.
    If more than one options is set to True, then return a dictionary of metrics where the keys are "train", "valid",
    and "xval"

    :param train: If train is True, then return the centroid statistics for the training data.
    :param valid: If valid is True, then return the centroid statistics for the validation data.
    :param xval:  If xval is True, then return the centroid statistics for the cross validation data.
    :return: The centroid statistics for this clustering model.
    """
    tm = ModelBase._get_metrics(self, train, valid, xval)
    m = {}
    for k,v in zip(tm.keys(),tm.values()): m[k] = None if v is None else v._metric_json["centroid_stats"]
    return m.values()[0] if len(m) == 1 else m

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
