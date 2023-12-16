import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators import H2OUpliftRandomForestEstimator


def uplift_random_forest_mojo():
    print("Uplift Distributed Random Forest MOJO Test")
    seed = 12345

    treatment_column = "treatment"
    response_column = "outcome"
    x_names = ["feature_"+str(x) for x in range(1, 13)]

    train_h2o = h2o.upload_file(pyunit_utils.locate("smalldata/uplift/upliftml_train.csv"))
    train_h2o[treatment_column] = train_h2o[treatment_column].asfactor()
    train_h2o[response_column] = train_h2o[response_column].asfactor()

    n_samples = train_h2o.shape[0]

    uplift_model = H2OUpliftRandomForestEstimator(
        ntrees=5,
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
    print(uplift_model)
    
    prediction = uplift_model.predict(train_h2o)
    
    assert_equals(n_samples, prediction.shape[0], "Not correct shape")
    assert_equals(3, prediction.shape[1], "Not correct shape")
    uplift_predict = prediction['uplift_predict'].as_data_frame(use_pandas=True)["uplift_predict"]

    path = pyunit_utils.locate("results")

    assert os.path.isdir(path), "Expected save directory {0} to exist, but it does not. ".format(path)
    model_path = uplift_model.download_mojo(path=path)

    assert os.path.isfile(model_path), "Expected load file {0} to exist, but it does not. ".format(model_path)
    mojo_model = h2o.upload_mojo(model_path)
    print(mojo_model)
    
    prediction_mojo = mojo_model.predict(train_h2o)
    
    assert_equals(n_samples, prediction.shape[0], "Not correct shape")
    assert_equals(3, prediction.shape[1], "Not correct shape")
    print(prediction_mojo)
    uplift_predict_mojo = prediction_mojo['uplift_predict'].as_data_frame(use_pandas=True)["uplift_predict"]

    assert_equals(uplift_predict[0], uplift_predict_mojo[0], "Output is not the same with MOJO")
    assert_equals(uplift_predict[5], uplift_predict_mojo[5], "Output is not the same with MOJO")
    assert_equals(uplift_predict[33], uplift_predict_mojo[33], "Output is not the same with MOJO")
    assert_equals(uplift_predict[256], uplift_predict_mojo[256], "Output is not the same with MOJO")
    assert_equals(uplift_predict[499], uplift_predict_mojo[499], "Output is not the same with MOJO")
    assert_equals(uplift_predict[512], uplift_predict_mojo[512], "Output is not the same with MOJO")
    assert_equals(uplift_predict[750], uplift_predict_mojo[750], "Output is not the same with MOJO")
    assert_equals(uplift_predict[999], uplift_predict_mojo[999], "Output is not the same with MOJO")

    perf_model = uplift_model.model_performance()
    print(perf_model)

    perf_mojo = mojo_model.model_performance()
    print(perf_mojo)

    perf_model_auuc = perf_model.auuc()
    perf_mojo_auuc = perf_mojo.auuc()
    assert_equals(perf_model_auuc, perf_mojo_auuc, "AUUC is not the same with MOJO")

    perf_model_qini = perf_model.qini()
    perf_mojo_qini = perf_mojo.qini()
    assert_equals(perf_model_qini, perf_mojo_qini, "Qini is not the same with MOJO")
    
    

    os.remove(model_path)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(uplift_random_forest_mojo)
else:
    uplift_random_forest_mojo()
