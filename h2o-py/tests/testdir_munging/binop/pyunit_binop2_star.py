



def binop_star():
    
    

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    rows, cols = iris.dim
    iris.show()

    #frame/scaler
    res = iris * 99
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == cols, "dimension mismatch"
    for x, y in zip([res[c].sum() for c in range(cols-1)], [86773.5, 45351.9, 55816.2, 17800.2]):
        assert abs(x - y) < 1e-7,  "unexpected column sums."

    res = 5 * iris
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == cols, "dimension mismatch"

    #frame/vec
    #try:
    #    res = iris * iris[0]
    #    res.show()
    #    assert False, "expected error. objects of different dimensions not supported."
    #except EnvironmentError:
    #    pass

    #try:
    #    res = iris[2] * iris
    #    res.show()
    #    assert False, "expected error. objects of different dimensions not supported."
    #except EnvironmentError:
    #    pass

    #vec/vec
    res = iris[0] * iris[1]
    res.show()
    assert abs(res.sum() - 2670.98) < 1e-2, "expected different column sum"

    res = iris[0] * iris[1] * iris[2] * iris[3]
    res.show()
    assert abs(res.sum() - 16560.42) < 1e-2, "expected different sum"

    # frame/frame
    res = iris * iris
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == cols, "dimension mismatch"

    res = iris[0:2] * iris[1:3]
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == 2, "dimension mismatch"

    #try:
    #    res = iris * iris[0:3]
    #    res.show()
    #    assert False, "expected error. frames are different dimensions."
    #except EnvironmentError:
    #    pass


binop_star()
