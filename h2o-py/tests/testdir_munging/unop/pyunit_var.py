import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
##
# Test out the var() functionality
# If NAs in the frame, they are skipped in calculation unless na.rm = F
# If any categorical columns, throw an error
##




import numpy as np

def var_test():
    
    

    iris_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    iris_np = np.genfromtxt(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"),
                            delimiter=',',
                            skip_header=1,
                            usecols=(0, 1, 2, 3))

    var_np = np.var(iris_np, axis=0, ddof=1)
    for i in range(4):
        var_h2o = iris_h2o[i].var()
        assert abs(var_np[i] - var_h2o) < 1e-10, "expected equal variances"


    var_cov_h2o = iris_h2o[0:4].var()
    var_cov_np = np.cov(iris_np, rowvar=0, ddof=1)




if __name__ == "__main__":
    pyunit_utils.standalone_test(var_test)
else:
    var_test()
