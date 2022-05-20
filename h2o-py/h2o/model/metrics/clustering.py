from __future__ import division, absolute_import, print_function, unicode_literals

import h2o
from h2o.model import MetricsBase


class H2OClusteringModelMetrics(MetricsBase):

    def _str_items_custom(self):
        cs = self._metric_json['centroid_stats']
        return [
            "Total Within Cluster Sum of Square Error: {}".format(self.tot_withinss()),
            "Total Sum of Square Error to Grand Mean: {}".format(self.totss()),
            "Between Cluster Sum of Square Error: {}".format(self.betweenss()),
            "Centroid stats are not available." if cs is None else cs, 
        ]

    def tot_withinss(self):
        """The Total Within Cluster Sum-of-Square Error, or None if not present.

        :examples:

        >>> from h2o.estimators.kmeans import H2OKMeansEstimator
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> km = H2OKMeansEstimator(k=3, nfolds=3)
        >>> km.train(x=list(range(4)), training_frame=iris)
        >>> km.tot_withinss()
        """
        if MetricsBase._has(self._metric_json, "tot_withinss"):
            return self._metric_json["tot_withinss"]
        return None

    def totss(self):
        """The Total Sum-of-Square Error to Grand Mean, or None if not present.

        :examples:

        >>> from h2o.estimators.kmeans import H2OKMeansEstimator
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> km = H2OKMeansEstimator(k=3, nfolds=3)
        >>> km.train(x=list(range(4)), training_frame=iris)
        >>> km.totss()
        """
        if MetricsBase._has(self._metric_json, "totss"):
            return self._metric_json["totss"]
        return None

    def betweenss(self):
        """The Between Cluster Sum-of-Square Error, or None if not present.

        :examples:

        >>> from h2o.estimators.kmeans import H2OKMeansEstimator
        >>> iris = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
        >>> km = H2OKMeansEstimator(k=3, nfolds=3)
        >>> km.train(x=list(range(4)), training_frame=iris)
        >>> km.betweenss()
        """
        if MetricsBase._has(self._metric_json, "betweenss"):
            return self._metric_json["betweenss"]
        return None
