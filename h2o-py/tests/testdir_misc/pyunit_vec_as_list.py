import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def vec_as_list():



    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    res = h2o.as_list(iris[0], use_pandas=False)
    assert abs(float(res[0][4]) - 4.6) < 1e-10 and abs(float(res[0][6]) - 5.4) < 1e-10 and \
           abs(float(res[0][10]) - 4.9) < 1e-10, "incorrect values"

    res = 2 - iris
    res = h2o.as_list(res[0], use_pandas=False)
    assert abs(float(res[0][4]) - -2.6) < 1e-10 and abs(float(res[0][18]) - -3.1) < 1e-10 and \
           abs(float(res[0][25]) - -2.8) < 1e-10, "incorrect values"



if __name__ == "__main__":
    pyunit_utils.standalone_test(vec_as_list)
else:
    vec_as_list()
