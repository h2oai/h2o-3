from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator


def coxph_smoke():
    heart = h2o.import_file(pyunit_utils.locate("smalldata/coxph_test/heart.csv"))
    coxph = H2OCoxProportionalHazardsEstimator(start_column="start", stop_column="stop")
    coxph.train(x="age", y="event", training_frame=heart)

    assert coxph.model_id != ""
    assert coxph.formula() == "Surv(start, stop, event) ~ age", \
        "Expected formula to be 'Surv(start, stop, event) ~ age' but it was " + coxph.formula()

    pred = coxph.predict(test_data=heart)
    assert len(pred) == len(heart)

    metrics = coxph.model_performance(heart)
    assert 0.581 > metrics.concordance() and 0.580 < metrics.concordance()
    assert 3696 == metrics.concordant()
    assert 10 == metrics.tiedY()


if __name__ == "__main__":
    pyunit_utils.standalone_test(coxph_smoke)
else:
    coxph_smoke()

