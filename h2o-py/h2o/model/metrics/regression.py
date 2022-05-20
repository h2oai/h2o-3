from __future__ import division, absolute_import, print_function, unicode_literals

from h2o.model import MetricsBase


class H2ORegressionModelMetrics(MetricsBase):
    """
    This class provides an API for inspecting the metrics returned by a regression model.

    It is possible to retrieve the :math:`R^2` (1 - MSE/variance) and MSE.

    :examples:

    >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
    >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
    >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    >>> predictors = ["displacement","power","weight","acceleration","year"]
    >>> response = "cylinders"
    >>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
    >>> cars_glm = H2OGeneralizedLinearEstimator()
    >>> cars_glm.train(x = predictors,
    ...                y = response,
    ...                training_frame = train,
    ...                validation_frame = valid)
    >>> cars_glm.mse()
    """

    # empty although all regression-specific metrics should go here...  
    pass

