from __future__ import print_function
import h2o
import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator


def stackedensemble_gaussian():
    australia_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/extdata/australia.csv"),
                                    destination_frame="australia.hex")
    myX = ["premax", "salmax", "minairtemp", "maxairtemp", "maxsst", "maxsoilmoist", "Max_czcs"]
    # myXSmaller = ["premax", "salmax","minairtemp", "maxairtemp", "maxsst", "maxsoilmoist"]
    # dependent = "runoffnew"

    my_gbm = H2OGradientBoostingEstimator(ntrees=10, max_depth=3, min_rows=2, learn_rate=0.2, nfolds=5,
                                          fold_assignment="Modulo", keep_cross_validation_predictions=True,
                                          distribution="gaussian")
    my_gbm.train(y="runoffnew", x=myX, training_frame=australia_hex)
    print("GBM performance: ")
    my_gbm.model_performance(australia_hex).show()


    my_glm = H2OGeneralizedLinearEstimator(family="gaussian", nfolds=5, fold_assignment="Modulo",
                                           keep_cross_validation_predictions=True)
    my_glm.train(y="runoffnew", training_frame=australia_hex)
    # my_glm.train(y = "runoffnew", x = myX, training_frame = australia_hex)
    # my_glm.train(y = "runoffnew", x = myXSmaller, training_frame = australia_hex)  # test parameter error-checking
    print("GLM performance: ")
    my_glm.model_performance(australia_hex).show()


    stacker = H2OStackedEnsembleEstimator(selection_strategy="choose_all",
                                          base_models=[my_gbm.model_id, my_glm.model_id])
    stacker.train(model_id="my_ensemble", x=myX, y="runoffnew", training_frame=australia_hex)
    # test ignore_columns parameter checking
    # stacker.train(model_id="my_ensemble", y="runoffnew", training_frame=australia_hex, ignored_columns=["premax"])
    predictions = stacker.predict(australia_hex)  # training data
    print("Predictions for australia ensemble are in: " + predictions.frame_id)

    ecology_train = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"), destination_frame="ecology_train")
    myX = ["SegSumT", "SegTSeas", "SegLowFlow", "DSDist", "DSMaxSlope", "USAvgT", "USRainDays", "USSlope", "USNative", "DSDam", "Method", "LocSed"]
  #  myXSmaller = ["SegSumT", "SegTSeas", "SegLowFlow"]
  
    my_gbm = H2OGradientBoostingEstimator(ntrees = 10, max_depth = 3, min_rows = 2, learn_rate = 0.2, nfolds = 5, fold_assignment='Modulo', keep_cross_validation_predictions = True, distribution = "gaussian")
    my_gbm.train(y = "Angaus", x = myX, training_frame = ecology_train)
    print("GBM performance: ")
    my_gbm.model_performance(ecology_train).show()

 
    my_glm = H2OGeneralizedLinearEstimator(family = "gaussian", nfolds = 5, fold_assignment='Modulo', keep_cross_validation_predictions = True)
    my_glm.train(y = "Angaus", x = myX, training_frame = ecology_train)
    print("GLM performance: ")
    my_glm.model_performance(ecology_train).show()


    stacker = H2OStackedEnsembleEstimator(selection_strategy="choose_all", base_models=[my_gbm.model_id, my_glm.model_id])
    print("created H2OStackedEnsembleEstimator: " + str(stacker))
    stacker.train(model_id="my_ensemble", y="Angaus", training_frame=ecology_train)
    print("trained H2OStackedEnsembleEstimator: " + str(stacker))
    predictions = stacker.predict(ecology_train)  # training data
    print("preditions for ensemble are in: " + predictions.frame_id)

if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_gaussian)
else:
    stackedensemble_gaussian()
