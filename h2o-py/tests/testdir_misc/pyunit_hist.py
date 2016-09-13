from future import standard_library
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

################################################################################
#
# Verifying that we can use hist() to create a histogram
#
################################################################################


def test_hist():
    df = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
    df.describe()

    h = df[0].hist(breaks=5,plot=False)
    assert h.nrow == 5

    h = df[0].hist(breaks=3,plot=False)
    assert h.nrow == 3

    h = df[0].hist(breaks=[0,0.5,2,3],plot=False)
    assert h.nrow == 4

    h = df[0].hist(breaks="Sturges",plot=False)
    assert h.nrow == 9

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_hist)
else:
    test_hist()
