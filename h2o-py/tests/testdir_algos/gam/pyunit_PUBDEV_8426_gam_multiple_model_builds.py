from __future__ import division
from __future__ import print_function
import sys, os
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
import pandas as pd
import numpy as np

# Running GAM multiple times with the same training frame.  This test should just run to completion and should
# not encounter any errors.  No need to have assert at the end of this test
def link_functions_tweedie_vpow():
    np.random.seed(25)

    data = {"predictor": np.random.uniform(400, 800, 15),
        "target": np.random.uniform(0.7, 1.4, 15),
        "weight_1": [1] * 15,
        "weight_2": [3] * 15,}

    df = h2o.H2OFrame(pd.DataFrame(data))

    model_w1 = H2OGeneralizedAdditiveEstimator(family='gaussian',gam_columns=["predictor"],scale=[1],bs=[0],weights_column='weight_1')
    model_w2 = H2OGeneralizedAdditiveEstimator(family='gaussian',gam_columns=["predictor"],scale=[1],bs=[0],weights_column='weight_2')
    model = H2OGeneralizedAdditiveEstimator(family='gaussian',gam_columns=["predictor"],scale=[1],bs=[0])

    model_w1.train(x=["predictor"], y="target", training_frame=df)
    model_w2.train(x=["predictor"], y="target", training_frame=df)
    model.train(x=["predictor"], y="target", training_frame=df)

if __name__ == "__main__":
    pyunit_utils.standalone_test(link_functions_tweedie_vpow)
else:
    link_functions_tweedie_vpow()
