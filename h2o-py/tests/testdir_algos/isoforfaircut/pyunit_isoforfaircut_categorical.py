from __future__ import print_function
import sys
import h2o
sys.path.insert(1, "../../../")
from tests import pyunit_utils
from h2o.estimators.fair_cut_forest import H2OFairCutForestEstimator


def test_fair_cut_forest_categorical():
    """
    Test if Extended Isolation Forest accept categorical_encoding parameter.
    Extension level with default categorical_encoding should be in [0,1] but with "one_hot_explicit"
    it can be much more higher because of the generated columns.
    """

    print("Fair Cut Forest Categorical Test")
    train = h2o.import_file(pyunit_utils.locate("smalldata/parser/hexdev_497/airlines_small_csv"))
    train["Origin"] = train["Origin"].asfactor()
    train["Dest"] = train["Dest"].asfactor()
    fcf_model = H2OFairCutForestEstimator(ntrees=100,
                                                    seed=0x1234,
                                                    sample_size=256,
                                                    extension_level=20,
                                                    k_planes=1,
                                                    categorical_encoding="one_hot_explicit")
    fcf_model.train(x=["Origin", "Dest"], training_frame=train)
    anomaly_score = fcf_model.predict(train)
    anomaly = anomaly_score['anomaly_score'].as_data_frame(use_pandas=True)["anomaly_score"]

    assert 0.5 < anomaly.mean() < 0.53, \
        "Not expected output: Mean anomaly score is suspiciously different." + str(anomaly.mean())

    print(anomaly_score)
    print(fcf_model)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_fair_cut_forest_categorical)
else:
    test_fair_cut_forest_categorical()
