from __future__ import print_function
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator
from h2o.two_dim_table import H2OTwoDimTable


def coxph_summary():
    heart = h2o.import_file(pyunit_utils.locate("smalldata/coxph_test/heart.csv"))

    coxph = H2OCoxProportionalHazardsEstimator(start_column="start", stop_column="stop")
    coxph.train(x="age", y="event", training_frame=heart)
    coxph.show()

    summary = coxph.summary()
    print(summary)
    assert isinstance(summary, H2OTwoDimTable)


if __name__ == "__main__":
    pyunit_utils.standalone_test(coxph_summary)
else:
    coxph_summary()
