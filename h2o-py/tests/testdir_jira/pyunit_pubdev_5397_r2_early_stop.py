from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.deeplearning import H2ODeepLearningEstimator

model_runtime = []  # store actual model runtime in seconds
model_maxRuntime = []   # store given maximum runtime restrictions placed on building models for different algos
algo_names =[]
actual_model_runtime = []   # in seconds
model_runtime_overrun = []  # % by which the model runtime exceeds the maximum runtime.
model_within_max_runtime = []
err_bound = 0.5              # fractor by which we allow the model runtime over-run to be

def test_r2_early_stop():
    '''
    This pyunit test is written to ensure that the r2 can be used to stop model building once it is not changing
    much.
    h2o algos.  See PUBDEV-5397.
    '''
    global model_within_max_runtime
    global err_bound
    seed = 12345

    # GBM run to check multinomials
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/multinomial_training1_set.csv"))
    y_index = training1_data.ncol-1
    x_indices = list(range(y_index))
    training1_data[y_index] = training1_data[y_index].round().asfactor()

    print("Checking early with r2 for GBM multinomial....")
    model = H2OGradientBoostingEstimator(distribution="multinomial", seed=seed)
    modelEarlyStop = H2OGradientBoostingEstimator(distribution="multinomial", seed=seed, stopping_metric="r2",
                                         stopping_tolerance=0.01, stopping_rounds=5)
    checkR2earlystop(model, modelEarlyStop, training1_data, x_indices, y_index, True)
    

    # DRF run to check binomials
    prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()
    x=list(range(1,prostate_train.ncol))
    y="CAPSULE"
    print("Checking early with r2 for RF binomial....")
    rfNoEarlyStop = H2ORandomForestEstimator(distribution="bernoulli", seed = seed)
    rf_h2o = H2ORandomForestEstimator(distribution="bernoulli", seed = seed, stopping_metric="r2", 
                                      stopping_tolerance=0.01, stopping_rounds=5)
    checkR2earlystop(rfNoEarlyStop, rf_h2o, prostate_train, x, y, True)

    # Deeplearning run to check regressions
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/gaussian_training1_set.csv"))
    y_index = training1_data.ncol-1
    x_indices = list(range(y_index))
    print("Checking early with r2 for GLM regression....")
    dlModel = H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[100, 100, 100])
    dlModelEarlyStop = H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[100, 100, 100], 
                                                stopping_metric="r2", stopping_tolerance=0.01, stopping_rounds=5)
    checkR2earlystop(dlModel, dlModelEarlyStop, training1_data, x_indices, y_index, False)
       

def checkR2earlystop(model, modelEarlyStop, training1_data, x_indices, y_index, tree_model):
    model.train(x=x_indices, y=y_index, training_frame=training1_data)
    modelEarlyStop.train(x=x_indices, y=y_index, training_frame=training1_data)
    
    if tree_model:
        numTrees = pyunit_utils.extract_from_twoDimTable(model._model_json["output"]["model_summary"],
                                                         "number_of_trees", takeFirst=True)
        numTreesEarlyStop = pyunit_utils.extract_from_twoDimTable(modelEarlyStop._model_json["output"]["model_summary"],
                                                         "number_of_trees", takeFirst=True)
        print("Number of trees built with early stopping: {0}.  Number of trees built without early stopping: {1}".format(numTreesEarlyStop[0], numTrees[0]))
        assert numTreesEarlyStop[0] <= numTrees[0], "Early stopping criteria r2 is not working."
    else:
        numIter = pyunit_utils.extract_from_twoDimTable(model._model_json["output"]["scoring_history"],
                                                        "iterations", takeFirst=False)[-1]
        numIterEarly = pyunit_utils.extract_from_twoDimTable(modelEarlyStop._model_json["output"]["scoring_history"],
                                                             "iterations", takeFirst=False)[-1]
        print("Training iterations with early stopping: {0}.  Training iterations  without early stopping: "
              "{1}".format(numIterEarly, numIter))
        assert numIter >= numIterEarly, "Early stopping criteria r2 is not working."        

if __name__ == "__main__":
    h2o.init(ip="192.168.86.243", port=54321, strict_version_check=False)
    pyunit_utils.standalone_test(test_r2_early_stop)
else:
    h2o.init(ip="192.168.86.243", port=54321, strict_version_check=False)
    test_r2_early_stop()
