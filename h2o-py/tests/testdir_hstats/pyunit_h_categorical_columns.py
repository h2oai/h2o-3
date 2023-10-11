import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.exceptions import H2OServerError

def h_stats_on_synthetic_data_with_categorical_variables():
    df, x, target = pyunit_utils.prepare_data()
    print(df.head())

    # Create the data frame
    train_frame = h2o.H2OFrame(df[x + [target]])
    train_frame['x4'] = train_frame['x4'].asfactor()
    train_frame['x5'] = train_frame['x5'].asfactor()
    train_frame[target] = train_frame[target].asfactor()

    # Train model
    gbm_h2o = H2OGradientBoostingEstimator(ntrees=10, learn_rate=0.1, max_depth=2, min_rows=1, seed=1234)
    gbm_h2o.train(x=x, y=target, training_frame=train_frame)

    # Calculate H stats with categorical columns
    try:
        gbm_h2o.h(train_frame, ['x4', 'x5'])
    except H2OServerError as e:
        print("Categorical features are not supported.")
        print(e)


if __name__ == "__main__":
    pyunit_utils.standalone_test(h_stats_on_synthetic_data_with_categorical_variables)
else:
    h_stats_on_synthetic_data_with_categorical_variables()
