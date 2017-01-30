from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.utils.typechecks import assert_is_type
from pandas import DataFrame

def h2oremove_all():
    """
    Python API test: h2o.remove_all()
    """
    # call with no arguments
    try:
        h2o.remove_all()    # remove all object first
        training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
        Y = 3
        X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]

        model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, Lambda=1e-5)
        model.train(x=X, y=Y, training_frame=training_data)
        lsObject = h2o.ls()
        assert_is_type(lsObject, DataFrame)
        assert len(lsObject.values) == 3, "h2o.ls() command is not working."
        h2o.remove_all()
        lsObject2 = h2o.ls()
        assert len(lsObject2.values) == 0, "h2o.remove_all() command is not working."
    except Exception as e:
        assert False, "h2o.remove_all() command is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oremove_all)
else:
    h2oremove_all()
