from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


import numpy as np

def distance_test():

    iris_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    iris_np = np.genfromtxt(pyunit_utils.locate("smalldata/iris/iris.csv"),
                            delimiter=',',
                            skip_header=1,
                            usecols=(0, 1, 2, 3))

    references = iris_h2o[10:150,0:4]
    queries    = iris_h2o[0:10,0:4]

    D = references.distance(queries, "l1")
    assert(D.min() >= 0)
    D = references.distance(queries, "l2")
    assert(D.min() >= 0)
    D = references.distance(queries, "cosine")
    assert(D.min() >= -1)
    assert(D.max() <= 1)
    D = references.distance(queries, "cosine_sq")
    assert(D.min() >= 0)
    assert(D.max() <= 1)

    A = references.distance(queries, "l2")
    B = references.distance(queries, "cosine")
    a = queries.distance(references, "l2")
    b = queries.distance(references, "cosine")

    assert((A.transpose() == a).all())
    assert((B.transpose() == b).all())

if __name__ == "__main__":
    pyunit_utils.standalone_test(distance_test)
else:
    distance_test()
