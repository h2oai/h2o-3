import sys
sys.path.insert(1, "../../")
import h2o
from h2o.expr import Expr

def expr_as_list(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))

    # multiple rows and columns
    res = 2 - iris
    res = h2o.as_list(res)
    assert abs(res[3][0] - -2.6) < 1e-10 and abs(res[4][1] - -1.6) < 1e-10 and abs(res[10][2] - 0.5) < 1e-10, \
        "incorrect values"

    # single column
    res = 2 - iris
    res = h2o.as_list(res[0])
    assert abs(res[3][0] - -2.6) < 1e-10 and abs(res[17][0] - -3.1) < 1e-10 and abs(res[24][0] - -2.8) < 1e-10, \
        "incorrect values"

    # local data
    expr = h2o.as_list(Expr([1,2,3]))
    assert expr[2] == 3, "incorrect values"

    expr = h2o.as_list(Expr([[1,2,3], [4,5,6]]))
    assert expr[1][1] == 5, "incorrect values"

if __name__ == "__main__":
    h2o.run_test(sys.argv, expr_as_list)