import sys
sys.path.insert(1, "../../../")
import h2o

def binop_pow(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    rows, cols = iris.dim()
    iris.show()

    ###################################################################

    # LHS: scaler, RHS: H2OFrame
    res = 2 ** iris
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == cols, "dimension mismatch"
    #for x, y in zip([sum([res[r,c] for r in range(rows)]) for c in range(cols-1)], [10211.96, 1304.952, 3676.173, 393.1384]):
    #    assert abs(x.eager() - y) < 1e-2,  "expected same values"

    # LHS: scaler, RHS: H2OVec
    res = 2 ** iris[1]
    assert abs(sum([res[i] for i in range(rows)]).eager() - 1304.952) < 1e-2, "expected same values"

    # LHS: scaler, RHS: Expr
    res = 2 ** iris[0]
    res2 = 1.1 ** res[133]
    assert abs(res2.eager() - 1825.845) < 1e-2, "expected same values"

    ###################################################################

    # LHS: Expr, RHS: H2OFrame
    try:
        res = 1.2 ** iris[2]
        res2 = res[133] ** iris
        res2.show()
        assert False, "expected error. objects with different dimensions not supported."
    except EnvironmentError:
        pass

    # LHS: Expr, RHS: H2OVec
    try:
        res = 1.2 ** iris[2]
        res2 = res[133] ** iris[1]
        res2.show()
        assert False, "expected error. objects with different dimensions not supported."
    except EnvironmentError:
        pass

    # LHS: Expr, RHS: Expr
    res = 1.1 ** iris[2]
    res2 = res[133] ** res[10]
    assert abs(res2.eager() - 1.752052) < 1e-5, "expected same values"

    # LHS: Expr, RHS: scaler
    res = 2 ** iris[0]
    res2 = res[133] ** 3
    assert int(res2.eager()) - 489178 == 0, "expected same values"

    ###################################################################

    # LHS: H2OVec, RHS: H2OFrame
    try:
        res = iris[2] ** iris
        res.show()
        assert False, "expected error. objects with different dimensions not supported."
    except EnvironmentError:
        pass

    # LHS: H2OVec, RHS: H2OVec
    res = iris[0] ** iris[1]
    assert abs(sum([res[i] for i in range(rows)]).eager() - 45817.88) < 1e-2, "expected same values"

    res = iris[2] ** iris[1]
    assert abs(sum([res[i] for i in range(rows)]).eager() - 14704.66) < 1e-2, "expected same values"

    res = iris[0] ** iris[1] * iris[2] ** iris[3]
    assert (int(sum([res[i] for i in range(rows)]).eager()) - 1018599) < 1e-2, "expected same values"

    # LHS: H2OVec, RHS: Expr
    try:
        res = 1.2 ** iris[2]
        res2 = iris[1] ** res[133]
        res2.show()
        assert False, "expected error. objects with different dimensions not supported."
    except EnvironmentError:
        pass

    # LHS: H2OVec, RHS: scaler
    res = iris[0] ** 2
    assert abs(sum([res[i] for i in range(rows)]).eager() - 5223.85) < 1e-2, "expected different column sum"

    ###################################################################

    # LHS: H2OFrame, RHS: H2OFrame
    res = iris ** iris
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == cols, "dimension mismatch"

    res = iris[0:2] ** iris[1:3]
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == 2, "dimension mismatch"

    try:
        res = iris ** iris[0:3]
        res.show()
        assert False, "expected error. frames are different dimensions."
    except EnvironmentError:
        pass

    # LHS: H2OFrame, RHS: H2OVec
    try:
        res = iris ** iris[0]
        res.show()
        assert False, "expected error. objects of different dimensions not supported."
    except EnvironmentError:
        pass

    # LHS: H2OFrame, RHS: Expr
    try:
        res = 1.2 ** iris[2]
        res2 = iris ** res[133]
        res2.show()
        assert False, "expected error. objects with different dimensions not supported."
    except EnvironmentError:
        pass

    # LHS: H2OFrame, RHS: scaler
    res = iris ** 2
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == cols, "dimension mismatch"
    #for x, y in zip([sum([res[r,c] for r in range(rows)]) for c in range(cols-1)], [5223.85, 1427.05, 2583, 302.3]):
    #    assert abs(x.eager() - y) < 1e-2,  "expected same values"

    ###################################################################

if __name__ == "__main__":
    h2o.run_test(sys.argv, binop_pow)