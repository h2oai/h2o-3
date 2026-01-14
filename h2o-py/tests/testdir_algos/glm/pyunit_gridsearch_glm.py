import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch
import pandas as pd
import numpy as np


def glm_lambda_in_gridsearch():
    # Generate sample data
    np.random.seed(1)
    X = np.random.rand(10) * 10
    Y = 2 * X + np.random.normal(0, 1, 10)
    data = pd.DataFrame({
        'Predictor': X,
        'Response': Y
    })
    # Convert data to h2oframe
    h2o_data = h2o.H2OFrame(data)
    # Fit a GLM with a fixed lambda_ parameter. This works
    predictors = ['Predictor']
    response = "Response"

    # Grid search with lambda_ parameter. This errors
    glm = H2OGeneralizedLinearEstimator()
    hyper_params = {'lambda_': [0.001, 0.0001]}

    grid = H2OGridSearch(model=glm, hyper_params=hyper_params,
                         search_criteria={'strategy': "Cartesian"})
    grid.train(x=predictors, y=response, training_frame=h2o_data)

    assert len(grid.model_ids) == 2


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_lambda_in_gridsearch)
else:
    glm_lambda_in_gridsearch()
