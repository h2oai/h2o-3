from __future__ import print_function
import h2o
import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator


def stackedensemble_gaussian():
    # 
    # australia.csv: Gaussian
    # 
    australia_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/extdata/australia.csv"),
                                    destination_frame="australia.hex")
    myX = ["premax", "salmax", "minairtemp", "maxairtemp", "maxsst", "maxsoilmoist", "Max_czcs"]
    # myXSmaller = ["premax", "salmax","minairtemp", "maxairtemp", "maxsst", "maxsoilmoist"]
    # dependent = "runoffnew"

    my_gbm = H2OGradientBoostingEstimator(ntrees=10, max_depth=3, min_rows=2, learn_rate=0.2, nfolds=5,
                                          fold_assignment="Modulo", keep_cross_validation_predictions=True,
                                          distribution="AUTO")
    my_gbm.train(y="runoffnew", x=myX, training_frame=australia_hex)
    print("GBM performance: ")
    my_gbm.model_performance(australia_hex).show()


    my_rf = H2ORandomForestEstimator(ntrees=10, max_depth=3, min_rows=2, nfolds=5,
                                          fold_assignment="Modulo", keep_cross_validation_predictions=True)
    my_rf.train(y="runoffnew", x=myX, training_frame=australia_hex)
    print("RF performance: ")
    my_rf.model_performance(australia_hex).show()


    my_dl = H2ODeepLearningEstimator(nfolds=5, fold_assignment="Modulo", keep_cross_validation_predictions=True)
    my_dl.train(y="runoffnew", x=myX, training_frame=australia_hex)
    print("DL performance: ")
    my_dl.model_performance(australia_hex).show()


    # NOTE: don't specify family
    my_glm = H2OGeneralizedLinearEstimator(nfolds=5, fold_assignment="Modulo",
                                           keep_cross_validation_predictions=True)
    my_glm.train(y="runoffnew", training_frame=australia_hex)
    # my_glm.train(y = "runoffnew", x = myX, training_frame = australia_hex)
    # my_glm.train(y = "runoffnew", x = myXSmaller, training_frame = australia_hex)  # test parameter error-checking
    print("GLM performance: ")
    my_glm.model_performance(australia_hex).show()


    stack = H2OStackedEnsembleEstimator(base_models=[my_gbm.model_id, my_rf.model_id, my_glm.model_id])
    stack.train(model_id="my_ensemble", x=myX, y="runoffnew", training_frame=australia_hex)
    # test ignore_columns parameter checking
    # stack.train(model_id="my_ensemble", y="runoffnew", training_frame=australia_hex, ignored_columns=["premax"])
    predictions = stack.predict(australia_hex)  # training data
    print("Predictions for australia ensemble are in: " + predictions.frame_id)




    # 
    # ecology.csv: Gaussian
    # 
    ecology_train = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"), destination_frame="ecology_train")
    myX = ["SegSumT", "SegTSeas", "SegLowFlow", "DSDist", "DSMaxSlope", "USAvgT", "USRainDays", "USSlope", "USNative", "DSDam", "Method", "LocSed"]
  #  myXSmaller = ["SegSumT", "SegTSeas", "SegLowFlow"]
  
    my_gbm = H2OGradientBoostingEstimator(ntrees = 10, max_depth = 3, min_rows = 2, learn_rate = 0.2, nfolds = 5, fold_assignment='Modulo', keep_cross_validation_predictions = True, distribution = "AUTO")
    my_gbm.train(y = "Angaus", x = myX, training_frame = ecology_train)
    print("GBM performance: ")
    my_gbm.model_performance(ecology_train).show()

 
    my_rf = H2ORandomForestEstimator(ntrees = 10, max_depth = 3, min_rows = 2, nfolds = 5, fold_assignment='Modulo', keep_cross_validation_predictions = True)
    my_rf.train(y = "Angaus", x = myX, training_frame = ecology_train)
    print("RF performance: ")
    my_rf.model_performance(ecology_train).show()

 
    my_dl = H2ODeepLearningEstimator(nfolds=5, fold_assignment='Modulo', keep_cross_validation_predictions = True)
    my_dl.train(y = "Angaus", x = myX, training_frame = ecology_train)
    print("DL performance: ")
    my_dl.model_performance(ecology_train).show()

 
    # NOTE: don't specify family
    my_glm = H2OGeneralizedLinearEstimator(nfolds = 5, fold_assignment='Modulo', keep_cross_validation_predictions = True)
    my_glm.train(y = "Angaus", x = myX, training_frame = ecology_train)
    print("GLM performance: ")
    my_glm.model_performance(ecology_train).show()


    stack = H2OStackedEnsembleEstimator(base_models=[my_gbm.model_id, my_rf.model_id, my_glm.model_id])
    print("created H2OStackedEnsembleEstimator: " + str(stack))
    stack.train(model_id="my_ensemble", y="Angaus", training_frame=ecology_train)
    print("trained H2OStackedEnsembleEstimator: " + str(stack))
    print("trained H2OStackedEnsembleEstimator via get_model: " + str(h2o.get_model("my_ensemble")))

    predictions = stack.predict(ecology_train)  # training data
    print("predictions for ensemble are in: " + predictions.frame_id)





    # 
    # insurance.csv: Poisson
    # 
    insurance_train = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/insurance.csv"), destination_frame="insurance_train")
    insurance_train["offset"] = insurance_train["Holders"].log()

    myX = list(range(3))
  
    my_gbm = H2OGradientBoostingEstimator(ntrees = 10, max_depth = 3, min_rows = 2, learn_rate = 0.2, nfolds = 5, fold_assignment='Modulo', keep_cross_validation_predictions = True, distribution = 'poisson')
    my_gbm.train(y = "Claims", x = myX, training_frame = insurance_train)
    print("GBM performance: ")
    my_gbm.model_performance(insurance_train).show()

 
    my_rf = H2ORandomForestEstimator(ntrees = 10, max_depth = 3, min_rows = 2, nfolds = 5, fold_assignment='Modulo', keep_cross_validation_predictions = True)
    my_rf.train(y = "Claims", x = myX, training_frame = insurance_train)
    print("RF performance: ")
    my_rf.model_performance(insurance_train).show()

 
    my_dl = H2ODeepLearningEstimator(nfolds=5, fold_assignment='Modulo', keep_cross_validation_predictions = True, distribution = 'poisson')
    my_dl.train(y = "Claims", x = myX, training_frame = insurance_train)
    print("DL performance: ")
    my_dl.model_performance(insurance_train).show()

 
    # NOTE: don't specify family
    my_glm = H2OGeneralizedLinearEstimator(nfolds = 5, fold_assignment='Modulo', keep_cross_validation_predictions = True, family = 'poisson')
    my_glm.train(y = "Claims", x = myX, training_frame = insurance_train)
    print("GLM performance: ")
    my_glm.model_performance(insurance_train).show()


    stack = H2OStackedEnsembleEstimator(base_models=[my_gbm.model_id, my_rf.model_id, my_glm.model_id])
    print("created H2OStackedEnsembleEstimator: " + str(stack))
    stack.train(model_id="my_ensemble", y="Claims", training_frame=insurance_train)
    print("trained H2OStackedEnsembleEstimator: " + str(stack))

    print("metalearner: ")
    print(h2o.get_model(stack.metalearner()['name']))

    predictions = stack.predict(insurance_train)  # training data
    print("preditions for ensemble are in: " + predictions.frame_id)

if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_gaussian)
else:
    stackedensemble_gaussian()
