import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OAdaBoostEstimator


def adaBoost_save_and_load():
    print("AdaBoost Save Load Test")
    
    train = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    train["CAPSULE"] = train["CAPSULE"].asfactor()

    adaboost_model = H2OAdaBoostEstimator(nlearners=7, seed=12)
    adaboost_model.train(training_frame=train, y="CAPSULE")
    predict = adaboost_model.predict(train)

    path = pyunit_utils.locate("results")

    assert os.path.isdir(path), "Expected save directory {0} to exist, but it does not.".format(path)
    model_path = h2o.save_model(adaboost_model, path=path, force=True)

    assert os.path.isfile(model_path), "Expected load file {0} to exist, but it does not.".format(model_path)
    reloaded = h2o.load_model(model_path)
    predict_reloaded = reloaded.predict(train)

    assert isinstance(reloaded,
                      H2OAdaBoostEstimator), \
        "Expected and H2OAdaBoostEstimator, but got {0}"\
        .format(reloaded)

    assert pyunit_utils.compare_frames_local(predict, predict_reloaded, returnResult=True)


if __name__ == "__main__":
    pyunit_utils.standalone_test(adaBoost_save_and_load)
else:
    adaBoost_save_and_load()
