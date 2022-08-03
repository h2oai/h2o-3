from builtins import range
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators import H2OGradientBoostingEstimator, H2OXGBoostEstimator


def test_model(model, train_frame, train_frame_clean, target):
    print("Testing: ", model.algo)
    model.train(y=target, training_frame=train_frame, ignored_columns=["AGE", "RACE", "PSA", "VOL"])

    first, second, third = model.h(train_frame, ['DPROS', 'DCAPS']), model.h(train_frame, ['DPROS', 'GLEASON']), model.h(train_frame, ['DCAPS', 'GLEASON'])

    first_clean, second_clean, third_clean = model.h(train_frame_clean, ['DPROS', 'DCAPS']), model.h(train_frame_clean, ['DPROS', 'GLEASON']), model.h(train_frame_clean, ['DCAPS', 'GLEASON'])

    assert_equals(first_clean, first, "H stats should be the same for both datasets")
    assert_equals(second_clean, second, "H stats should be the same for both datasets")
    assert_equals(third_clean, third, "H stats should be the same for both datasets")

    # TODO test call model.h with dataset with missing columns or totally different columns

    print("Test OK")


def h_stats_same_data_but_missing_or_additional_columns():
    target = "CAPSULE"
    train_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    train_frame[target] = train_frame[target].asfactor()

    train_frame_clean = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"), skipped_columns=[1, 2, 5, 6])
    train_frame_clean[target] = train_frame_clean[target].asfactor()

    gbm_h2o = H2OGradientBoostingEstimator(ntrees=100, learn_rate=0.1, max_depth=2, min_rows=1, seed=1234)
    xgb_h2o = H2OXGBoostEstimator(ntrees=100, learn_rate=0.1, max_depth=2, min_rows=1, seed=1234)

    test_model(gbm_h2o, train_frame, train_frame_clean, target)
    test_model(xgb_h2o, train_frame, train_frame_clean, target)


if __name__ == "__main__":
    pyunit_utils.standalone_test(h_stats_same_data_but_missing_or_additional_columns)
else:
    h_stats_same_data_but_missing_or_additional_columns()
