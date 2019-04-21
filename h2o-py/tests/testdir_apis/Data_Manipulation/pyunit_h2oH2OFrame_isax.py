from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.frame import H2OFrame
from h2o.utils.typechecks import assert_is_type

def h2o_H2OFrame_isax():
    """
    Python API test: h2o.frame.H2OFrame.isax(num_words, max_cardinality, optimize_card=False)

    copied from pyunit_isax.py
    """
    df = h2o.create_frame(rows=1,cols=256,real_fraction=1.0,missing_fraction=0.0,seed=123)
    df2 = df.cumsum(axis=1)
    res = df2.isax(num_words=10,max_cardinality=10, optimize_card=False)
    res.show()
    answer = "0^10_0^10_0^10_0^10_5^10_7^10_8^10_9^10_9^10_8^10"

    assert_is_type(res, H2OFrame)       # check return type
    assert answer == res[0,0], "expected isax index to be " + answer + " but got" + res[0,0] + " instead."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_isax())
else:
    h2o_H2OFrame_isax()
