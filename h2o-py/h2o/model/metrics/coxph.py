from __future__ import division, absolute_import, print_function, unicode_literals

import h2o
from h2o.model import MetricsBase


class H2ORegressionCoxPHModelMetrics(MetricsBase):
    """
    :examples:

    >>> from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator
    >>> heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")
    >>> coxph = H2OCoxProportionalHazardsEstimator(start_column="start",
    ...                                            stop_column="stop",
    ...                                            ties="breslow")
    >>> coxph.train(x="age", y="event", training_frame=heart)
    >>> coxph
    """
    
    def _str_items_custom(self):
        return [
            "Concordance score: {}".format(self.concordance()),
            "Concordant count: {}".format(self.concordant()),
            "Tied cout: {}".format(self.tied_y()),
        ]

    def concordance(self):
        """Concordance metrics (c-index). 
        Proportion of concordant pairs divided by the total number of possible evaluation pairs.
        1.0 for perfect match, 0.5 for random results."""
        if MetricsBase._has(self._metric_json, "concordance"):
            return self._metric_json["concordance"]
        return None

    def concordant(self):
        """Count of concordant pairs."""
        if MetricsBase._has(self._metric_json, "concordant"):
            return self._metric_json["concordant"]
        return None

    def tied_y(self):
        """Count of tied pairs."""
        if MetricsBase._has(self._metric_json, "tied_y"):
            return self._metric_json["tied_y"]
        return None

    def __init__(self, metric_json, on=None, algo=""):
        super(H2ORegressionCoxPHModelMetrics, self).__init__(metric_json, on, algo)
