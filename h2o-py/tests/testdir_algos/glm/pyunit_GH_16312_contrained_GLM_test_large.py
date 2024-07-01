import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
from tests import pyunit_utils
from tests.pyunit_utils import utils_for_glm_tests
import numpy as np
import pandas as pd
from h2o.grid.grid_search import H2OGridSearch

def data_prep(seed):
    np.random.seed(seed)
    x1 = np.random.normal(0, 10, 100000)
    x2 = np.random.normal(10, 100 , 100000)
    x3 = np.random.normal(20, 200, 100000)
    x4 = np.random.normal(30, 3000, 100000)
    x5 = np.random.normal(400, 4000, 100000)

    y_raw = np.sin(x1)*100 + np.sin(x2)*100 + x3/20 + x3/30 + x5/400
    y = np.random.normal(y_raw, 20)

    data = {
        'x1': x1,
        'x2': x2,
        'x3': x3,
        'x4': x4,
        'x5': x5,
        'y': y,
    }
    return h2o.H2OFrame(pd.DataFrame(data))

def test_bad_linear_constraints():
    train_data = data_prep(123)
    family = 'gaussian'
    link = 'identity'
    nfolds = 0
    lambda_ = 0
    seed = 1234
    calc_like = True
    compute_p_values = True
    solver = 'irlsm'
    predictors = ['x1', 'x2', 'x3', 'x4', 'x5']
    response = "y"

    linear_constraints2 = []

    name = "x2"
    values = 1
    types = "Equal"
    contraint_numbers = 0
    linear_constraints2.append([name, values, types, contraint_numbers])
    
    name = "x3"
    values = 1
    types = "Equal"
    contraint_numbers = 0
    linear_constraints2.append([name, values, types, contraint_numbers])
    
    name = "constant"
    values = 0
    types = "Equal"
    contraint_numbers = 0
    linear_constraints2.append([name, values, types, contraint_numbers])
    
    
    linear_constraints = h2o.H2OFrame(linear_constraints2)
    linear_constraints.set_names(["names", "values", "types", "constraint_numbers"])
    
    params3 = {
        "family" : family,
        "link": link,
        "lambda_" : lambda_,
        "seed" : seed,
        "nfolds" : nfolds,
        "compute_p_values" : compute_p_values,
        "calc_like" : calc_like,
        "solver" : solver,
        "linear_constraints": linear_constraints,
        "standardize": True,
        #"startval": list(np.random.rand(6))
    }
    
    # add grid search
    # set hyperparameter search
    constraint_eta0 = [0.05, 0.1, 0.1258925, 0.5, 1.1]
    constraint_tau = [1.5, 5, 10, 20, 50, 60] # increase penalty
    constraint_alpha = [0.1, 0.5, 0.9]
    constraint_beta = [0.1, 0.5, 0.9]
    constraint_c0 = [1.5, 2, 5, 10, 15, 20] # initial value, controls precision of line search
    
    # best hyperparameters
    # constraint_eta0 = [0.05, 0.1, 0.1258925]
    # constraint_tau = [1.5, 5, 10, 20, 50, 60] # increase penalty
    # constraint_alpha = [0.5]
    # constraint_beta = [0.1]
    # constraint_c0 = [1.5] # initial value, controls precision of line search
    
    hyper_parameters = {"constraint_eta0":constraint_eta0, "constraint_tau":constraint_tau, "constraint_alpha":constraint_alpha,
                       "constraint_beta":constraint_beta, "constraint_c0":constraint_c0}
    # glmGrid = H2OGridSearch(glm(**params3), hyper_params=hyper_parameters)
    # glmGrid.train(x=predictors, y=response, training_frame=train_data)
    # sortedGrid = glmGrid.get_grid()
    # print(sortedGrid)
    # best_model = h2o.get_model(sortedGrid.model_ids[0])
    # print(best_model.coef())



    glm3 = glm(**params3)

    model3 = glm3.train(x = predictors, y = response, training_frame = train_data)
    print(model3.coef())

    params2 = {
        "family" : family,
        "link": link,
        "lambda_" : lambda_,
        "seed" : seed,
        "nfolds" : nfolds,
        "compute_p_values" : compute_p_values,
        "calc_like" : calc_like,
        "solver" : solver
    }
    glm2 = glm(**params2)

    model2 = glm2.train(x = predictors, y = response, training_frame = train_data)
    print(model2.coef())
    # check x2 + X3 <= 0

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_bad_linear_constraints)
else:
    test_bad_linear_constraints()
