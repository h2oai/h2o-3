from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def isax():
    df = h2o.create_frame(rows=1,cols=256,real_fraction=1.0,missing_fraction=0.0,seed=123)
    df2 = df.cumsum(axis=1)
    res = df2.isax(num_words=10,max_cardinality=10)
    res.show()
    answer = "0^10_0^10_0^10_0^10_5^10_7^10_8^10_9^10_9^10_8^10"
    assert answer == res[0,0], "expected isax index to be " + answer + " but got" + res[0,0] + " instead."
    h2o.remove(df)
    h2o.remove(df2)
    h2o.remove(res)

if __name__ == "__main__":
    pyunit_utils.standalone_test(isax)
else:
    isax()

