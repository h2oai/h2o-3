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
from h2o.transforms.decomposition import H2OPCA
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
    y_index = training1_data.ncol-1
    x_indices = list(range(y_index))
    model = H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[10, 10, 10])
    grabRuntimeInfo(err_bound, 2.0, model, training1_data, x_indices, y_index)
    cleanUp([training1_data, model])

    # stack ensemble, stacking part is not iterative
    print("******************** Skip testing stack ensemble.  Not an iterative algo.")

    # GBM run
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/multinomial_training1_set.csv"))
    y_index = training1_data.ncol-1
    x_indices = list(range(y_index))
    training1_data[y_index] = training1_data[y_index].round().asfactor()
    model = H2OGradientBoostingEstimator(distribution="multinomial", seed=seed)
    grabRuntimeInfo(err_bound, 2.0, model, training1_data, x_indices, y_index)
    cleanUp([model])

    # GLM run
    model = H2OGeneralizedLinearEstimator(family='multinomial', seed=seed)
    grabRuntimeInfo(err_bound, 2.0, model, training1_data, x_indices, y_index)
    cleanUp([model])

    # naivebayes, not iterative
    print("******************** Skip testing Naives Bayes.  Not an iterative algo.")

    # random foreset
    model = H2ORandomForestEstimator(ntrees=100, score_tree_interval=0)
    grabRuntimeInfo(err_bound, 2.0, model, training1_data, x_indices)
    cleanUp([model, training1_data])

    # deepwater
    if H2ODeepWaterEstimator.available():
        training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
        training1_data = training1_data.drop('Site')
        training1_data['Angaus'] = training1_data['Angaus'].asfactor()
        y_index = "Angaus"
        x_indices = list(range(1, training1_data.ncol))
        model = H2ODeepWaterEstimator(epochs=50, hidden=[4096, 4096, 4096], hidden_dropout_ratios=[0.2, 0.2, 0.2])
        grabRuntimeInfo(err_bound, 2.0, model, training1_data, x_indices, y_index)
        cleanUp([training1_data, model])

    # GLRM, do not make sense to stop in the middle of an iteration
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/glrmdata1000x25.csv"))
    x_indices = list(range(training1_data.ncol))
    model = H2OGeneralizedLowRankEstimator(k=10, loss="Quadratic", gamma_x=0.3,
                                           gamma_y=0.3, transform="STANDARDIZE")
    grabRuntimeInfo(err_bound, 2.0, model, training1_data, x_indices)
    cleanUp([training1_data, model])

    # PCA
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/pca1000by25.csv"))
    x_indices = list(range(training1_data.ncol))
    model = H2OPCA(k=10, transform="STANDARDIZE", pca_method="Power", compute_metrics=True)
    grabRuntimeInfo(err_bound*3, 1.2, model, training1_data, x_indices)
    cleanUp([training1_data, model])

    # kmeans
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/kmeans_8_centers_3_coords.csv"))
    x_indices = list(range(training1_data.ncol))
    model = H2OKMeansEstimator(k=10)
    grabRuntimeInfo(err_bound*2, 2.0, model, training1_data, x_indices)
    cleanUp([training1_data, model])

    # word2vec
    train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/text8.gz"), header=1, col_types=["string"])
    used = train[0:170000, 0]
    w2v_model = H2OWord2vecEstimator()
    grabRuntimeInfo(err_bound, 2.0, w2v_model, used, [], 0)
    cleanUp([train, used, w2v_model])

    if sum(model_within_max_runtime)>0:
        sys.exit(1)


def grabRuntimeInfo(err_bound, reduction_factor, model, training_data, x_indices, y_index=0):
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

    unsupervised = ("glrm" in model.algo) or ("pca" in model.algo) or ("kmeans" in model.algo)
    if unsupervised:
        model.train(x=x_indices, training_frame=training_data)
    else:
        if ('word2vec' in model.algo):
            model.train(training_frame=training_data)
        else:
            model.train(x=x_indices, y=y_index, training_frame=training_data)
    algo_names.append(model.algo)
    model_iteration = checkIteration(model)
    model_runtime.append(model._model_json["output"]["run_time"]/1000.0)
    model_maxRuntime.append(model_runtime[-1]/reduction_factor)
    if unsupervised:
        model.train(x=x_indices, training_frame=training_data, max_runtime_secs=model_maxRuntime[-1])
    else:
        if ('word2vec' in model.algo):
            model.train(training_frame=training_data, max_runtime_secs=model_maxRuntime[-1])
        else:
            model.train(x=x_indices, y=y_index, training_frame=training_data, max_runtime_secs=model_maxRuntime[-1])


    actual_model_runtime.append(model._model_json["output"]["run_time"]/1000.0)
    # capture model runtime with
    model_runtime_overrun.append((actual_model_runtime[-1]-model_maxRuntime[-1])*1.0/model_maxRuntime[-1])

    print("Model: {0}, \nMax_runtime_sec: {1}, \nActual_model_runtime_sec: {2}, "
          "\nRun time overrun: {3}".format(algo_names[-1], model_maxRuntime[-1],
                                               actual_model_runtime[-1], model_runtime_overrun[-1]))
    print("Number of epochs/iterations/trees without max_runtime_sec restriction: {0}"
          "\nNumber of epochs/iterations/trees with max_runtime_sec "
          "restriction: {1}".format(model_iteration, checkIteration(model)))
    iteration_change = model_iteration - checkIteration(model)   # pass test as long as iteration number has dropped
    if (model_runtime_overrun[-1] <= err_bound) or (iteration_change>0):
        print("********** Test passed!.")
        model_within_max_runtime.append(0)
    else:
        print("********** Test failed.  Model training time exceeds max_runtime_sec by more than {0}.".format(err_bound))
        model_within_max_runtime.append(1)


def checkIteration(model):
    if model._model_json["output"]["scoring_history"] != None:
        epochList=pyunit_utils.extract_scoring_history_field(model, "epochs")
        if (epochList==None):   # return the scoring history length as number of iteration estimate
            return len(model._model_json["output"]["scoring_history"].cell_values)
        return epochList[-1]
    elif "epochs" in model._model_json["output"]:
        return model._model_json["output"]["epochs"]
    else:
        return 0

def cleanUp(eleList):
    for ele in eleList:
        h2o.remove(ele)


if __name__ == "__main__":
    pyunit_utils.standalone_test(algo_max_runtime_secs)
else:
    algo_max_runtime_secs()