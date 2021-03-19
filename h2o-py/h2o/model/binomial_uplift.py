# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

from h2o.exceptions import H2OValueError
from h2o.utils.typechecks import assert_is_type
from .model_base import ModelBase


class H2OBinomialUpliftModel(ModelBase):

    def find_idx_by_threshold(self, threshold, train=False, valid=False, xval=False):
        """
        Retrieve the index in this metric's threshold list at which the given threshold is located.

        If all are False (default), then return the training metric value.
        If more than one options is set to True, then return a dictionary of metrics where the keys are "train",
        "valid", and "xval".

        :param float threshold: Threshold value to search for in the threshold list.
        :param bool train: If True, return the find idx by threshold value for the training data.
        :param bool valid: If True, return the find idx by threshold value for the validation data.
        :param bool xval: If True, return the find idx by threshold value for each of the cross-validated splits.

        :returns: The find idx by threshold values for the specified key(s).

        :examples:

        >>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        >>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
        >>> r = cars[0].runif()
        >>> train = cars[r > .2]
        >>> valid = cars[r <=.2]
        >>> response_col = "economy_20mpg"
        >>> distribution = "bernoulli"
        >>> predictors = ["displacement", "power", "weight",
        ...               "acceleration", "year"]
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> gbm = H2OGradientBoostingEstimator(nfolds=3,
        ...                                    distribution=distribution,
        ...                                    fold_assignment="Random")
        >>> gbm.train(y=response_col,
        ...           x=predictors,
        ...           validation_frame=valid,
        ...           training_frame=train)
        >>> idx_threshold = gbm.find_idx_by_threshold(threshold=0.39438,
        ...                                           train=True)
        >>> idx_threshold
        """
        return self._delegate_to_metrics('find_idx_by_threshold', train, valid, xval, threshold=threshold)

    def gains_lift(self, train=False, valid=False, xval=False):
        """
        Get the Uplift table for the specified metrics.

        If all are False (default), then return the training metric Uplift table.
        If more than one options is set to True, then return a dictionary of metrics where t
        he keys are "train", "valid", and "xval".

        :param bool train: If True, return the uplift value for the training data.
        :param bool valid: If True, return the uplift value for the validation data.
        :param bool xval: If True, return the uplift value for each of the cross-validated splits.

        :returns: The upift values for the specified key(s).

        :examples:
        TODO
        """
        return self._delegate_to_metrics('gains_uplift', train, valid, xval)

    def _delegate_to_metrics(self, method, train=False, valid=False, xval=False, **kwargs):
        tm = ModelBase._get_metrics(self, train, valid, xval)
        m = {}
        for k, v in viewitems(tm):
            if v is None:
                m[k] = None
            elif hasattr(v, method) and callable(getattr(v, method)):
                m[k] = getattr(v, method)(**kwargs)
            else:
                raise ValueError('no method {} in {}'.format(method, type(v)))
        return list(m.values())[0] if len(m) == 1 else m
