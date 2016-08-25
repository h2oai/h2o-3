#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Test the "zipped" format of the model."""
from __future__ import division, print_function, unicode_literals

import os
import random

from tests import pyunit_utils
import h2o



def test_zipped_rf_model():
    """Test."""
    problem = random.choice(["regression", "binomial", "multinomial"])
    problem = "regression"
    fractions = {k + "_fraction": random.random() for k in "real categorical integer time string binary".split()}
    sum_fractions = sum(fractions.values())
    for k in fractions:
        fractions[k] /= sum_fractions
    response_factors = (1 if problem == "regression" else
                        2 if problem == "binomial" else
                        random.randint(10, 60))
    df = h2o.create_frame(rows=random.randint(5000, 15000), cols=random.randint(10, 20),
                          missing_fraction=random.uniform(0, 0.05),
                          has_response=True, response_factors=response_factors, positive_response=True,
                          **fractions)
    df.show()


    # rf = h2o.estimators.H2ORandomForestEstimator(ntrees=100, max_depth=20)
    # rf.train(training_frame=df, x=x, y="GLEASON")
    # print(rf)

    # target_file = os.path.expanduser("~/Downloads/")
    # target_file = h2o.api("GET /3/Models/%s/data" % rf.model_id, save_to=target_file)
    # print("Saved to %s" % target_file)
    # assert os.path.exists(target_file)





if __name__ == "__main__":
    pyunit_utils.standalone_test(test_zipped_rf_model)
else:
    test_zipped_rf_model()
