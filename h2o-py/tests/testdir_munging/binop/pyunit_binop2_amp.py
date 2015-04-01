import sys
sys.path.insert(1, "../../../")
import h2o

def binop_amp(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    rows, cols = iris.dim()
    iris.show()

    ###################################################################

    # LHS: scaler, RHS: H2OFrame
    amp_res = 5 & iris
    amp_rows, amp_cols = amp_res.dim()
    assert amp_rows == rows and amp_cols == cols, "dimension mismatch"

    # LHS: scaler, RHS: H2OVec
    amp_res = 1 & iris[1]
    amp_rows = len(amp_res)
    assert amp_rows == rows, "dimension mismatch"
    new_rows = iris[amp_res].nrow()
    assert new_rows == rows, "wrong number of rows returned"

    # LHS: scaler, RHS: Expr
    res = 2 + iris[0]
    res2 = 1.1 & res[133]
    assert res2.eager(), "expected True"

    ###################################################################

    # LHS: Expr, RHS: H2OFrame
    try:
        res = 1.2 + iris[2]
        res2 = res[133] & iris
        res2.show()
        assert False, "expected error. objects with different dimensions not supported."
    except EnvironmentError:
        pass

    # LHS: Expr, RHS: H2OVec
    try:
        res = 1.2 + iris[2]
        res2 = res[133] & iris[1]
        res2.show()
        assert False, "expected error. objects with different dimensions not supported."
    except EnvironmentError:
        pass

    # LHS: Expr, RHS: Expr
    res = 1.1 + iris[2]
    res2 = res[133] & res[10]
    assert res2.eager(), "expected True"

    # LHS: Expr, RHS: scaler
    res = 2 + iris[0]
    res2 = res[133] & 3
    assert res2.eager(), "expected True"

    ###################################################################

    # LHS: H2OVec, RHS: H2OFrame
    try:
        res = iris[2] & iris
        res.show()
        assert False, "expected error. objects with different dimensions not supported."
    except EnvironmentError:
        pass

    # LHS: H2OVec, RHS: H2OVec
    res = iris[0] & iris[1]
    assert res, "expected same values"

    res = iris[2] & iris[1]
    assert res, "expected same values"

    # LHS: H2OVec, RHS: Expr
    try:
        res = 1.2 + iris[2]
        res2 = iris[1] & res[133]
        res2.show()
        assert False, "expected error. objects with different dimensions not supported."
    except EnvironmentError:
        pass

    # LHS: H2OVec, RHS: scaler
    res = iris[0] & 0
    for r in range(rows):
        assert not res[r].eager(),  "expected False"

    ###################################################################

    # LHS: H2OFrame, RHS: H2OFrame
    res = iris & iris
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == cols, "dimension mismatch"

    res = iris[0:2] & iris[1:3]
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == 2, "dimension mismatch"

    try:
        res = iris & iris[0:3]
        res.show()
        assert False, "expected error. frames are different dimensions."
    except EnvironmentError:
        pass

    # LHS: H2OFrame, RHS: H2OVec
    try:
        res = iris & iris[0]
        res.show()
        assert False, "expected error. objects of different dimensions not supported."
    except EnvironmentError:
        pass

    # LHS: H2OFrame, RHS: Expr
    try:
        res = 1.2 + iris[2]
        res2 = iris & res[133]
        res2.show()
        assert False, "expected error. objects of different dimensions not supported."
    except EnvironmentError:
        pass

    # LHS: H2OFrame, RHS: scaler
    res = iris & 0
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == cols, "dimension mismatch"
    for c in range(cols-1):
        for r in range(rows):
            assert not res[r,c].eager(),  "expected False"

    ###################################################################


if __name__ == "__main__":
  h2o.run_test(sys.argv, binop_amp)

