# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

import h2o
from .model_base import ModelBase


class H2OAutoEncoderModel(ModelBase):

    def anomaly(self, test_data, per_feature=False):
        """
        Obtain the reconstruction error for the input test_data.

        :param H2OFrame test_data: The dataset upon which the reconstruction error is computed.
        :param bool per_feature: Whether to return the square reconstruction error per feature.
            Otherwise, return the mean square error.

        :returns: the reconstruction error.
        """
        if test_data is None or test_data.nrow == 0: raise ValueError("Must specify test data")
        j = h2o.api("POST /3/Predictions/models/%s/frames/%s" % (self.model_id, test_data.frame_id),
                    data={"reconstruction_error": True, "reconstruction_error_per_feature": per_feature})
        return h2o.get_frame(j["model_metrics"][0]["predictions"]["frame_id"]["name"])
