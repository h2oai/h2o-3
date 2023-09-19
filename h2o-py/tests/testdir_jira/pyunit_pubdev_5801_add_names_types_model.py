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
from h2o.estimators.naive_bayes import H2ONaiveBayesEstimator

def algo_max_runtime_secs():
    '''
    This pyunit test is written to ensure that column names and column types are returned in the model
      output for every algorithm supported by H2O.  See https://github.com/h2oai/h2o-3/issues/12655.
    '''
    seed = 12345
    print("Checking GLM.....")
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    model =  H2OGeneralizedLinearEstimator(family = "binomial", alpha  = 1.0, lambda_search = False,
                                       max_iterations  = 2, seed = seed)
    checkColumnNamesTypesReturned(training1_data, model, ["displacement","power","weight", "acceleration","year"],
                              y_index= "economy_20mpg")

    print("Checking GLRM.....")
    irisH2O = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    glrm_h2o = H2OGeneralizedLowRankEstimator(k=3, loss="Quadratic", gamma_x=0.5, gamma_y=0.5, transform="STANDARDIZE")
    checkColumnNamesTypesReturned(irisH2O, glrm_h2o, irisH2O.names)
    
    print("Checking NaiveBayes......")
    model = H2ONaiveBayesEstimator(laplace=0.25)
    x_indices = irisH2O.names
    y_index = x_indices[-1]
    x_indices.remove(y_index)
    checkColumnNamesTypesReturned(irisH2O, model, x_indices, y_index=y_index)

    # deeplearning
    print("Checking deeplearning.....")
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/gaussian_training1_set.csv"))
    x_indices = training1_data.names
    y_index = x_indices[-1]
    x_indices.remove(y_index)
    model = H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[10, 10, 10])
    checkColumnNamesTypesReturned(training1_data, model, x_indices, y_index=y_index)

    # stack ensemble, stacking part is not iterative
    print("******************** Skip testing stack ensemble.  Test done in pyunit_stackedensemble_regression.py.")

    # GBM run
    print("Checking GBM.....")
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/multinomial_training1_set.csv"))
    x_indices = training1_data.names
    y_index = x_indices[-1]
    x_indices.remove(y_index)
    training1_data[y_index] = training1_data[y_index].round().asfactor()
    model = H2OGradientBoostingEstimator(distribution="multinomial", seed=seed)
    checkColumnNamesTypesReturned(training1_data, model, x_indices, y_index=y_index)

    # random foreset
    print("Checking Random Forest.....")
    model = H2ORandomForestEstimator(ntrees=100, score_tree_interval=0)
    checkColumnNamesTypesReturned(training1_data, model, x_indices, y_index=y_index)

    # PCA
    print("Checking PCA.....")
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/pca1000by25.csv"))
    x_indices = training1_data.names
    model = H2OPCA(k=10, transform="STANDARDIZE", pca_method="Power", compute_metrics=True)
    checkColumnNamesTypesReturned(training1_data, model, x_indices)

    # kmeans
    print("Checking kmeans....")
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/kmeans_8_centers_3_coords.csv"))
    x_indices = training1_data.names
    model = H2OKMeansEstimator(k=10)
    checkColumnNamesTypesReturned(training1_data, model, x_indices)

    # word2vec
    print("Checking word2vec....")
    train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/text8.gz"), header=1, col_types=["string"])
    used = train[0:170000, 0]
    w2v_model = H2OWord2vecEstimator()
    checkColumnNamesTypesReturned(train,  w2v_model, [],0)


def checkColumnNamesTypesReturned(training_data, model, x_indices, y_index=0):
    unsupervised = ("glrm" in model.algo) or ("pca" in model.algo) or ("kmeans" in model.algo)
    trainNames = []
    if unsupervised:
        model.train(x=x_indices, training_frame=training_data, max_runtime_secs=1)
        trainNames = x_indices
    else:
        if ('word2vec' in model.algo):
            model.train(training_frame=training_data, max_runtime_secs=1)
        else:
            model.train(x=x_indices, y=y_index, training_frame=training_data, max_runtime_secs=1)
            trainNames = x_indices+[y_index]
    trainTypes = training_data.types
    
    pyunit_utils.assertModelColNamesTypesCorrect(model._model_json["output"]['names'], 
                                                 model._model_json["output"]["column_types"], trainNames, trainTypes)

if __name__ == "__main__":
    pyunit_utils.standalone_test(algo_max_runtime_secs)
else:
    algo_max_runtime_secs()
