import sys
sys.path.insert(1, "../../../")
import h2o
import numpy as np

def minmax_basic(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    print "Uploading iris data..."
    iris_h2o = h2o.import_frame(h2o.locate("smalldata/iris/iris_wheader.csv"))
    iris_np = np.genfromtxt(h2o.locate("smalldata/iris/iris_wheader.csv"), delimiter=",", skip_header=1)

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

    print "min and max corretness"
    data = [1,-0.1,0]
    mn = min(data)
    mx = max(data)
    h2o_min = h2o.H2OFrame(data).min()
    h2o_max = h2o.H2OFrame(data).max()
    assert h2o_min == mn, "Expected the same min value. H2O got {0}, but python got {1}".format(h2o_min, mn)
    assert h2o_max == mx, "Expected the same max value. H2O got {0}, but python got {1}".format(h2o_max, mx)

if __name__ == "__main__":
    h2o.run_test(sys.argv, minmax_basic)
