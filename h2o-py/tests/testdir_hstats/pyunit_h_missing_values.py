import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator,  H2OXGBoostEstimator

def h_stats_on_synthetic_data_with_missing_values():
    df, x, target = pyunit_utils.prepare_data()
    print(df.head())

    # Create the data frame
    train_frame = h2o.H2OFrame(df[x + [target]])
    train_frame[target] = train_frame[target].asfactor()

    # Train GBM model
    gbm_h2o = H2OGradientBoostingEstimator(ntrees=10, learn_rate=0.1, max_depth=2, min_rows=1, seed=1234)
    gbm_h2o.train(x=x, y=target, training_frame=train_frame)

    # Calculate H stats with column includes missing values
    gbm_h = gbm_h2o.h(train_frame, ['x1', 'x3'])
    print(gbm_h)

    # Train XGBoost model
    xgb_h2o = H2OXGBoostEstimator(ntrees=100, learn_rate=0.1, max_depth=2, min_rows=1, seed=1234)
    xgb_h2o.train(x=x, y=target, training_frame=train_frame)

    xgb_h = xgb_h2o.h(train_frame, ['x1', 'x3'])
    print(xgb_h)


if __name__ == "__main__":
    pyunit_utils.standalone_test(h_stats_on_synthetic_data_with_missing_values)
else:
    h_stats_on_synthetic_data_with_missing_values()
