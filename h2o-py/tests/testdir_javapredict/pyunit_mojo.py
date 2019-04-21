#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
Test the "MOJO" format of the model.

This really is an integration test, not a unit test.
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import csv
import os
import random
import shutil
import subprocess
import sys; sys.path.insert(1, os.path.join("..", ".."))
import tempfile
import time

import colorama
import tabulate
from tests import pyunit_utils
import h2o
from h2o.estimators import H2ORandomForestEstimator, H2OGradientBoostingEstimator, H2ODeepWaterEstimator


# These variables can be tweaked to increase / reduce stress on the test. However when submitting to GitHub
# please keep these reasonably low, so that the test wouldn't take exorbitant amounts of time.
NTREES = 50
DEPTH = 5
NTESTROWS = 1000

# Deep Water
EPOCHS = 1


def test_mojo_model(target_dir):
    """
    Test the correctness of the "MOJO" model format.

    This test will create a random dataset, split into training/testing part, train a DRF model on it,
    download the model's MOJO, score the model remotely and fetch the predictions, score the model locally by
    running the genmodel jar, and finally compare the prediction results.
    """
    genmodel_jar = os.path.abspath("../../../h2o-assemblies/genmodel/build/libs/genmodel.jar")
    assert os.path.exists(genmodel_jar), "Cannot find " + genmodel_jar

    report = []
    for estimator, estimator_name in [(H2ODeepWaterEstimator, "DeepWater"),
                                      (H2ORandomForestEstimator, "DRF"),
                                      (H2OGradientBoostingEstimator, "GBM")]:
        if (estimator == H2ODeepWaterEstimator and not H2ODeepWaterEstimator.available()): continue
        print(colorama.Fore.LIGHTYELLOW_EX + "\n#================================================")
        print("#  Estimator: " + estimator.__name__)
        print("#================================================\n" + colorama.Fore.RESET)

        for problem in ["binomial", "multinomial", "regression"]:
            print("========================")
            print("%s problem" % problem.capitalize())
            print("========================")
            if estimator == H2ODeepWaterEstimator and problem == "regression":
                print("Skipping %s" % problem.capitalize)
                continue
            df = random_dataset(problem, verbose=False)
            print("Created dataset with %d rows x %d columns" % (df.nrow, df.ncol))
            train = df[NTESTROWS:, :]
            test0 = df[0, :]
            test1 = df[:NTESTROWS, :]
            test2 = test1.rbind(test1)

            time0 = time.time()
            print("\n\nTraining %s model..." % estimator.__name__)
            if estimator == H2ODeepWaterEstimator:
                model = estimator(epochs=EPOCHS)  # , categorical_encoding="enum")
            else:
                model = estimator(ntrees=NTREES, max_depth=DEPTH)
            model.train(training_frame=train)
            print(model.summary())
            print("    Time taken = %.3fs" % (time.time() - time0))

            print("\nDownloading MOJO...")
            time0 = time.time()
            mojo_file = model.download_mojo(target_dir)
            print("    => %s  (%d bytes)" % (mojo_file, os.stat(mojo_file).st_size))
            assert os.path.exists(mojo_file)
            print("    Time taken = %.3fs" % (time.time() - time0))

            if estimator != H2ODeepWaterEstimator:
                print("\nDownloading POJO...")
                time0 = time.time()
                pojo_file = model.download_pojo(target_dir)
                pojo_size = os.stat(pojo_file).st_size
                pojo_name = os.path.splitext(os.path.basename(pojo_file))[0]
                print("    => %s  (%d bytes)" % (pojo_file, pojo_size))
                print("    Time taken = %.3fs" % (time.time() - time0))

            print("\nDownloading the test datasets for local use: ")
            time0 = time.time()
            test0_file = os.path.join(target_dir, "test0_%s.csv" % test0.frame_id)
            test1_file = os.path.join(target_dir, "test1_%s.csv" % test1.frame_id)
            test2_file = os.path.join(target_dir, "test2_%s.csv" % test2.frame_id)
            print("    => " + test0_file)
            print("    => " + test1_file)
            print("    => " + test2_file)
            h2o.download_csv(test0, test0_file)
            h2o.download_csv(test1, test1_file)
            h2o.download_csv(test2, test2_file)
            print("    Time taken = %.3fs" % (time.time() - time0))

            print("\nScoring the model remotely and downloading to files...")
            times = []
            h2o_pred_file0 = os.path.join(target_dir, "predR_%s.csv" % test0.frame_id)
            h2o_pred_file1 = os.path.join(target_dir, "predR_%s.csv" % test1.frame_id)
            h2o_pred_file2 = os.path.join(target_dir, "predR_%s.csv" % test2.frame_id)
            for testframe, outfile in [(test0, h2o_pred_file0), (test1, h2o_pred_file1), (test2, h2o_pred_file2)]:
                predictions = model.predict(testframe)
                h2o.download_csv(predictions, outfile)
                print("    => " + outfile)
                times.append(time.time())
            print("    Time taken = %.3fs   (1st run: %.3f, 2nd run: %.3f)" %
                  (times[2] + times[0] - 2 * times[1], times[1] - times[0], times[2] - times[1]))
            report.append((estimator_name, problem, "Server", times[1] - times[0], times[2] - times[1]))

            print("\nScoring the model locally and saving to files... ")
            times = []
            local_pred_file0 = os.path.join(target_dir, "predL_%s.csv" % test0.frame_id)
            local_pred_file1 = os.path.join(target_dir, "predL_%s.csv" % test1.frame_id)
            local_pred_file2 = os.path.join(target_dir, "predL_%s.csv" % test2.frame_id)
            for inpfile, outfile in [(test0_file, local_pred_file0), (test1_file, local_pred_file1),
                                     (test2_file, local_pred_file2)]:
                load_csv(inpfile)
                java_cmd = ["java", "-cp", genmodel_jar,
                            "-ea", "-Xmx12g", "-XX:ReservedCodeCacheSize=256m",
                            "hex.genmodel.tools.PredictCsv",
                            "--input", inpfile, "--output", outfile, "--mojo", mojo_file, "--decimal"]
                print("    %r" % java_cmd)
                ret = subprocess.call(java_cmd)
                assert ret == 0, "GenModel finished with return code %d" % ret
                print("    => " + local_pred_file1)
                times.append(time.time())
            print("    Time taken = %.3fs   (1st run: %.3f, 2nd run: %.3f)" %
                  (times[2] + times[0] - 2 * times[1], times[1] - times[0], times[2] - times[1]))
            report.append((estimator_name, problem, "Mojo", times[1] - times[0], times[2] - times[1]))

            if estimator != H2ODeepWaterEstimator and pojo_size <= 1000 << 20:  # 1000 Mb
                time0 = time.time()
                print("\nCompiling Java Pojo")
                javac_cmd = ["javac", "-cp", genmodel_jar, "-J-Xmx12g", pojo_file]
                subprocess.check_call(javac_cmd)
                print("    Time taken = %.3fs" % (time.time() - time0))

                pojo_pred_file0 = os.path.join(target_dir, "predP_%s.csv" % test0.frame_id)
                pojo_pred_file1 = os.path.join(target_dir, "predP_%s.csv" % test1.frame_id)
                pojo_pred_file2 = os.path.join(target_dir, "predP_%s.csv" % test2.frame_id)
                print("\nScoring POJO and saving to file...")
                times = []
                cp_sep = ";" if sys.platform == "win32" else ":"
                for inpfile, outfile in [(test0_file, pojo_pred_file0), (test1_file, pojo_pred_file1),
                                         (test2_file, pojo_pred_file2)]:
                    load_csv(inpfile)
                    java_cmd = ["java", "-cp", cp_sep.join([genmodel_jar, target_dir]),
                                "-ea", "-Xmx12g", "-XX:ReservedCodeCacheSize=256m", "-XX:MaxPermSize=256m",
                                "hex.genmodel.tools.PredictCsv",
                                "--pojo", pojo_name, "--input", inpfile, "--output", outfile, "--decimal"]
                    print("    %r" % java_cmd)
                    ret = subprocess.call(java_cmd)
                    assert ret == 0, "GenModel finished with return code %d" % ret
                    times.append(time.time())
                print("    Time taken = %.3fs   (1st run: %.3f, 2nd run: %.3f)" %
                      (times[2] + times[0] - 2 * times[1], times[1] - times[0], times[2] - times[1]))
                report.append((estimator_name, problem, "POJO", times[1] - times[0], times[2] - times[1]))
            else:
                pojo_pred_file1 = None


            print("\nChecking whether the predictions coincide...")
            time0 = time.time()
            local_pred = load_csv(local_pred_file1)
            server_pred = load_csv(h2o_pred_file1)
            pojo_pred = load_csv(pojo_pred_file1) if pojo_pred_file1 else local_pred
            assert len(local_pred) == len(server_pred) == len(pojo_pred) == test1.nrow, \
                "Number of rows in prediction files do not match: %d vs %d vs %d vs %d" % \
                (len(local_pred), len(server_pred), len(pojo_pred), test1.nrow)

            for i in range(test1.nrow):
                lpred = local_pred[i]
                rpred = server_pred[i]
                ppred = pojo_pred[i]
                assert type(lpred) == type(rpred) == type(ppred), \
                    "Types of predictions do not match: %r / %r / %r" % (lpred, rpred, ppred)
                if isinstance(lpred, float):
                    same = abs(lpred - rpred) + abs(lpred - ppred) <= 1e-8 * (abs(lpred) + abs(rpred) + abs(ppred))
                else:
                    same = lpred == rpred == ppred
                assert same, \
                    "Predictions are different for row %d: mojo=%r, pojo=%r, server=%r" % (i + 1, lpred, ppred, rpred)
            print("    Time taken = %.3fs" % (time.time() - time0))
            print(colorama.Fore.LIGHTGREEN_EX + "\nPredictions match!\n" + colorama.Fore.RESET)

    print(colorama.Fore.LIGHTYELLOW_EX + "\n\n#================================================")
    print("#  Timing report")
    print("#================================================\n" + colorama.Fore.RESET)
    print(tabulate.tabulate(report,
          headers=["Model", "Problem type", "Scorer", "%d rows" % NTESTROWS, "%d rows" % (2 * NTESTROWS)],
          floatfmt=".3f"), end="\n\n\n")

def random_dataset(response_type, verbose=True):
    """Create and return a random dataset."""
    if verbose: print("\nCreating a dataset for a %s problem:" % response_type)
    fractions = {k + "_fraction": random.random() for k in "real categorical integer time string binary".split()}
    fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] /= 3
    fractions["time_fraction"] /= 2
    #fractions["categorical_fraction"] = 0
    sum_fractions = sum(fractions.values())
    for k in fractions:
        fractions[k] /= sum_fractions
    response_factors = (1 if response_type == "regression" else
                        2 if response_type == "binomial" else
                        random.randint(10, 30))
    df = h2o.create_frame(rows=random.randint(15000, 25000) + NTESTROWS, cols=random.randint(20, 100),
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


try:
    target_dir = tempfile.mkdtemp()
    if __name__ == "__main__":
        colorama.init()
        pyunit_utils.standalone_test(lambda: test_mojo_model(target_dir))
    else:
        test_mojo_model(target_dir)
finally:
    shutil.rmtree(target_dir)
