from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.klime import H2OKLimeEstimator



def kLIMEtitanic():
    # Load Titanic dataset (with predictions of 'Survived' made by GBM)
    titanic_input = h2o.import_file(path = pyunit_utils.locate("smalldata/klime_test/titanic_input.csv"),
                                    col_types=["enum","enum","real","real","real","enum","real","real","real","real"])
    titanic_expected = h2o.import_file(path = pyunit_utils.locate("smalldata/klime_test/titanic_3_expected.csv"),
                                       col_types=["real","real","real","real","real","real","real","real","real"])

    # Train a k-LIME model
    klime = H2OKLimeEstimator(seed=12345, max_k=3, estimate_k=False)
    klime.train(training_frame=titanic_input, y="p1", ignored_columns=["PassengerId", "Survived", "predict", "p0"])
    klime.show()

    # Use as a regular regression model to predict
    titanic_predicted = klime.predict(titanic_input)
    titanic_predicted.show(use_pandas=True)

    # Check that MSE is not too off (and that it is calculated correctly)
    predicted_p1 = titanic_predicted["predict_klime"]
    mse_manual = ((titanic_input["p1"] - predicted_p1) * (titanic_input["p1"] - predicted_p1)).mean()[0,0]
    assert abs(klime.mse() - mse_manual) < 1e-6
    assert abs(klime.mse() - 0.00937167549983) < 1e-6

    # Clusters are the same
    assert (abs(titanic_predicted["cluster_klime"] - titanic_expected["cluster_klime"])).sum()[0,0] == 0

    # K-Lime prediction are almost the same as expected predictions
    assert (abs(titanic_predicted["predict_klime"] - titanic_expected["predict_klime"]) > 0.005).sum()[0,0] == 0

if __name__ == "__main__":
    pyunit_utils.standalone_test(kLIMEtitanic)
else:
    kLIMEtitanic()
