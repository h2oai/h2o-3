from __future__ import division, absolute_import, print_function, unicode_literals

import h2o
from h2o.model import MetricsBase


class H2OAnomalyDetectionModelMetrics(MetricsBase):
    
    def _str_items_custom(self):
        return [
            "Anomaly Score: {}".format(self.mean_score()),
            "Normalized Anomaly Score: {}".format(self.mean_normalized_score()),
        ]

    def mean_score(self):
        """
        Mean Anomaly Score. For Isolation Forest represents the average of all tree-path lengths.

        :examples:

        >>> from h2o.estimators.isolation_forest import H2OIsolationForestEstimator
        >>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_train.csv")
        >>> test = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_test.csv")
        >>> isofor_model = H2OIsolationForestEstimator(sample_size=5, ntrees=7)
        >>> isofor_model.train(training_frame = train)
        >>> perf = isofor_model.model_performance()
        >>> perf.mean_score()
        """
        if MetricsBase._has(self._metric_json, "mean_score"):
            return self._metric_json["mean_score"]
        return None

    def mean_normalized_score(self):
        """
        Mean Normalized Anomaly Score. For Isolation Forest - normalized average path length.

        :examples:

        >>> from h2o.estimators.isolation_forest import H2OIsolationForestEstimator
        >>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_train.csv")
        >>> test = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_test.csv")
        >>> isofor_model = H2OIsolationForestEstimator(sample_size=5, ntrees=7)
        >>> isofor_model.train(training_frame = train)
        >>> perf = isofor_model.model_performance()
        >>> perf.mean_normalized_score()

        """
        if MetricsBase._has(self._metric_json, "mean_normalized_score"):
            return self._metric_json["mean_normalized_score"]
        return None
    
    
