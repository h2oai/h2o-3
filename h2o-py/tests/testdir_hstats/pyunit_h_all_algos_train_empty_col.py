import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator, H2OXGBoostEstimator
import numpy as np


def test_model_empty_col(model, target, train_frame_empty_col):
    model.train(y=target, training_frame=train_frame_empty_col)

    try:
        h_stat = model.h(train_frame_empty_col, ['DCAPS', 'GLEASON'])
        print("h_stat", h_stat)
        assert h_stat is not None
    except:
        assert False, "Should work"

    print("Test ok")


def h_stats_data_with_empty_col():
    target = "CAPSULE"
    params = {"ntrees": 10, "learn_rate": 0.1, "max_depth": 2, "min_rows": 1, "seed": 1234}

    train_frame_empty_col = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    train_frame_empty_col["DPROS"] = np.nan
    train_frame_empty_col[target] = train_frame_empty_col[target].asfactor()

    gbm_h2o = H2OGradientBoostingEstimator(**params)
    xgb_h2o = H2OXGBoostEstimator(**params)

    test_model_empty_col(gbm_h2o, target, train_frame_empty_col)
    test_model_empty_col(xgb_h2o, target, train_frame_empty_col)


if __name__ == "__main__":
    pyunit_utils.standalone_test(h_stats_data_with_empty_col)
else:
    h_stats_data_with_empty_col()
