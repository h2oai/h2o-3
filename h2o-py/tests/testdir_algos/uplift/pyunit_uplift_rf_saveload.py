from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators import H2OUpliftRandomForestEstimator


def uplift_random_forest_save_and_load():
    print("Uplift Distributed Random Forest Save Load Test")
    seed = 12345

    treatment_column = "treatment"
    response_column = "outcome"
    x_names = ["feature_"+str(x) for x in range(1, 13)]

    train_h2o = h2o.upload_file(pyunit_utils.locate("smalldata/uplift/upliftml_train.csv"))
    train_h2o[treatment_column] = train_h2o[treatment_column].asfactor()
    train_h2o[response_column] = train_h2o[response_column].asfactor()

    n_samples = train_h2o.shape[0]

    uplift_model = H2OUpliftRandomForestEstimator(
        ntrees=10,
        max_depth=5,
        treatment_column=treatment_column,
        uplift_metric="KL",
        distribution="bernoulli",
        min_rows=10,
        nbins=1000,
        seed=seed,
        sample_rate=0.99,
        auuc_type="gain"
    )
    uplift_model.train(y=response_column, x=x_names, training_frame=train_h2o)
    prediction = uplift_model.predict(train_h2o)
    assert_equals(n_samples, prediction.shape[0], "Not correct shape")
    assert_equals(3, prediction.shape[1], "Not correct shape")
    print(prediction)
    uplift_predict = prediction['uplift_predict'].as_data_frame(use_pandas=True)["uplift_predict"]

    path = pyunit_utils.locate("results")

    assert os.path.isdir(path), "Expected save directory {0} to exist, but it does not. ".format(path)
    model_path = h2o.save_model(uplift_model, path=path, force=True)

    assert os.path.isfile(model_path), "Expected load file {0} to exist, but it does not. ".format(model_path)
    reloaded = h2o.load_model(model_path)
    prediction_reloaded = reloaded.predict(train_h2o)
    assert_equals(n_samples, prediction.shape[0], "Not correct shape")
    assert_equals(3, prediction.shape[1], "Not correct shape")
    print(prediction_reloaded)
    uplift_predict_reloaded = prediction_reloaded['uplift_predict'].as_data_frame(use_pandas=True)["uplift_predict"]

    assert isinstance(reloaded,
                      H2OUpliftRandomForestEstimator), \
        "Expected and H2OUpliftRandomForestEstimator, but got {0}" \
        .format(reloaded)

    assert_equals(uplift_predict[0], uplift_predict_reloaded[0], "Output is not the same after reload")
    assert_equals(uplift_predict[5], uplift_predict_reloaded[5], "Output is not the same after reload")
    assert_equals(uplift_predict[33], uplift_predict_reloaded[33], "Output is not the same after reload")
    assert_equals(uplift_predict[256], uplift_predict_reloaded[256], "Output is not the same after reload")
    assert_equals(uplift_predict[499], uplift_predict_reloaded[499], "Output is not the same after reload")
    assert_equals(uplift_predict[512], uplift_predict_reloaded[512], "Output is not the same after reload")
    assert_equals(uplift_predict[750], uplift_predict_reloaded[750], "Output is not the same after reload")
    assert_equals(uplift_predict[999], uplift_predict_reloaded[999], "Output is not the same after reload")


if __name__ == "__main__":
    pyunit_utils.standalone_test(uplift_random_forest_save_and_load)
else:
    uplift_random_forest_save_and_load()
