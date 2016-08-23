#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Test the "zipped" format of the model."""
from __future__ import division, print_function, unicode_literals

import os

import h2o
from tests import pyunit_utils



def test_zipped_rf_model():
    """Test."""
    df = h2o.import_file(pyunit_utils.locate("h2o/h2o_data/prostate.csv"))
    df["AGE"] = df["AGE"].asfactor()
    x = list(set(df.names) - {"GLEASON"})
    print(x)

    rf = h2o.estimators.H2ORandomForestEstimator(ntrees=100, max_depth=20)
    rf.train(training_frame=df, x=x, y="GLEASON")
    print(rf)

    target_file = os.path.expanduser("~/Downloads/out.zip")
    h2o.api("GET /3/Models/%s/data" % rf.model_id, save_to=target_file)
    print("Saved to %s" % target_file)
    assert os.path.exists(target_file)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_zipped_rf_model)
else:
    test_zipped_rf_model()
