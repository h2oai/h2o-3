import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2o_H2OFrame_structure():
    """
    Python API test: h2o.frame.H2OFrame.structure()
    """
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    frame.structure()


pyunit_utils.standalone_test(h2o_H2OFrame_structure)
