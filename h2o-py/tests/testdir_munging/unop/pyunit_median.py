from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

# Test out the h2o.median() functionality

import numpy as np

def med():



    iris_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    iris_np = np.genfromtxt(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"),
                            delimiter=',',
                            skip_header=1,
                            usecols=(0, 1, 2, 3))

    med_np = np.median(iris_np, axis=0)
    med_h2o = iris_h2o.median()
    for i in range(4):
        assert abs(med_np[i] - med_h2o[i]) < 1e-10, "expected medians to be the same"

    print("Medians from Numpy: ")
    print(med_np)
    print("Medians from H2O: ")
    print(med_h2o)



if __name__ == "__main__":
    pyunit_utils.standalone_test(med)
else:
    med()