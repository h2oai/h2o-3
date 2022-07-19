from __future__ import division, absolute_import, print_function, unicode_literals

import h2o
from h2o.model import MetricsBase


class H2OOrdinalModelMetrics(MetricsBase):

    def _str_items_custom(self):
        return [
            self.confusion_matrix(),
            self.hit_ratio_table()
        ]

    def confusion_matrix(self):
        """Returns a confusion matrix based of H2O's default prediction threshold for a dataset.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
        >>> response_col = "cylinders"
        >>> distribution = "multinomial"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution = distribution)
        >>> gbm.train(x=predictors,
        ...           y = response,
        ...           training_frame = train,
        ...           validation_frame = valid)
        >>> gbm.confusion_matrix(train)
        """
        # FIXME: why doesn't it return a ConfusionMatrix instance, like in H2OBinomialModelMetrics?
        return self._metric_json['cm']['table']

    def hit_ratio_table(self):
        """Retrieve the Hit Ratios.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["cylinders"] = cars["cylinders"].asfactor()
        >>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
        >>> response_col = "cylinders"
        >>> distribution = "multinomial"
        >>> predictors = ["displacement","power","weight","acceleration","year"]
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution = distribution)
        >>> gbm.train(x=predictors,
        ...           y = response,
        ...           training_frame = train,
        ...           validation_frame = valid)
        >>> gbm.hit_ratio_table()
        """
        return self._metric_json['hit_ratio_table']
