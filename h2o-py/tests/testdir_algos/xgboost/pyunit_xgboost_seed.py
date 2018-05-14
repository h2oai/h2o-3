from h2o.estimators.xgboost import *
from tests import pyunit_utils


def seed():
    assert H2OXGBoostEstimator.available()

    prostate_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))

    x = ["ID", "AGE", "RACE", "GLEASON", "DCAPS", "PSA", "VOL", "CAPSULE"]
    y = 'DPROS'

    prostate_frame.split_frame(ratios=[0.75], destination_frames=['prostate_training', 'prostate_validation'], seed=1)

    training_frame = h2o.get_frame('prostate_training')
    test_frame = h2o.get_frame('prostate_validation')

    ntrees = 2

    # Three iterations are enough to compare seed's effect, as the greatest difference occurs early in model training phase
    # Build model with seed 1
    model_seed_1 = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=0.3,
                                       booster='gbtree', seed=1, ntrees=ntrees, distribution='gamma')
    model_seed_1.train(x=x, y=y, training_frame=training_frame)

    # Build model with seed 42
    model_seed_42 = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=0.3,
                                        booster='gbtree', seed=42, ntrees=ntrees, distribution='gamma')
    model_seed_42.train(x=x, y=y, training_frame=training_frame)

    # Test model properties are not equal
    model1_history_rmse = model_seed_1.score_history()['training_rmse'].tolist()
    model1_history_mae = model_seed_1.score_history()['training_mae'].tolist()
    model1_history_deviance = model_seed_1.score_history()['training_deviance'].tolist()

    model42_history_rmse = model_seed_42.score_history()['training_rmse'].tolist()
    model42_history_mae = model_seed_42.score_history()['training_mae'].tolist()
    model42_history_deviance = model_seed_42.score_history()['training_deviance'].tolist()

    assert model1_history_rmse != model42_history_rmse
    assert model1_history_mae != model42_history_mae
    assert model1_history_deviance != model42_history_deviance

if __name__ == "__main__":
    pyunit_utils.standalone_test(seed)
else:
    seed()
