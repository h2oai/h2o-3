from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator
from h2o.estimators.kmeans import H2OKMeansEstimator
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator as H2OPCA
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.word2vec import H2OWord2vecEstimator
from h2o.estimators.deepwater import H2ODeepWaterEstimator

model_runtime = []  # store actual model runtime in seconds
model_maxRuntime = []   # store given maximum runtime restrictions placed on building models for different algos
algo_names =[]
actual_model_runtime = []   # in seconds
model_runtime_overrun = []  # % by which the model runtime exceeds the maximum runtime.
model_within_max_runtime = []
err_bound = 0.5              # fractor by which we allow the model runtime over-run to be

def algo_max_runtime_secs():
    '''
    This pyunit test is written to ensure that the max_runtime_secs can restrict the model training time for all
    h2o algos.  See PUBDEV-4702.
    '''
    global model_within_max_runtime
    global err_bound
    seed = 12345

    # deeplearning
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/gaussian_training1_set.csv"))
    validation_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/gaussian_training2_set.csv"))
    y_index = training1_data.ncol-1
    x_indices = list(range(y_index))
    model = H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[10, 10, 10], stopping_method="valid",
                                     stopping_rounds=3, stopping_tolerance=0.01, nfolds=3)
    grabRuntimeInfo(err_bound, 2.0, model, training1_data, x_indices, y_index, validation_data)



def grabRuntimeInfo(err_bound, reduction_factor, model, training_data, x_indices, y_index, validation_data):
    '''
    This function will train the passed model, extract the model runtime.  Next it train the model again
    with the max_runtime_secs set to the original model runtime divide by half.  At the end of both runs, it
    will perform several tasks:
    1. it will extract the new model runtime, calculate the runtime overrun factor as
        (new model runtime - max_runtime_secs)/max_runtime_secs.
    2. for iterative algorithms, it will calculate number of iterations/epochs dropped between the two models;
    3. determine if the timing test passes/fails based on the runtime overrun factor and the iterations/epochs drop.
        - test passed if runtime overrun factor < err_bound or if iterations/epochs drop > 0
    4. it will print out all the related information regarding the timing test.

    :param err_bound: runtime overrun factor used to determine if test passed/failed
    :param reduction_factor: how much run time to cut in order to set the max_runtime_secs
    :param model: model to be evaluated
    :param training_data: H2OFrame containing training dataset
    :param x_indices: prediction input indices to model.train
    :param y_index: response index to model.train
    :return: None
    '''
    global model_runtime
    global model_maxRuntime
    global algo_names
    global actual_model_runtime
    global model_runtime_overrun
    global model_within_max_runtime


    model.train(x=x_indices, y=y_index, training_frame=training_data, validation_frame=validation_data)


    actual_model_runtime.append(model._model_json["output"]["run_time"]/1000.0)
    # capture model runtime with




if __name__ == "__main__":
    pyunit_utils.standalone_test(algo_max_runtime_secs)
else:
    algo_max_runtime_secs()