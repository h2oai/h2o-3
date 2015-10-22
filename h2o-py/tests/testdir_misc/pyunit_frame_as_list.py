import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def frame_as_list():



    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv.zip"))
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k.zip"))

    res1 = h2o.as_list(iris, use_pandas=False)
    assert abs(float(res1[0][9]) - 4.4) < 1e-10 and abs(float(res1[1][9]) - 2.9) < 1e-10 and \
           abs(float(res1[2][9]) - 1.4) < 1e-10, "incorrect values"

    res2 = h2o.as_list(prostate, use_pandas=False)
    assert abs(float(res2[0][7]) - 7) < 1e-10 and abs(float(res2[1][7]) - 0) < 1e-10 and \
           abs(float(res2[2][7]) - 68) < 1e-10, "incorrect values"

    res3 = h2o.as_list(airlines, use_pandas=False)
    assert abs(float(res3[0][4]) - 1987) < 1e-10 and abs(float(res3[1][4]) - 10) < 1e-10 and \
           abs(float(res3[2][4]) - 18) < 1e-10, "incorrect values"



if __name__ == "__main__":
    pyunit_utils.standalone_test(frame_as_list)
else:
    frame_as_list()
