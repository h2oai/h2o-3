import sys
import os
import csv
sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.xgboost import H2OXGBoostEstimator


# this is more of a demo of how to use mojo_predict_csv to test prediction contributions concurrently
# the dataset is likely too small to reliably catch problems (it might reveal something once in a while)
def demo_xgboost_concurrent_contributions():
    prostate_path = pyunit_utils.locate("smalldata/logreg/prostate.csv")

    prostate = h2o.import_file(path=prostate_path)
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()

    xgb_model = H2OXGBoostEstimator()
    xgb_model.train(x=["AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"], y="CAPSULE", training_frame=prostate)

    results_dir = os.path.join(pyunit_utils.locate("results"), "xgb_concurrent")
    os.mkdir(results_dir)
    mojo_path = xgb_model.download_mojo(results_dir, get_genmodel_jar=True)

    # how many parallel threads to run
    concurrency = 4

    reference_result = h2o.mojo_predict_csv(input_csv_path=prostate_path, mojo_zip_path=mojo_path,
                                            output_csv_path=os.path.join(results_dir, "predictions.csv"),
                                            predict_contributions=True,
                                            extra_cmd_args=["--testConcurrent", str(concurrency)])
    print(reference_result)

    for test_id in range(4):
        with open(os.path.join(results_dir, "predictions.csv." + str(test_id))) as csv_file:
            concurrent_result = list(csv.DictReader(csv_file))
            assert reference_result == concurrent_result


if __name__ == "__main__":
    pyunit_utils.standalone_test(demo_xgboost_concurrent_contributions)
else:
    demo_xgboost_concurrent_contributions()
