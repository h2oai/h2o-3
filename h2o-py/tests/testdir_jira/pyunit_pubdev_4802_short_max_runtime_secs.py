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

model_within_max_runtime = []
max_runtime_secs_small=1e-63    # small max_runtime_secs to make sure model did not get to run.

def algo_max_runtime_secs():
    '''
    This pyunit test is written to ensure that the various model will not crash if the max_runtime_secs
    is set to be too short.  See PUBDEV-4802.
    '''
    global model_within_max_runtime
    seed = 12345

    # word2vec
    train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/text8.gz"), header=1, col_types=["string"])
    used = train[0:170000, 0]
    w2v_model = H2OWord2vecEstimator()
    grabRuntimeInfo(w2v_model, used, [], 0)
    cleanUp([train, used, w2v_model])

    # kmeans
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/kmeans_8_centers_3_coords.csv"))
    x_indices = list(range(training1_data.ncol))
    model = H2OKMeansEstimator(k=10)
    grabRuntimeInfo(model, training1_data, x_indices)
    cleanUp([training1_data, model])

    # PCA, pca_method=Power
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/pca1000by25.csv"))
    x_indices = list(range(training1_data.ncol))
    model = H2OPCA(k=10, transform="STANDARDIZE", pca_method="Power", compute_metrics=True)
    grabRuntimeInfo(model, training1_data, x_indices)
    cleanUp([model])

    # PCA, pca_method=Randomized
    model = H2OPCA(k=10, transform="STANDARDIZE", pca_method="Randomized", compute_metrics=True)
    grabRuntimeInfo(model, training1_data, x_indices)
    cleanUp([model])

    # PCA, pca_method=GLRM
    model = H2OPCA(k=10, transform="STANDARDIZE", pca_method="GLRM", compute_metrics=True, use_all_factor_levels=True)
    grabRuntimeInfo(model, training1_data, x_indices)
    cleanUp([model])

    # deeplearning
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/gaussian_training1_set.csv"))
    y_index = training1_data.ncol-1
    x_indices = list(range(y_index))
    model = H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[10, 10, 10])
    grabRuntimeInfo(model, training1_data, x_indices, y_index)
    cleanUp([training1_data, model])

    # stack ensemble, stacking part is not iterative
    print("******************** Skip testing stack ensemble.  Not an iterative algo.")

    # GBM run
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/multinomial_training1_set.csv"))
    y_index = training1_data.ncol-1
    x_indices = list(range(y_index))
    training1_data[y_index] = training1_data[y_index].round().asfactor()
    model = H2OGradientBoostingEstimator(distribution="multinomial", seed=seed)
    grabRuntimeInfo(model, training1_data, x_indices, y_index)
    cleanUp([model])

    # GLM run
    model = H2OGeneralizedLinearEstimator(family='multinomial', seed=seed)
    grabRuntimeInfo(model, training1_data, x_indices, y_index)
    cleanUp([model])

    # naivebayes, not iterative
    print("******************** Skip testing Naives Bayes.  Not an iterative algo.")

    # random foreset
    model = H2ORandomForestEstimator(ntrees=100, score_tree_interval=0)
    grabRuntimeInfo(model, training1_data, x_indices)
    cleanUp([model, training1_data])

    # deepwater
    if H2ODeepWaterEstimator.available():
        training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
        training1_data = training1_data.drop('Site')
        training1_data['Angaus'] = training1_data['Angaus'].asfactor()
        y_index = "Angaus"
        x_indices = list(range(1, training1_data.ncol))
        model = H2ODeepWaterEstimator(epochs=50, hidden=[4096, 4096, 4096], hidden_dropout_ratios=[0.2, 0.2, 0.2])
        grabRuntimeInfo(model, training1_data, x_indices, y_index)
        cleanUp([training1_data, model])

    # GLRM, do not make sense to stop in the middle of an iteration
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/glrmdata1000x25.csv"))
    x_indices = list(range(training1_data.ncol))
    model = H2OGeneralizedLowRankEstimator(k=10, loss="Quadratic", gamma_x=0.3, gamma_y=0.3, transform="STANDARDIZE",
                                           recover_svd=True)
    grabRuntimeInfo(model, training1_data, x_indices)
    cleanUp([training1_data, model])

    if sum(model_within_max_runtime)>0:
        sys.exit(1)


def grabRuntimeInfo(model, training_data, x_indices, y_index=0):
    '''
    This function will train the passed model with the max_runtime_secs set to be too short.  Want to make sure
    a warning message is received.

    :param model: model to be evaluated
    :param training_data: H2OFrame containing training dataset
    :param x_indices: prediction input indices to model.train
    :param y_index: response index to model.train
    :return: None
    '''

    global max_runtime_secs_small
    unsupervised = ("glrm" in model.algo) or ("pca" in model.algo) or ("kmeans" in model.algo)

    if unsupervised:
        model.train(x=x_indices, training_frame=training_data, max_runtime_secs=max_runtime_secs_small)
        model.model_performance(training_data).show()
    else:
        if ('word2vec' in model.algo):
            model.train(training_frame=training_data, max_runtime_secs=max_runtime_secs_small)
        else:
            model.train(x=x_indices, y=y_index, training_frame=training_data, max_runtime_secs=max_runtime_secs_small)
            model.model_performance(training_data).show()

    print("Model: {0}, \nActual_model_runtime_sec: {1}, "
          "\nNumber of epochs/iterations/trees : {2}".format(model.algo,
                                                             model._model_json["output"]["run_time"]/1000.0,
                                                             checkIteration(model)))
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