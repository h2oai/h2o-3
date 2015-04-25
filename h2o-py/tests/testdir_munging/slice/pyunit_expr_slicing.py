import sys
sys.path.insert(1, "../../../")
import h2o
from h2o.expr import Expr

def expr_slicing(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    iris.show()

    ###################################################################

    # expr[int] (column slice), expr is pending
    res = 2 - iris
    res2 = res[0]
    assert abs(res2[3][0] - -2.6) < 1e-10 and abs(res2[17][0] - -3.1) < 1e-10 and abs(res2[24][0] - -2.8) < 1e-10, \
        "incorrect values"

    # expr[int,int], expr is remote
    res.eager()
    res3 = res[13, 3]
    assert abs(res3[0][0] - 1.9) < 1e-10, "incorrect values"

    # expr[int, slice], expr is remote
    res4 = res[12, 0:3]
    assert abs(res4[0][0] - -2.8) < 1e-10 and abs(res4[0][1] - -1.0) < 1e-10 and abs(res4[0][2] - 0.6) < 1e-10 and \
        abs(res4[0][3] - 1.9) < 1e-10, "incorrect values"

    # expr[slice, int], expr is remote
    res5 = res[5:8, 1]
    assert abs(res5[0][0] - -1.9) < 1e-10 and abs(res5[1][0] - -1.4) < 1e-10 and abs(res5[2][0] - -1.4) < 1e-10 and \
           abs(res5[3][0] - -0.9) < 1e-10, "incorrect values"

    # expr[slice, slice], expr is pending
    res = iris * 2
    res6 = res[5:8, 0:3]
    assert abs(res6[0][0] - 10.8) < 1e-10 and abs(res6[1][1] - 6.8) < 1e-10 and abs(res6[2][2] - 3.0) < 1e-10 and \
           abs(res6[3][3] - 0.4) < 1e-10, "incorrect values"

if __name__ == "__main__":
    h2o.run_test(sys.argv, expr_slicing)