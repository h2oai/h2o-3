from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_isin():
    """
    Python API test: h2o.frame.H2OFrame.isin(item)

    Copied from pyunit_isin.py
    """
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))

    assert_is_type(cars.isin("AMC Gremlin"), H2OFrame)   # check return type
    assert (cars[0].isin("AMC Gremlin") == (cars[0] == "AMC Gremlin")).all()
    assert (cars[2].isin(6) == (cars[2] == 6)).all()
    assert not (cars.isin(["AMC Gremlin","AMC Concord DL"]) == cars.isin("AMC Gremlin")).all()
    assert (cars.isin(["AMC Gremlin","AMC Concord DL",6]) == cars.isin("AMC Gremlin") + cars.isin("AMC Concord DL")
        + cars.isin(6)).all()


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_isin())
else:
    h2o_H2OFrame_isin()
