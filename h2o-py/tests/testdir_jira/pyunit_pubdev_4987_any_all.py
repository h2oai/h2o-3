from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

def h2o_H2OFrame_all():
    """
    Python API test: h2o.frame.H2OFrame.all(), h2o.frame.H2OFrame.any()
    """
    python_lists=[[True, False], [False, True], [True, True], [True, 'NA']]
    h2oframe = h2o.H2OFrame(python_obj=python_lists, na_strings=['NA']) # contains true and false
    assert not(h2oframe.all()), "h2o.H2OFrame.all() command is not working." # all elements are true or NA
    assert h2oframe.any(), "h2o.H2OFrame.any() command is not working." # all elements are true or NA
    h2o.remove(h2oframe)
    python_lists=[[True, True], [True, True], [True, True], [True, 'NA']]   # check with one boolean level only
    h2oframe = h2o.H2OFrame(python_obj=python_lists, na_strings=['NA']) # contains true and false
    assert h2oframe.all(), "h2o.H2OFrame.all() command is not working." # all elements are true or NA
    assert h2oframe.any(), "h2o.H2OFrame.any() command is not working." # all elements are true or NA
    h2o.remove(h2oframe)
    python_lists=[[False, False], [False, False], [False, False], [False, 'NA']] # check with one boolean level only
    h2oframe = h2o.H2OFrame(python_obj=python_lists, na_strings=['NA']) # contains true and false
    assert not(h2oframe.all()), "h2o.H2OFrame.all() command is not working." # all elements are false or NA
    assert h2oframe.any(), "h2o.H2OFrame.any() command is not working." # all elements are true or NA


pyunit_utils.standalone_test(h2o_H2OFrame_all)
