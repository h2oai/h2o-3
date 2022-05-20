# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

import h2o
from h2o.model import ModelBase


class H2OAutoEncoderModel(ModelBase):

    def anomaly(self, test_data, per_feature=False):
        """
        Obtain the reconstruction error for the input ``test_data``.

        :param H2OFrame test_data: The dataset upon which the reconstruction error is computed.
        :param bool per_feature: Whether to return the square reconstruction error per feature.
            Otherwise, return the mean square error.

        :returns: the reconstruction error.

        :examples:

        >>> from h2o.estimators.deeplearning import H2OAutoEncoderEstimator
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")
        >>> test = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")
        >>> predictors = list(range(0,784))
        >>> resp = 784
        >>> train = train[predictors]
        >>> test = test[predictors]
        >>> ae_model = H2OAutoEncoderEstimator(activation="Tanh",
        ...                                    hidden=[2],
        ...                                    l1=1e-5,
        ...                                    ignore_const_cols=False,
        ...                                    epochs=1)
        >>> ae_model.train(x=predictors,training_frame=train)
        >>> test_rec_error = ae_model.anomaly(test)
        >>> test_rec_error
        >>> test_rec_error_features = ae_model.anomaly(test, per_feature=True)
        >>> test_rec_error_features
        """
        if test_data is None or test_data.nrow == 0: raise ValueError("Must specify test data")
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                    data={"reconstruction_error": True, "reconstruction_error_per_feature": per_feature})
        return h2o.get_frame(j["model_metrics"][0]["predictions"]["frame_id"]["name"])
