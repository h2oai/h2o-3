import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator


def coxph_smoke_strata():
    heart = h2o.import_file(pyunit_utils.locate("smalldata/coxph_test/heart.csv"))
    heart['transplant'] = heart['transplant'].asfactor()
    coxph = H2OCoxProportionalHazardsEstimator(stratify_by = ["transplant"], start_column="start", stop_column="stop")
    coxph.train(x=["age", "surgery", "transplant"], y="event", training_frame=heart)

    assert coxph.model_id != ""
    assert coxph.formula() == "Surv(start, stop, event) ~ age + surgery + strata(transplant)", \
        "Expected formula to be 'Surv(start, stop, event) ~ age + surgery + strata(transplant)' but it was " + coxph.formula()

    pred = coxph.predict(test_data=heart)
    assert len(pred) == len(heart)


if __name__ == "__main__":
    pyunit_utils.standalone_test(coxph_smoke_strata)
else:
    coxph_smoke_strata()

