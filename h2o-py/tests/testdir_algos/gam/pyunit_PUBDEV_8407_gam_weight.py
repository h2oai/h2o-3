from __future__ import division
from __future__ import print_function
import sys, os
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
import pandas as pd
import numpy as np

# When GAM is trained with weight columns, scoring on the training frame run into error.
# I fixed this by not tracking weight columns in Java backend.
def link_functions_tweedie_vpow():
    np.random.seed(1234)
    n_rows = 10

    data = {
        "X1": np.random.randn(n_rows),
        "X2": np.random.randn(n_rows),
        "X3": np.random.randn(n_rows),
        "W": np.random.choice([10, 20], size=n_rows),
        "Y": np.random.choice([0, 0, 0, 0, 0, 10, 20, 30], size=n_rows)
    }

    train = h2o.H2OFrame(pd.DataFrame(data))
    test = train.drop("W")
    print(train)
    h2o_model = H2OGeneralizedAdditiveEstimator(family="tweedie",
                                                gam_columns=["X3"],
                                                weights_column="W",
                                                lambda_=0,
                                                tweedie_variance_power=1.5,
                                                tweedie_link_power=0)
    h2o_model.train(x=["X1", "X2"], y="Y", training_frame=train)

    predict_w = h2o_model.predict(train)
    predict = h2o_model.predict(test) # scoring without weight column
    # should produce same frame
    pyunit_utils.compare_frames_local(predict_w, predict, prob=1, tol=1e-6)


if __name__ == "__main__":
    pyunit_utils.standalone_test(link_functions_tweedie_vpow)
else:
    link_functions_tweedie_vpow()
