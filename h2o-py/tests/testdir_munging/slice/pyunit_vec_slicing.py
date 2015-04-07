import sys
sys.path.insert(1, "../../../")
import h2o
from h2o.frame import H2OVec

def vec_slicing(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    iris.show()

    ###################################################################

    # H2OVec[int]
    res = 2 - iris
    res2 = h2o.as_list(H2OVec(name="C0", expr=res[0]))
    assert abs(res2[3][0] - -2.6) < 1e-10 and abs(res2[17][0] - -3.1) < 1e-10 and abs(res2[24][0] - -2.8) < 1e-10, \
        "incorrect values"

    # H2OVec[slice]
    res = iris[1][12:25]
    res3 = h2o.as_list(res)
    assert abs(res3[0][0] - 3.0) < 1e-10 and abs(res3[1][0] - 3.0) < 1e-10 and abs(res3[5][0] - 3.5) < 1e-10, \
        "incorrect values"

if __name__ == "__main__":
    h2o.run_test(sys.argv, vec_slicing)