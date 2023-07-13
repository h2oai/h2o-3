import os
import sys

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu, dataset_prostate, CustomMaeFunc


def test_automl_custom_metric():
    def custom_mae_mm():
        return h2o.upload_custom_metric(CustomMaeFunc, func_name="mae", func_file="mm_mae.py")

    ftrain, fvalid, _ = dataset_prostate()
    ftrain = ftrain.rbind(fvalid)
    ftrain = h2o.H2OFrame(ftrain.as_data_frame(), "my_training_frame")
    aml = H2OAutoML(max_models=20, custom_metric_func=custom_mae_mm(), sort_metric="custom")
    aml.train(y="AGE", training_frame=ftrain)

    for sd in ["train", "valid", "xval", "AUTO"]:
        print(sd + "\n" + ("=" * len(sd)))
        ldb = h2o.make_leaderboard(aml, scoring_data="xval").as_data_frame()
        print(f"MAE==Custom: {((ldb.mae == ldb.custom) | ldb.custom.isna()).all()}")
        print(ldb)
        assert ((ldb.mae == ldb.custom) | ldb.custom.isna()).all() and (~ldb.custom.isna()).any()


pu.run_tests([
    test_automl_custom_metric,
])
