#!/usr/bin/env python
"""
This is a helper script to generate many "Frosted" models and store them on disk.

This is not a pyunit test, and is designed to run only when invoked directly.
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import os
import random
import time

import h2o
from h2o.estimators import H2ORandomForestEstimator, H2OGradientBoostingEstimator


def generate_models(n_models, n_rows, n_cols, n_rows_per_model, n_trees, max_depth, target_dir):
    target_dir = os.path.abspath(target_dir)
    if not os.path.exists(target_dir):
        os.makedirs(target_dir)
    assert n_rows_per_model <= n_rows, "Not enough rows to train any model"
    assert n_rows <= n_rows_per_model * n_models, "Too many rows"
    assert os.path.isdir(target_dir), "%s is not a directory" % target_dir

    genmodel_jar = os.path.abspath("../../../h2o-genmodel/build/libs/h2o-genmodel-all.jar")
    assert os.path.exists(genmodel_jar), "Cannot find " + genmodel_jar

    # Step 1: generate the dataset.
    df = h2o.create_frame(rows=n_rows, cols=n_cols, missing_fraction=0, integer_fraction=1,
                          has_response=True, response_factors=1, positive_response=True)
    assert df.names == ["response"] + ["C%d" % n for n in range(1, n_cols + 1)]
    assert df.types["response"] == "real"
    assert all(v == "int" for k, v in df.types.items() if k != "response")
    print("Dataset created (%d x %d).\n" % (df.nrow, df.ncol))

    # Step 2: train and save the models
    for i in range(n_models):
        estimator = random.choice([H2ORandomForestEstimator, H2OGradientBoostingEstimator])
        start_row = random.randint(0, n_rows - n_rows_per_model)
        end_row = start_row + n_rows_per_model

        # Step 2.a: train a model on a random subset of the frame `df`
        time0 = time.time()
        print("%-4d  %-30s" % (i + 1, estimator.__name__), end="")
        model = estimator(ntrees=n_trees, max_depth=max_depth)
        model.train(training_frame=df[start_row:end_row, :])
        print(" %.3fs" % (time.time() - time0), end="")

        # Step 2.b: save the model to a file
        model_file = h2o.api("GET /3/Models/%s/data" % model.model_id, save_to=target_dir)
        assert os.path.exists(model_file)
        simple_file = model_file[len(target_dir) + 1:] if model_file.startswith(target_dir + "/") else model_file
        print(" => %s  (%d bytes)" % (simple_file, os.stat(model_file).st_size))

        # Step 2.c
        h2o.remove(model)


if __name__ == "__main__":
    h2o.init()
    h2o.no_progress()
    generate_models(n_models=5000, n_rows=1000000, n_cols=30, n_rows_per_model=10000, n_trees=300, max_depth=5,
                    target_dir="../results/models")
