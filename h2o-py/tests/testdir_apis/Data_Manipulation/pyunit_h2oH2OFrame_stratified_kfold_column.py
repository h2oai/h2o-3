from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_stratified_kfold_column():
    """
    Python API test: h2o.frame.H2OFrame.stratified_kfold_column(n_folds=3, seed=-1)
    """
    python_lists = np.random.randint(-3,3, (10000,2))
    h2oframe = h2o.H2OFrame(python_obj=python_lists).asfactor()
    newframe = h2oframe[1].stratified_kfold_column(n_folds=3, seed=-1)
    assert_is_type(newframe, H2OFrame)
    assert ((newframe==0).sum()+(newframe==1).sum()+(newframe==2).sum())==h2oframe.nrow, \
        "h2o.H2OFrame.stratified_kfold_column() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_stratified_kfold_column())
else:
    h2o_H2OFrame_stratified_kfold_column()
