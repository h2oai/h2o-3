import random
import sys

sys.path.insert(1, "../../../")
import h2o
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
from tests import pyunit_utils


def test_gam_splines_non_negative_actual_params():
    random.seed(42)
    n = 50

    x1 = [random.gauss(0, 1) for _ in range(n)]
    x2 = [random.gauss(5, 2) for _ in range(n)]
    x3 = [random.uniform(0, 10) for _ in range(n)]
    response = [random.gauss(5, 2) for _ in range(n)]

    h2o_data = h2o.H2OFrame({
        "Response": response,
        "X1": x1,
        "X2": x2,
        "X3": x3
    })

    splines_non_negative = [True, False]
    gam = H2OGeneralizedAdditiveEstimator(
        gam_columns=["X1", "X2"],
        splines_non_negative=splines_non_negative,
        bs=[2, 2],
        spline_orders=[2, 3]
    )
    gam.train(x=["X3"], y="Response", training_frame=h2o_data)

    assert gam.actual_params["splines_non_negative"] == splines_non_negative
    assert gam.parms["splines_non_negative"]["actual_value"] == splines_non_negative


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_splines_non_negative_actual_params)
else:
    test_gam_splines_non_negative_actual_params()
