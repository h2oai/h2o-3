#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Test the "zipped" format of the model."""
from __future__ import absolute_import, division, print_function, unicode_literals

import csv
import os
import random
import subprocess
import sys
import tempfile

import colorama
from tests import pyunit_utils
import h2o
from h2o.estimators import H2ORandomForestEstimator



def test_zipped_rf_model():
    """
    Test the correctness of the "zipped" model format.

    This test will create a random dataset, split into training/testing part, train a DRF model on it,
    download the model's data, score the model remotely and fetch the predictions, score the model locally by
    running the genmodel jar, and finally compare the prediction results.
    """
    genmodel_jar = os.path.abspath("../../../h2o-genmodel/build/libs/h2o-genmodel-all.jar")
    assert os.path.exists(genmodel_jar), "Cannot find " + genmodel_jar

    target_dir = ""
    if sys.platform == "win32":
        target_dir = tempfile.mkdtemp()
    else:
        target_dir = os.path.expanduser("~/Downloads/")

    for problem in ["regression", "binomial", "multinomial"]:
        df = random_dataset(problem)
        test = df[:100, :]
        train = df[100:, :]

        print("\n\nTraining Random Forest model...")
        model = H2ORandomForestEstimator(ntrees=100, max_depth=20)
        model.train(training_frame=train)
        print(model.summary())

        model_file = h2o.api("GET /3/Models/%s/data" % model.model_id, save_to=target_dir)
        print("\n\nSaved the model to %s" % model_file)
        assert os.path.exists(model_file)

        test_file = os.path.join(target_dir, "test_%s.csv" % test.frame_id)
        print("\nDownloading the test dataset for local use: %s" % test_file)
        h2o.download_csv(test, test_file)

        local_pred_file = os.path.join(target_dir, "predL_%s.csv" % test.frame_id)
        print("\nScoring the model locally and saving to file %s..." % local_pred_file)
        ret = subprocess.call(["java", "-cp", genmodel_jar, "hex.genmodel.tools.PredictCsv", "--input", test_file,
                               "--output", local_pred_file, "--model", model_file, "--decimal"])
        assert ret == 0, "GenModel finished with return code %d" % ret

        h2o_pred_file = os.path.join(target_dir, "predR_%s.csv" % test.frame_id)
        print("\nScoring the model remotely and downloading to file %s..." % h2o_pred_file)
        predictions = model.predict(test)
        h2o.download_csv(predictions, h2o_pred_file)

        print("\nCheck whether the predictions coincide...")
        local_pred = load_csv(local_pred_file)
        server_pred = load_csv(h2o_pred_file)
        assert len(local_pred) == len(server_pred) == test.nrow, \
            "Number of rows in prediction files do not match: %d vs %d vs %d" % \
            (len(local_pred), len(server_pred), test.nrow)
        for i in range(test.nrow):
            lpred = local_pred[i]
            rpred = server_pred[i]
            assert type(lpred) == type(rpred), "Types of predictions do not match: %r / %r" % (lpred, rpred)
            if isinstance(lpred, float):
                same = abs(lpred - rpred) < 1e-8
            else:
                same = lpred == rpred
            assert same, "Predictions are different for row %d: local = %r, remote = %r" % (i + 1, lpred, rpred)
        print(colorama.Fore.LIGHTGREEN_EX + "\nPredictions match!\n" + colorama.Fore.RESET)


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


def load_csv(csvfile):
    """Load the csv file and return its first column as a single array."""
    assert os.path.exists(csvfile), "File %s does not exist" % csvfile
    output = []
    with open(csvfile, "rt") as f:
        reader = csv.reader(f)
        for rownum, row in enumerate(reader):
            if rownum == 0: continue
            try:
                value = float(row[0])
            except ValueError:
                value = row[0]
            output.append(value)
    return output



if __name__ == "__main__":
    colorama.init()
    pyunit_utils.standalone_test(test_zipped_rf_model)
else:
    test_zipped_rf_model()
