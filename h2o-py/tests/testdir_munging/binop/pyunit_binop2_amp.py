import sys
sys.path.insert(1, "../../../")
import h2o

def binop_amp(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris_wheader_65_rows.csv"))
    rows, cols = iris.dim()

    ###################################################################

    # LHS: scaler, RHS: H2OFrame
    amp_res = 5 & iris
    amp_rows, amp_cols = amp_res.dim()
    assert amp_rows == rows and amp_cols == cols, "dimension mismatch"

    # LHS: scaler, RHS: H2OVec
    amp_res = 1 & iris[1]
    amp_rows = amp_res.nrow()
    assert amp_rows == rows, "dimension mismatch"
    new_rows = iris[amp_res].nrow()
    assert new_rows == rows, "wrong number of rows returned"

    ###################################################################

    # LHS: scaler, RHS: H2OFrame
    res = 1.2 + iris[2]
    res2 = res[11,:] & iris
    res2.show()

    # LHS: scaler, RHS: H2OVec
    res = 1.2 + iris[2]
    res2 = res[43,:] & iris[1]
    res2.show()

    ###################################################################

    # LHS: H2OVec, RHS: H2OFrame
    #try:
    #    res = iris[2] & iris
    #    res.show()
    #    assert False, "expected error. objects with different dimensions not supported."
    #except EnvironmentError:
    #    pass

    # LHS: H2OVec, RHS: H2OVec
    res = iris[0] & iris[1]
    assert res.sum() == 65.0, "expected all True"

    res = iris[2] & iris[1]
    assert res.sum() == 65.0, "expected all True"

    # LHS: H2OVec, RHS: H2OVec
    res = 1.2 + iris[2]
    res2 = iris[1,:] & res[7,:]
    res2.show()

    # LHS: H2OVec, RHS: scaler
    res = iris[0] & 0
    assert res.sum() == 0.0, "expected all False"

    ###################################################################

    # LHS: H2OFrame, RHS: H2OFrame
    res = iris & iris
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == cols, "dimension mismatch"

    res = iris[0:2] & iris[1:3]
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == 2, "dimension mismatch"

    #try:
    #    res = iris & iris[0:3]
    #    res.show()
    #    assert False, "expected error. frames are different dimensions."
    #except EnvironmentError:
    #    pass

    # LHS: H2OFrame, RHS: H2OVec
    #try:
    #    res = iris & iris[0]
    #    res.show()
    #    assert False, "expected error. objects of different dimensions not supported."
    #except EnvironmentError:
    #    pass

    # LHS: H2OFrame, RHS: scaler
    res = 1.2 + iris[2]
    res2 = iris & res[55,:]
    res2.show()

    # LHS: H2OFrame, RHS: scaler
    res = iris & 0
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == cols, "dimension mismatch"
    for c in range(cols-1):
        for r in range(rows):
            assert res[r,c] == 0.0,  "expected False"

    ###################################################################

if __name__ == "__main__":
  h2o.run_test(sys.argv, binop_amp)
