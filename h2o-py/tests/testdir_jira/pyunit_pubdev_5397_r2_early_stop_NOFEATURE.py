import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

model_runtime = []  # store actual model runtime in seconds
model_maxRuntime = []   # store given maximum runtime restrictions placed on building models for different algos
algo_names =[]
actual_model_runtime = []   # in seconds
model_runtime_overrun = []  # % by which the model runtime exceeds the maximum runtime.
model_within_max_runtime = []
err_bound = 0.5              # fractor by which we allow the model runtime over-run to be

def test_r2_early_stop():
    '''
    This pyunit test is written to ensure that the max_runtime_secs can restrict the model training time for all
    h2o algos.  See https://github.com/h2oai/h2o-3/issues/11581.
    '''
    global model_within_max_runtime
    global err_bound
    seed = 12345

    # GBM run
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/multinomial_training1_set.csv"))
    y_index = training1_data.ncol-1
    x_indices = list(range(y_index))
    training1_data[y_index] = training1_data[y_index].round().asfactor()

    modelNoEarlyStop = H2OGradientBoostingEstimator(distribution="multinomial", seed=seed)
    modelNoEarlyStop.train(x=x_indices, y=y_index, training_frame=training1_data)
    numTrees = pyunit_utils.extract_from_twoDimTable(modelNoEarlyStop._model_json["output"]["model_summary"],
                                                     "number_of_trees", takeFirst=True)

    model = H2OGradientBoostingEstimator(distribution="multinomial", seed=seed, stopping_metric="r2",
                                         stopping_tolerance=0.01, stopping_rounds=5)
    model.train(x=x_indices, y=y_index, training_frame=training1_data)
    numTreesEarlyStop = pyunit_utils.extract_from_twoDimTable(model._model_json["output"]["model_summary"],
                                                              "number_of_trees", takeFirst=True)
    print("Number of tress built with early stopping: {0}.  Number of trees built without early stopping: {1}".format(numTreesEarlyStop[0], numTrees[0]))
    assert numTreesEarlyStop[0] <= numTrees[0], "Early stopping criteria r2 is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_r2_early_stop)
else:
    test_r2_early_stop()
