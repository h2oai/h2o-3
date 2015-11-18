import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def deeplearning_basic():
    

    iris_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    hh = h2o.deeplearning(x=iris_hex[:3],
                          y=iris_hex[4],
                          loss='CrossEntropy')
    hh.show()


if __name__ == "__main__":
    pyunit_utils.standalone_test(deeplearning_basic)
else:
    deeplearning_basic()
