import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


# A more detailed test on checking the warning messages from scoring/prediction functions.
def covType_lambdaSearch():
    covtype = h2o.import_file(path=pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
    covtype[54] = covtype[54].asfactor()
    covtypeTest = h2o.import_file(path=pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
    covtypeTest[54] = covtype[54].asfactor()

    model = H2OGeneralizedLinearEstimator(family="multinomial", lambda_search=True, solver="IRLSM_NATIVE")
    model.train(x=list(range(54)), y=54, training_frame=covtype)
    modelLogloss = model._model_json["output"]["training_metrics"]._metric_json["logloss"]
    modelMeanError = model._model_json["output"]["training_metrics"]._metric_json["mean_per_class_error"]
    model2 = H2OGeneralizedLinearEstimator(family="multinomial", lambda_search=True, solver="IRLSM")
    model2.train(x=list(range(54)), y=54, training_frame=covtype)
    model2Logloss = model2._model_json["output"]["training_metrics"]._metric_json["logloss"]
    model2MeanError = model2._model_json["output"]["training_metrics"]._metric_json["mean_per_class_error"]
    
    print("IRLSM_SPEEDUP logloss: {0}.  IRLSM logloss: {1}".format(modelLogloss, model2Logloss))
    print("IRLSM_SPEEDUP mean_per_class_error: {0}.  IRLSM mean_per_class_error: {1}".format(modelMeanError, 
                                                                                             model2MeanError))
    assert (modelLogloss-model2Logloss)<1e-2
    assert (modelMeanError-model2MeanError)<1e-2
    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(covType_lambdaSearch)
else:
    covType_lambdaSearch()
