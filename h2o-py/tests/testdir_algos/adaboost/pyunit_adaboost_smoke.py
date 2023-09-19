import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OAdaBoostEstimator


def adaboost():
    print("AdaBoost Smoke Test")

    train = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    train["CAPSULE"] = train["CAPSULE"].asfactor()

    adaboost_model = H2OAdaBoostEstimator(nlearners=50, seed=0xBEEF, weak_learner="DRF", learn_rate=0.5)
    adaboost_model.train(training_frame=train, y="CAPSULE")
    predict = adaboost_model.predict(train)
    
    print("")
    print(adaboost_model)
    print("")
    print(predict)

    assert 50 == adaboost_model._model_json["output"]["model_summary"]["number_of_weak_learners"][0], "Python API is not working!"
    assert "DRF" == adaboost_model._model_json["output"]["model_summary"]["weak_learner"][0], "Python API is not working!"
    assert 0.5 == adaboost_model._model_json["output"]["model_summary"]["learn_rate"][0], "Python API is not working!"


if __name__ == "__main__":
    pyunit_utils.standalone_test(adaboost)
else:
    adaboost()
