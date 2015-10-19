

import h2o, tests

def binop_minus():
    
    

    iris = h2o.import_file(path=tests.locate("smalldata/iris/iris_wheader_65_rows.csv"))
    rows, cols = iris.dim

    ###################################################################

    # LHS: scaler, RHS: H2OFrame
    res = 2 - iris
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == cols, "dimension mismatch"
    for x, y in zip([sum([res[r,c] for r in range(rows)]) for c in range(cols-1)], [-209.9, -82.6, -6.9, 97.8]):
        assert abs((x - y)) < 1e-1,  "expected same values"

    # LHS: scaler, RHS: scaler
    res = 2 - iris[0]
    res2 = 1.1 - res[21,:]
    assert abs(res2 - 4.2) < 1e-1, "expected same values"

    ###################################################################

    # LHS: 1x1 H2OFrame, RHS: H2OFrame
    try:
      res = 1.2 - iris[2]
      res2 = res[21,:] - iris
      print res2.dim
      assert False, " Expected Frame dimension mismatch error"
    except Exception:
      pass

    # LHS: 1x1, RHS: nx1 column
    try:
      res = 1.2 - iris[2]
      res2 = res[21,:] - iris[1]
      res2.show()
      assert False, "Expected Frame dimension mismatch error"
    except Exception:
      pass

    # LHS: scaler, RHS: scaler
    res = 1.1 - iris[2]
    res2 = res[21,:] - res[10,:]
    assert abs(res2 - 0) < 1e-1, "expected same values"

    # LHS: scaler, RHS: scaler
    res = 2 - iris[0]
    res2 = res[21,:] - 3
    assert abs(res2 - -6.1) < 1e-1, "expected same values"

    ###################################################################

    # LHS: H2OVec, RHS: H2OFrame
    #try:
    #    res = iris[2] - iris
    #    res.show()
    #    assert False, "expected error. objects with different dimensions not supported."
    #except EnvironmentError:
    #    pass

    # LHS: nx1, RHS: 1x1
    try:
      res = 1.2 - iris[2]
      res2 = iris[1] - res[21,:]
      res2.show()
      assert False, "Expected Frame dimension mismatch error"
    except Exception:
      pass

    ###################################################################

    # LHS: H2OFrame, RHS: H2OFrame
    res = iris - iris
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == cols, "dimension mismatch"

    res = iris[0:2] - iris[1:3]
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == 2, "dimension mismatch"

    #try:
    #    res = iris - iris[0:3]
    #    res.show()
    #    assert False, "expected error. frames are different dimensions."
    #except EnvironmentError:
    #    pass

    # LHS: H2OFrame, RHS: H2OVec
    #try:
    #    res = iris - iris[0]
    #    res.show()
    #    assert False, "expected error. objects of different dimensions not supported."
    #except EnvironmentError:
    #    pass

    # LHS: H2OFrame, RHS: 1x1
    try:
      res = 1.2 - iris[2]
      res2 = iris - res[21,:]
      res2.show()
      assert False, "Expected Frame dimension mismatch error"
    except Exception:
      pass

    # LHS: H2OFrame, RHS: scaler
    res = iris - 2
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == cols, "dimension mismatch"
    for x, y in zip([sum([res[r,c] for r in range(rows)]) for c in range(cols-1)], [209.9, 82.6, 6.9, -97.8]):
        assert abs(x - y) < 1e-1,  "expected same values"

###################################################################


pyunit_test = binop_minus
