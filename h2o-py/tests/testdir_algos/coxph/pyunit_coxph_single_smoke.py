import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator


def coxph_smoke_single_node():
    heart = h2o.import_file(pyunit_utils.locate("smalldata/coxph_test/heart.csv"))
    coxph = H2OCoxProportionalHazardsEstimator(start_column="start", stop_column="stop", single_node_mode=True)
    coxph.train(x="age", y="event", training_frame=heart)

    pred = coxph.predict(test_data=heart)
    assert len(pred) == len(heart)


if __name__ == "__main__":
    pyunit_utils.standalone_test(coxph_smoke_single_node)
else:
    coxph_smoke_single_node()
