from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.utils.typechecks import assert_is_type
from h2o.model.metrics_base import H2OMultinomialModelMetrics

def h2omake_metrics_mutlinomial():
    """
    Python API test: h2o.make_metrics(predicted, actual, domain=None, distribution=None)

    Copied from pyunit_make_metrics.py
    """
    fr = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    fr["CAPSULE"] = fr["CAPSULE"].asfactor()
    fr["RACE"] = fr["RACE"].asfactor()

    response = "RACE"
    predictors = list(set(fr.names) - {"ID", response})
    model = H2OGradientBoostingEstimator(distribution="multinomial", ntrees=2, max_depth=3, min_rows=1,
                                         learn_rate=0.01, nbins=20, auc_type="MACRO_OVR")
    model.train(x=predictors, y=response, training_frame=fr)
    predicted = h2o.assign(model.predict(fr)[1:], "pred")
    actual = h2o.assign(fr[response].asfactor(), "act")
    domain = fr[response].levels()[0]

    m0 = model.model_performance(train=True)
    m1 = h2o.make_metrics(predicted, actual, domain=domain, auc_type="MACRO_OVR")
    m2 = h2o.make_metrics(predicted, actual, auc_type="MACRO_OVR")
    assert_is_type(m1, H2OMultinomialModelMetrics)
    assert_is_type(m2, H2OMultinomialModelMetrics)
    assert abs(m0.mse() - m1.mse()) < 1e-5
    assert abs(m0.rmse() - m1.rmse()) < 1e-5
    assert abs(m0.logloss() - m1.logloss()) < 1e-5
    assert abs(m0.mean_per_class_error() - m1.mean_per_class_error()) < 1e-5
    assert abs(m0.auc() - m1.auc()) < 1e-5
    assert abs(m0.aucpr() - m1.aucpr()) < 1e-5
    assert abs(m2.mse() - m1.mse()) < 1e-5
    assert abs(m2.rmse() - m1.rmse()) < 1e-5
    assert abs(m2.logloss() - m1.logloss()) < 1e-5
    assert abs(m2.mean_per_class_error() - m1.mean_per_class_error()) < 1e-5
    assert abs(m2.auc() - m1.auc()) < 1e-5
    assert abs(m2.aucpr() - m1.aucpr()) < 1e-5


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2omake_metrics_mutlinomial)
else:
    h2omake_metrics_mutlinomial()
