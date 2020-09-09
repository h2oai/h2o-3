from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o

from lifelines import CoxPHFitter
from lifelines.datasets import load_rossi



from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator


def coxph_smoke():
    rossi = load_rossi()

    cph = CoxPHFitter()
    cph.fit(rossi, duration_col='week', event_col='arrest')

    cph.print_summary()

    rossiH2O = h2o.H2OFrame(rossi)
    cphH2O = H2OCoxProportionalHazardsEstimator(stop_column="week")
    cphH2O.train(x=["age", "fin", "race", "wexp", "mar", "paro", "prio"], y="arrest", training_frame=rossiH2O)

    assert cphH2O.model_id != ""
    assert cphH2O.formula() == "Surv(week, arrest) ~ fin + age + race + wexp + mar + paro + prio", \
        "Expected formula to be 'Surv(week, arrest) ~ fin + age + race + wexp + mar + paro + prio' but it was " + cphH2O.formula()

    predH2O = cphH2O.predict(test_data=rossiH2O)
    assert len(predH2O) == len(rossi)

    metricsH2O = cphH2O.model_performance(rossiH2O)
    assert abs(cph._model._concordance_index_ - metricsH2O.concordance()) < 0.001


if __name__ == "__main__":
    pyunit_utils.standalone_test(coxph_smoke)
else:
    coxph_smoke()

