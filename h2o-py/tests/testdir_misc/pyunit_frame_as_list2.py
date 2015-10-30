import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils





def expr_as_list():



    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    # multiple rows and columns
    res = 2 - iris
    res = h2o.as_list(res, use_pandas=False)
    assert abs(float(res[0][4]) - -2.6) < 1e-10 and abs(float(res[1][5]) - -1.6) < 1e-10 and \
           abs(float(res[2][11]) - 0.5) < 1e-10, "incorrect values"

    # single column
    res = 2 - iris
    res = h2o.as_list(res[0], use_pandas=False)
    assert abs(float(res[0][4]) - -2.6) < 1e-10 and abs(float(res[0][18]) - -3.1) < 1e-10 and \
           abs(float(res[0][25]) - -2.8) < 1e-10, "incorrect values"

    # local data
    frm = h2o.as_list(h2o.H2OFrame(zip(*[[1,2,3]])), use_pandas=False)
    assert float(frm[2][1]) == 3, "incorrect values"

    frm = h2o.as_list(h2o.H2OFrame(zip(*[[1,2,3], [4,5,6]])), use_pandas=False)
    assert float(frm[1][2]) == 5, "incorrect values"



if __name__ == "__main__":
    pyunit_utils.standalone_test(expr_as_list)
else:
    expr_as_list()
