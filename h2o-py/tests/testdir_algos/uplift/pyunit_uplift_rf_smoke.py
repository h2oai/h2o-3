from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OUpliftRandomForestEstimator


def uplift_train_predict(uplift_metric, x_names, treatment_column, response_column, train_h2o, seed):
    print("uplift_metric {0}".format(uplift_metric))
    uplift_model = H2OUpliftRandomForestEstimator(
        ntrees=10,
        max_depth=5,
        treatment_column=treatment_column,
        uplift_metric=uplift_metric,
        distribution="bernoulli",
        min_rows=10,
        nbins=1000,
        seed=seed,
        sample_rate=0.99,
        auuc_type="gain"
    )
    uplift_model.train(y=response_column, x=x_names, training_frame=train_h2o)
    prediction = uplift_model.predict(train_h2o)
    print(prediction)
    return prediction['uplift_predict'].as_data_frame(use_pandas=True)["uplift_predict"]


def uplift_random_forest_smoke():
    seed = 12345
    treatment_column = "treatment"
    response_column = "outcome"
    x_names = ["feature_"+str(x) for x in range(1,13)]

    train_h2o = h2o.upload_file(pyunit_utils.locate("smalldata/uplift/upliftml_train.csv"))
    train_h2o[treatment_column] = train_h2o[treatment_column].asfactor()
    train_h2o[response_column] = train_h2o[response_column].asfactor()

    uplift_predict_kl = uplift_train_predict("KL", x_names, treatment_column, response_column, train_h2o, seed)
    uplift_predict_euc = uplift_train_predict("euclidean", x_names, treatment_column, response_column, train_h2o, seed)
    uplift_predict_chi = uplift_train_predict("chi_squared", x_names, treatment_column, response_column, train_h2o, seed)

    assert 0.08 < uplift_predict_kl.mean() < 0.09, \
        "Not expected output: Mean uplift is suspiciously different. " + str(uplift_predict_kl.mean())

    assert 0.08 < uplift_predict_euc.mean() < 0.09, \
        "Not expected output: Mean uplift is suspiciously different. " + str(uplift_predict_euc.mean())

    assert 0.08 < uplift_predict_chi.mean() < 0.09, \
        "Not expected output: Mean uplift is suspiciously different. " + str(uplift_predict_chi.mean())


if __name__ == "__main__":
    pyunit_utils.standalone_test(uplift_random_forest_smoke)
else:
    uplift_random_forest_smoke()
