import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators import H2OGradientBoostingEstimator, H2OXGBoostEstimator


def test_model(model, x, target, train_frame, train_frame_clean, test_frame_missing):
    print("Testing: ", model.algo)
    model.train(x=x, y=target, training_frame=train_frame)

    # Scenario where user train model and ignore some cols but using the same frame for H statistic
    first, second, third = model.h(train_frame, ['DPROS', 'DCAPS']), model.h(train_frame, ['DPROS', 'GLEASON']), model.h(train_frame, ['DCAPS', 'GLEASON'])

    print("H stats identical frame", first, second, third)

    # Here we call with frame that contain only the cols used for training
    first_clean, second_clean, third_clean = model.h(train_frame_clean, ['DPROS', 'DCAPS']), model.h(train_frame_clean, ['DPROS', 'GLEASON']), model.h(train_frame_clean, ['DCAPS', 'GLEASON'])

    print("H stats clean frame", first_clean, second_clean, third_clean)

    # Both H statistics must me the same, user provide everything and we pick only what we need
    assert_equals(first_clean, first, "H stats should be the same for both datasets")
    assert_equals(second_clean, second, "H stats should be the same for both datasets")
    assert_equals(third_clean, third, "H stats should be the same for both datasets")

    try:
        # Scenario where user request H stats but forgot to provide one col (DPROS)
        model.h(test_frame_missing, ['DCAPS', 'GLEASON'])
    except Exception as e:
        assert "DPROS is missing" in str(e)

    print("Test OK")


def h_stats_same_data_but_missing_or_additional_columns():
    x = ['DPROS', 'DCAPS', 'GLEASON']
    target = "CAPSULE"
    params = {"ntrees": 10, "learn_rate": 0.1, "max_depth": 2, "min_rows": 1, "seed": 1234}

    train_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    train_frame[target] = train_frame[target].asfactor()

    train_frame_clean = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"), skipped_columns=[1, 2, 5, 6])
    train_frame_clean[target] = train_frame_clean[target].asfactor()

    train_frame_missing = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"), skipped_columns=[1, 2, 3, 5, 6]) # skip DPROS
    train_frame_missing[target] = train_frame_missing[target].asfactor()

    gbm_h2o = H2OGradientBoostingEstimator(**params)
    xgb_h2o = H2OXGBoostEstimator(**params)

    test_model(gbm_h2o, x, target, train_frame, train_frame_clean, train_frame_missing)
    test_model(xgb_h2o, x, target, train_frame, train_frame_clean, train_frame_missing)


if __name__ == "__main__":
    pyunit_utils.standalone_test(h_stats_same_data_but_missing_or_additional_columns)
else:
    h_stats_same_data_but_missing_or_additional_columns()
