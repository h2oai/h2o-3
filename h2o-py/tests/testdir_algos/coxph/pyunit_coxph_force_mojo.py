from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator
from h2o.exceptions import H2OValueError


def coxph_force_mojo():
    heart = h2o.import_file(pyunit_utils.locate("smalldata/coxph_test/heart.csv"))
    coxph = H2OCoxProportionalHazardsEstimator(
        start_column="start",
        stop_column="stop",
        interactions=["age", "year"]
    )
    coxph.train(x=["age", "year"], y="event", training_frame=heart)

    # MOJO will not be enabled if interactions were used
    try:
        coxph.download_mojo()
        assert False, "Expected an error to be thrown"
    except H2OValueError as ex:
        assert "Export to MOJO not supported" == str(ex.args[0])
 
    # Show that just force-enabling MOJO won't help without model retraining
    try:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.coxph.mojo.forceEnable", "true"))
        coxph.download_mojo()
        assert False, "Expected an error to be thrown"
    except H2OValueError as ex:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.coxph.mojo.forceEnable", "false"))
        assert "Export to MOJO not supported" == str(ex.args[0])

    # The happy path: train model with mojo force-enabled and successfully download the MOJO
    try:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.coxph.mojo.forceEnable", "true"))
        coxph2 = H2OCoxProportionalHazardsEstimator(
            start_column="start",
            stop_column="stop",
            interactions=["age", "year"]
        )
        coxph2.train(x=["age", "year"], y="event", training_frame=heart)
        mojo = coxph2.download_mojo()
        assert mojo is not None
    finally:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.coxph.mojo.forceEnable", "false"))


if __name__ == "__main__":
    pyunit_utils.standalone_test(coxph_force_mojo)
else:
    coxph_force_mojo()
