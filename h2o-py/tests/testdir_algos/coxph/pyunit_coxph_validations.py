from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator
from h2o.exceptions import H2OResponseError


def coxph_validations():
    heart = h2o.import_file(pyunit_utils.locate("smalldata/coxph_test/heart.csv"))
    heart["age"] = heart["age"].ascharacter()
    # validate start/stop columns
    try:
        coxph = H2OCoxProportionalHazardsEstimator(
            start_column="XXX",
            stop_column="YYY"
        )
        coxph.train(x=["surgery", "transplant", "year"], y="event", training_frame=heart)
        assert False
    except H2OResponseError as ex:
        assert "start_column XXX not found in the training frame" in ex.args[0].exception_msg
        assert "stop_column YYY not found in the training frame" in ex.args[0].exception_msg
    # check any num/cat features present
    try:
        coxph = H2OCoxProportionalHazardsEstimator(
            start_column="start",
            stop_column="stop"
        )
        coxph.train(x=["age"], y="event", training_frame=heart)
        assert False
    except H2OResponseError as ex:
        assert "Training data must have at least 2 features" in ex.args[0].exception_msg
    # interactions column validation
    try:
        coxph = H2OCoxProportionalHazardsEstimator(
            start_column="start",
            stop_column="stop",
            interactions=["ZZZ"],
            interactions_only=["AAA"],
            interaction_pairs=[("BBB", "transplant")]
        )
        coxph.train(x=["transplant"], y="event", training_frame=heart)
        assert False
    except H2OResponseError as ex:
        assert "interactions: ZZZ not found in the training frame" in ex.args[0].exception_msg
        assert "interactions_only: AAA not found in the training frame" in ex.args[0].exception_msg
        assert "interaction_pairs: BBB not found in the training frame" in ex.args[0].exception_msg
    # happy path
    coxph = H2OCoxProportionalHazardsEstimator(
        start_column="start",
        stop_column="stop"
    )
    coxph.train(x=["surgery"], y="event", training_frame=heart)
    assert True

if __name__ == "__main__":
    pyunit_utils.standalone_test(coxph_validations)
else:
    coxph_validations()
