#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Test the "zipped" format of the model."""
from __future__ import absolute_import, division, print_function, unicode_literals

import os
import random

from tests import pyunit_utils
import h2o



def test_zipped_rf_model():
    """
    Test the correctness of the "zipped" model format.

    This test will create a random dataset, split into training/testing part, train a DRF model on it,
    download the model's data, score the model remotely and fetch the predictions, score the model locally by
    running the genmodel jar, and finally compare the prediction results.
    """
    for problem in ["regression", "binomial", "multinomial"]:
        df = random_dataset(problem)
        test = df[:2000, :]
        train = df[2000:, :]

        print("\n\nTraining Randome Forest model...")
        rf = h2o.estimators.H2ORandomForestEstimator(ntrees=100, max_depth=20)
        rf.train(training_frame=train)
        print(rf.summary())

        target_file = os.path.expanduser("~/Downloads/")
        target_file = h2o.api("GET /3/Models/%s/data" % rf.model_id, save_to=target_file)
        print("\n\nSaved the model to %s" % target_file)
        assert os.path.exists(target_file)


def random_dataset(response_type, verbose=True):
    """Create and return a random dataset."""
    if verbose: print("\nCreating a dataset for a %s problem:" % response_type)
    fractions = {k + "_fraction": random.random() for k in "real categorical integer time string binary".split()}
    fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] /= 3
    fractions["time_fraction"] /= 2
    sum_fractions = sum(fractions.values())
    for k in fractions:
        fractions[k] /= sum_fractions
    response_factors = (1 if response_type == "regression" else
                        2 if response_type == "binomial" else
                        random.randint(10, 60))
    df = h2o.create_frame(rows=random.randint(5000, 15000), cols=random.randint(10, 20),
                          missing_fraction=random.uniform(0, 0.05),
                          has_response=True, response_factors=response_factors, positive_response=True,
                          **fractions)
    if verbose:
        print()
        df.show()
    return df


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_zipped_rf_model)
else:
    test_zipped_rf_model()
