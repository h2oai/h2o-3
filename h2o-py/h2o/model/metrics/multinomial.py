from __future__ import division, absolute_import, print_function, unicode_literals

import h2o
from h2o.model import MetricsBase


class H2OMultinomialModelMetrics(MetricsBase):

    def _str_items_custom(self):
        return [
            self.multinomial_auc_table(),
            self.multinomial_aucpr_table(),
            self.confusion_matrix(),
            self.hit_ratio_table()
        ]

    def confusion_matrix(self):
        """Returns a confusion matrix based on H2O's default prediction threshold for a dataset.

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

    def multinomial_auc_table(self):
        """Retrieve the multinomial AUC values.

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
        >>> gbm.multinomial_auc_table()
        """
        if self._metric_json['multinomial_auc_table'] is not None:
            return self._metric_json['multinomial_auc_table']
        else:
            return "AUC table was not computed: " \
                   "it is either disabled (model parameter 'auc_type' was set to AUTO or NONE) " \
                   "or the domain size exceeds the limit (maximum is 50 domains)."

    def multinomial_aucpr_table(self):
        """Retrieve the multinomial PR AUC values.

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
        >>> gbm.multinomial_aucpr_table()
        """
        if self._metric_json['multinomial_aucpr_table'] is not None:
            return self._metric_json['multinomial_aucpr_table']
        else:
            return "AUCPR table was not computed: " \
                   "it is either disabled (model parameter 'auc_type' was set to AUTO or NONE) " \
                   "or the domain size exceeds the limit (maximum is 50 domains)."
