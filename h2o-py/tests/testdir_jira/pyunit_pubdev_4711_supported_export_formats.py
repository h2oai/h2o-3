from __future__ import print_function

import sys

sys.path.insert(1, "../../../")
import random
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator
from h2o.estimators.kmeans import H2OKMeansEstimator
from h2o.estimators.naive_bayes import H2ONaiveBayesEstimator
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from h2o.estimators.word2vec import H2OWord2vecEstimator
from h2o.exceptions import H2OValueError

RESULT_DIR = pyunit_utils.locate("results")


def expect_error(action, model, format):
    try:
        action(path=RESULT_DIR)
        assert False, "There should be an error when trying to export %s to %s" % (model, format)
    except H2OValueError as e:
        print("Expected H2OValueError message: '%s'" % e)


def deeplearning_export():
    print("###### DEEPLEARNING ######")
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    problem = random.sample(list(range(3)), 1)[0]
    predictors = ["displacement", "power", "weight", "acceleration", "year"]
    if problem == 1:
        response_col = "economy_20mpg"
        frame[response_col] = frame[response_col].asfactor()
    elif problem == 2:
        response_col = "cylinders"
        frame[response_col] = frame[response_col].asfactor()
    else:
        response_col = "economy"
    print("Response column: {0}".format(response_col))
    model = H2ODeepLearningEstimator(nfolds=random.randint(3, 10), fold_assignment="Modulo", hidden=[20, 20], epochs=10)
    model.train(x=predictors, y=response_col, training_frame=frame)
    h2o.download_pojo(model, path=RESULT_DIR)
    model.download_mojo(path=RESULT_DIR)


def gbm_export():
    print("###### GBM ######")
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    frame["CAPSULE"] = frame["CAPSULE"].asfactor()
    model = H2OGradientBoostingEstimator(ntrees=100, learn_rate=0.1,
                                         max_depth=5,
                                         min_rows=10,
                                         distribution="bernoulli")
    model.train(x=list(range(1, frame.ncol)), y="CAPSULE", training_frame=frame)
    h2o.download_pojo(model, path=RESULT_DIR)
    model.download_mojo(path=RESULT_DIR)


def glm_export():
    print("###### GLM ######")
    frame = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    Y = 3
    X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]
    model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, Lambda=1e-5)
    model.train(x=X, y=Y, training_frame=frame)
    h2o.download_pojo(model, path=RESULT_DIR)
    model.download_mojo(path=RESULT_DIR)


def glrm_export():
    print("###### GLRM ######")
    frame = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    model = H2OGeneralizedLowRankEstimator(k=8, init="svd", recover_svd=True)
    model.train(x=frame.names, training_frame=frame)
    expect_error(model.download_pojo, model="GLRM", format='POJO')
    model.download_mojo(path=RESULT_DIR)


def k_means_export():
    print("###### K MEANS ######")
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/benign.csv"))
    model = H2OKMeansEstimator(k=1)
    model.train(x=list(range(frame.ncol)), training_frame=frame)
    h2o.download_pojo(model, path=RESULT_DIR)
    model.download_mojo(path=RESULT_DIR)


def naive_bayes_export():
    print("###### NAIVE BAYES ######")
    frame = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    model = H2ONaiveBayesEstimator(laplace=0.25)
    model.train(x=list(range(4)), y=4, training_frame=frame)
    h2o.download_pojo(model, path=RESULT_DIR)
    expect_error(model.download_mojo, model="Naive Bayes", format='MOJO')


def pca_export():
    print("###### PCA ######")
    frame = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
    model = H2OPrincipalComponentAnalysisEstimator(k=3, impute_missing=True)
    model.train(x=list(range(4)), training_frame=frame)
    h2o.download_pojo(model, path=RESULT_DIR)
    expect_error(model.download_mojo, model="PCA", format='MOJO')


def drf_export():
    print("###### DRF ######")
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/bigcat_5000x2.csv"))
    frame["y"] = frame["y"].asfactor()
    model = H2ORandomForestEstimator(ntrees=1, max_depth=1, nbins=100, nbins_cats=10)
    model.train(x="X", y="y", training_frame=frame)
    h2o.download_pojo(model, path=RESULT_DIR)
    model.download_mojo(path=RESULT_DIR)


def stacked_ensemble_export():
    print("###### STACKED ENSEMBLE ######")
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_train_5k.csv"),
                            destination_frame="higgs_train_5k")
    x = frame.columns
    y = "response"
    x.remove(y)
    frame[y] = frame[y].asfactor()
    my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                          ntrees=10,
                                          nfolds=5,
                                          fold_assignment="Modulo",
                                          keep_cross_validation_predictions=True,
                                          seed=1)
    my_gbm.train(x=x, y=y, training_frame=frame)
    my_rf = H2ORandomForestEstimator(ntrees=50,
                                     nfolds=5,
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True,
                                     seed=1)
    my_rf.train(x=x, y=y, training_frame=frame)
    model = H2OStackedEnsembleEstimator(model_id="my_ensemble_binomial1",
                                        base_models=[my_gbm.model_id, my_rf.model_id])
    model.train(x=x, y=y, training_frame=frame)
    expect_error(model.download_pojo, "Stacked Enemble", "POJO")
    model.download_mojo(path=RESULT_DIR)


def word2vec_export():
    print("###### WORD2VEC ######")
    words = h2o.create_frame(rows=1000, cols=1, string_fraction=1.0, missing_fraction=0.0)
    embeddings = h2o.create_frame(rows=1000, cols=100, real_fraction=1.0, missing_fraction=0.0)
    frame = words.cbind(embeddings)
    model = H2OWord2vecEstimator(pre_trained=frame)
    model.train(training_frame=frame)
    expect_error(model.download_pojo, model="Word2Vec", format="POJO")
    model.download_mojo(path=RESULT_DIR)


if __name__ == "__main__":
    pyunit_utils.standalone_test(deeplearning_export)
    pyunit_utils.standalone_test(gbm_export)
    pyunit_utils.standalone_test(glm_export)
    pyunit_utils.standalone_test(glrm_export)
    pyunit_utils.standalone_test(k_means_export)
    pyunit_utils.standalone_test(naive_bayes_export)
    pyunit_utils.standalone_test(pca_export)
    pyunit_utils.standalone_test(drf_export)
    pyunit_utils.standalone_test(stacked_ensemble_export)
    pyunit_utils.standalone_test(word2vec_export)
else:
    deeplearning_export()
    gbm_export()
    glm_export()
    glrm_export()
    k_means_export()
    naive_bayes_export()
    pca_export()
    drf_export()
    stacked_ensemble_export()
    word2vec_export()
