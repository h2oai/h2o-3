from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

def h2o_H2OFrame_any_rm_na():
    """
    Python API test: h2o.frame.H2OFrame.any_na_rm()
    """
    python_lists = [['NA',1,'NA','NA'], ['NA','NA','NA','NA']]
    h2oframe = h2o.H2OFrame(python_obj=python_lists, na_strings=['NA'])
    assert h2oframe.any_na_rm(), "h2o.H2OFrame.any_rm_na() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_any_rm_na())
else:
    h2o_H2OFrame_any_rm_na()
