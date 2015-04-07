import sys
sys.path.insert(1, "../../")
import h2o
from h2o.expr import Expr

def expr_as_list(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    rows, cols = iris.dim()
    print "iris:"
    iris.show()

    ###################################################################

    # expr[int], expr is pending
    res = 2 - iris
    res2 = h2o.as_list(res[0])
    assert abs(res2[3][0] - -2.6) < 1e-10 and abs(res[17][0] - -3.1) < 1e-10 and abs(res[24][0] - -2.8) < 1e-10, \
        "incorrect values"

    # expr[int], expr is remote
    res.eager()
    res3 = h2o.as_list(res[0])
    assert abs(res2[3][0] - -2.6) < 1e-10 and abs(res[17][0] - -3.1) < 1e-10 and abs(res[24][0] - -2.8) < 1e-10, \
        "incorrect values"

    # expr[int], expr is local
    expr = h2o.as_list(Expr([1,2,3]))
    res4 = expr[2]
    assert res4 == 3, "incorrect values"

    # expr[tuple], expr._data is pending
    res = 2 - iris
    res5 = h2o.as_list(res[5,2])
    assert abs(res5[0][0] - 0.3) < 1e-10, "incorrect values"

    # expr[tuple], expr._data is remote
    res.eager()
    res6 = h2o.as_list(res[5,2])
    assert abs(res6[0][0] - 0.3) < 1e-10, "incorrect values"

    # expr[tuple], expr._data is local
    expr = h2o.as_list(Expr([[1,2,3], [4,5,6]]))
    assert expr[1][1] == 5, "incorrect values"

if __name__ == "__main__":
    h2o.run_test(sys.argv, expr_as_list)