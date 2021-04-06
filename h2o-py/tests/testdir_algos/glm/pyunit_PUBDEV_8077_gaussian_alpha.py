import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# test scoring_history for Gaussian family with validation dataset and cv
def test_gaussian_alpha():
    col_list_compare = ["iterations", "objective", "negative_log_likelihood", "training_rmse", "validation_rmse",
                        "training_mae", "validation_mae", "training_deviance", "validation_deviance"]

    h2o_data = h2o.import_file(
        path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname]
    myY = "C21"
    myX = h2o_data.names.remove(myY)
    data_frames = h2o_data.split_frame(ratios=[0.8])
    training_data = data_frames[0]
    test_data = data_frames[1]

    # test with lambda search on, generate_scoring_history on and off
    model1 = glm(family="gaussian", lambda_search=True, alpha=[0,0.2,0.5,0.8,1], generate_scoring_history=True)
    model1.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    model2 = glm(family="gaussian", lambda_search=True, alpha=[0,0.2,0.5,0.8,1], generate_scoring_history=False)
    model2.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    pyunit_utils.assertCoefDictEqual(model1.coef(), model2.coef())

    # test with lambda search off, generate_scoring_history on and off
    model1 = glm(family="gaussian", lambda_search=False, alpha=[0,0.2,0.5,0.8,1], generate_scoring_history=True, 
                 Lambda=[0,0.1,0.001,0.004])
    model1.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    model2 = glm(family="gaussian", lambda_search=False, alpha=[0,0.2,0.5,0.8,1], generate_scoring_history=False,
                 Lambda=[0,0.1,0.001,0.004])
    model2.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    pyunit_utils.assertCoefDictEqual(model1.coef(), model2.coef())

    # test with lambda search on, generate_scoring_history on and off, cv on
    model1 = glm(family="gaussian", lambda_search=True, alpha=[0,0.2,0.5,0.8,1], generate_scoring_history=True, 
                 nfolds=2, seed=12345)
    model1.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    model2 = glm(family="gaussian", lambda_search=True, alpha=[0,0.2,0.5,0.8,1], generate_scoring_history=False,
                 nfolds=2, seed=12345)
    model2.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    pyunit_utils.assertCoefDictEqual(model1.coef(), model2.coef())

    # test with lambda search off, generate_scoring_history on and off, cv on
    model1 = glm(family="gaussian", lambda_search=False, alpha=[0,0.2,0.5,0.8,1], generate_scoring_history=True,
                 Lambda=[0,0.1,0.001,0.004], nfolds=2, seed=12345)
    model1.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    model2 = glm(family="gaussian", lambda_search=False, alpha=[0,0.2,0.5,0.8,1], generate_scoring_history=False,
                 Lambda=[0,0.1,0.001,0.004], nfolds=2, seed=12345)
    model2.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    pyunit_utils.assertCoefDictEqual(model1.coef(), model2.coef())

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gaussian_alpha)
else:
    test_gaussian_alpha()
