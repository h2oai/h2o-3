from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OUpliftRandomForestEstimator
from causalml.dataset import make_uplift_classification


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
    n_samples = 500
    seed = 12345

    # Data generation
    # Generate dataset with 10 features, treatment/control flag feature
    # and outcome feature (In this case "conversion")
    train, x_names = make_uplift_classification(n_samples=n_samples,  # n_samples*2 rows will be generated
                                                                      # (#n_samples for each treatment_name)
                                                treatment_name=['control', 'treatment'],
                                                n_classification_features=10,
                                                # Dataset contains only features valid for modeling
                                                # Do not confuse model with irrelevant or redundant features
                                                n_classification_informative=10,
                                                random_seed=seed
                                                )

    treatment_column = "treatment_group_key"
    response_column = "conversion"

    train_h2o = h2o.H2OFrame(train)
    train_h2o[treatment_column] = train_h2o[treatment_column].asfactor()
    train_h2o[response_column] = train_h2o[response_column].asfactor()

    uplift_predict_kl = uplift_train_predict("KL", x_names, treatment_column, response_column, train_h2o, seed)
    uplift_predict_euc = uplift_train_predict("euclidean", x_names, treatment_column, response_column, train_h2o, seed)
    uplift_predict_chi = uplift_train_predict("chi_squared", x_names, treatment_column, response_column, train_h2o, seed)

    assert 0.02 < uplift_predict_kl.mean() < 0.03, \
        "Not expected output: Mean uplift is suspiciously different. " + str(uplift_predict_kl.mean())

    assert -0.007 < uplift_predict_euc.mean() < -0.005, \
        "Not expected output: Mean uplift is suspiciously different. " + str(uplift_predict_euc.mean())

    assert 0.0008 < uplift_predict_chi.mean() < 0.0010, \
        "Not expected output: Mean uplift is suspiciously different. " + str(uplift_predict_chi.mean())


if __name__ == "__main__":
    pyunit_utils.standalone_test(uplift_random_forest_smoke)
else:
    uplift_random_forest_smoke()
