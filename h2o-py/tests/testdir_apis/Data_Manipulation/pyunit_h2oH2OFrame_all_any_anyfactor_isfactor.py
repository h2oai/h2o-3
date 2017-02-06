from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.utils.typechecks import assert_is_type
from tests import pyunit_utils

def h2o_H2OFrame_all():
    """
    Python API test: h2o.frame.H2OFrame.all(), h2o.frame.H2OFrame.any(), h2o.frame.H2OFrame.anyfactor(),
    h2o.frame.H2OFrame.isfactor()
    """
    python_lists=[[True, False], [False, True], [True, True], [True, 'NA']]
    h2oframe = h2o.H2OFrame(python_obj=python_lists, na_strings=['NA']) # contains true and false
    assert not(h2oframe.all()), "h2o.H2OFrame.all() command is not working." # all elements are true or NA
    assert h2oframe.any(), "h2o.H2OFrame.any() command is not working." # all elements are true or NA
    assert h2oframe.anyfactor(), "h2o.H2OFrame.anyfactor() command is not working." # all columns are factors

    clist = h2oframe.isfactor()
    assert_is_type(clist, list)     # check return type
    assert sum(clist)==h2oframe.ncol, "h2o.H2OFrame.isfactor() command is not working."  # check return result


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_all())
else:
    h2o_H2OFrame_all()
