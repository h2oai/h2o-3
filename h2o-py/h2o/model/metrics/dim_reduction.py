from __future__ import division, absolute_import, print_function, unicode_literals

from h2o.model import MetricsBase


class H2ODimReductionModelMetrics(MetricsBase):

    def _str_items_custom(self):
        return [
            "Sum of Squared Error (Numeric): {}".format(self.num_err()),
            "Misclassification Error (Categorical): {}".format(self.cat_err()),
        ]

    def num_err(self):
        """Sum of Squared Error over non-missing numeric entries, or None if not present."""
        if MetricsBase._has(self._metric_json, "numerr"):
            return self._metric_json["numerr"]
        return None

    def cat_err(self):
        """The Number of Misclassified categories over non-missing categorical entries, or None if not present."""
        if MetricsBase._has(self._metric_json, "caterr"):
            return self._metric_json["caterr"]
        return None
