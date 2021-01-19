from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.utils.typechecks import assert_is_type
import numpy as np
import scipy.stats
from tests import pyunit_utils


def h2o_H2OFrame_kurtosis():
    """
    Python API test: h2o.frame.H2OFrame.kurtosis(na_rm=False)
    """
    python_lists = np.random.normal(0,1, (10000, 1))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    clist = h2oframe.kurtosis(na_rm=True)

    assert_is_type(clist, list)     # check return type
    clist_kurtosis = clist[0]
    scipy_kurtosis = scipy.stats.kurtosis(python_lists)[0]
    assert abs(clist_kurtosis - 3.0 - scipy_kurtosis) < 1e-10, "h2o.H2OFrame.kurtosis() command is not working, wrong result. CList kurtosis: %s, scipy kurtosis: %s" % (clist_kurtosis, scipy_kurtosis)  # check return result


pyunit_utils.standalone_test(h2o_H2OFrame_kurtosis)
