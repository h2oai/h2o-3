import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OUpliftRandomForestEstimator
import numpy as np


def uplift_train_predict(uplift_metric, x_names, treatment_column, response_column, train_h2o, seed):
    print("train_predict: uplift_metric {0}".format(uplift_metric))
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


def uplift_train_performance_and_plot(uplift_metric, x_names, treatment_column, response_column, train_h2o, seed):
    print("train_performance: uplift_metric {0}".format(uplift_metric))
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
    perf = uplift_model.model_performance()
    perf.plot_uplift(plot=True, metric="lift")
    perf.plot_uplift(plot=True, metric="lift", normalize=True)
    n, uplift = perf.plot_uplift(plot=False, metric="gain")
    print(perf)
    print(uplift)
    return perf, uplift


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
        "Not expected output: Mean prediction is suspiciously different. " + str(uplift_predict_kl.mean())

    assert 0.08 < uplift_predict_euc.mean() < 0.09, \
        "Not expected output: Mean prediction is suspiciously different. " + str(uplift_predict_euc.mean())

    assert 0.08 < uplift_predict_chi.mean() < 0.09, \
        "Not expected output: Mean prediction is suspiciously different. " + str(uplift_predict_chi.mean())
    
    perf_kl,  uplift_kl = uplift_train_performance_and_plot("KL", x_names, treatment_column, response_column, train_h2o, seed)
    perf_euc,  uplift_euc = uplift_train_performance_and_plot("euclidean", x_names, treatment_column, response_column, train_h2o, seed)
    perf_chi, uplift_chi = uplift_train_performance_and_plot("chi_squared", x_names, treatment_column, response_column, train_h2o, seed)

    assert 93 < np.mean(uplift_kl) < 94, \
        "Not expected output: Mean uplift is suspiciously different. " + str(np.mean(uplift_kl))

    assert 85 < np.mean(uplift_euc) < 86, \
        "Not expected output: Mean uplift is suspiciously different. " + str(np.mean(uplift_euc))

    assert 405 < np.mean(uplift_chi) < 406, \
        "Not expected output: Mean uplift is suspiciously different. " + str(np.mean(uplift_chi))
    
    assert 399 < perf_kl.auuc() < 400, \
        "Not expected output: AUUC is suspiciously different. " + str(perf_kl.auuc())
    
    assert 5 < perf_kl.qini() < 6, \
        "Not expected output: Qini is suspiciously different. " + str(perf_kl.qini())
    
    assert 403 < perf_euc.auuc() < 404, \
        "Not expected output: AUUC is suspiciously different. " + str(perf_euc.auuc())

    assert 7 < perf_euc.qini() < 8, \
        "Not expected output: Qini is suspiciously different. " + str(perf_euc.qini())

    assert 409 < perf_chi.auuc() < 410, \
        "Not expected output: AUUC is suspiciously different. " + str(perf_chi.auuc())

    assert 9 < perf_chi.qini() < 10, \
        "Not expected output: Qini is suspiciously different. " + str(perf_chi.qini())
    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(uplift_random_forest_smoke)
else:
    uplift_random_forest_smoke()
