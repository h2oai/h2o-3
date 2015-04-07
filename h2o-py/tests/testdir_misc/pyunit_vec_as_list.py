import sys
sys.path.insert(1, "../../")
import h2o
from h2o.frame import H2OVec

def vec_as_list(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    rows, cols = iris.dim()
    print "iris:"
    iris.show()

    ###################################################################

    res = h2o.as_list(iris[0])
    assert abs(res[3][0] - 4.6) < 1e-10 and abs(res[5][0] - 5.4) < 1e-10 and abs(res[9][0] - 4.9) < 1e-10, \
        "incorrect values"

    res = 2 - iris
    res2 = h2o.as_list(H2OVec(name="C0", expr=res[0]))
    assert abs(res2[3][0] - -2.6) < 1e-10 and abs(res2[17][0] - -3.1) < 1e-10 and abs(res2[24][0] - -2.8) < 1e-10, \
        "incorrect values"

    res3 = h2o.as_list(H2OVec(name="C1", expr=res[1]))
    assert abs(res3[3][0] - -1.1) < 1e-10 and abs(res3[5][0] - -1.9) < 1e-10 and abs(res3[9][0] - -1.1) < 1e-10, \
        "incorrect values"

if __name__ == "__main__":
    h2o.run_test(sys.argv, vec_as_list)