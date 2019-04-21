from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame
import numpy as np

def h2o_H2OFrame_cor():
    """
    Python API test: h2o.frame.H2OFrame.cor(y=None, na_rm=False, use=None)
    """
    python_lists = np.random.uniform(-1,1, (10,2))
    python_lists2 = 0.5*python_lists[:,0]-0.3*python_lists[:,1]
    python_list3 = python_lists[:,0]
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    h2oframe2 = h2o.H2OFrame(python_obj=python_lists2)
    h2oframe3 = h2o.H2OFrame(python_obj=python_list3)

    corframe = h2oframe.cor(h2oframe2, na_rm=True, use=None)
    corval = h2oframe2.cor(h2oframe3, na_rm=False, use=None)

    assert_is_type(corframe, H2OFrame)
    assert corframe.shape==(2,1), "h2o.H2OFrame.cor() command is not working."
    assert_is_type(corval, float)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_cor())
else:
    h2o_H2OFrame_cor()
