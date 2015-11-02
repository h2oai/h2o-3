import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import numpy as np


def minmax_basic():
    print "Uploading iris data..."
    iris_h2o = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    iris_np = np.genfromtxt(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"), delimiter=",", skip_header=1)

    print "Computing min & max of the first column of iris..."
    iris1_min = iris_h2o[0].min()
    print "Minimum: {0}".format(iris1_min)
    iris1_max = iris_h2o[0].max()
    print "Maximum: {0}".format(iris1_max)
    np_min = iris_np[:,0].min()
    np_max = iris_np[:,0].max()
    assert iris1_min == np_min, "Expected the same min value. H2O got {0}, but numpy got {1}".format(iris1_min, np_min)
    assert iris1_max == np_max, "Expected the same max value. H2O got {0}, but numpy got {1}".format(iris1_max, np_max)

    print "Computing min & max of all numeric columns of iris..."
    irisall_min = iris_h2o[0:4].min()
    print "Minimum: {0}".format(irisall_min)
    irisall_max = iris_h2o[0:4].max()
    print "Maximum: {0}".format(irisall_max)
    np_min = iris_np[:,0:4].min()
    np_max = iris_np[:,0:4].max()
    assert irisall_min == np_min, "Expected the same min value. H2O got {0}, but numpy got {1}".format(irisall_min, np_min)
    assert irisall_max == np_max, "Expected the same max value. H2O got {0}, but numpy got {1}".format(irisall_max, np_max)

    print "min and max correctness"
    data = [1,-0.1,0]
    mn = min(data)
    mx = max(data)
    h2o_min = h2o.H2OFrame(data).min()
    h2o_max = h2o.H2OFrame(data).max()
    assert h2o_min == mn, "Expected the same min value. H2O got {0}, but python got {1}".format(h2o_min, mn)
    assert h2o_max == mx, "Expected the same max value. H2O got {0}, but python got {1}".format(h2o_max, mx)



if __name__ == "__main__":
    pyunit_utils.standalone_test(minmax_basic)
else:
    minmax_basic()
