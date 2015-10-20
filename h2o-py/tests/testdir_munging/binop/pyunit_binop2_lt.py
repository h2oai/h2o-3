



def binop_lt():
    
    

    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    rows, cols = iris.dim
    iris.show()

    #frame/scaler
    res = iris < 5
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == cols, "dimension mismatch"
    new_rows = iris[res[0]].nrow
    assert new_rows == 22, "wrong number of rows returned"

    res = 5 > iris
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == cols, "dimension mismatch"
    new_rows = iris[res[0]].nrow
    assert new_rows == 22, "wrong number of rows returned"

    #frame/vec
    #try:
    #    res = iris < iris[0]
    #    res.show()
    #    assert False, "expected error. objects of different dimensions not supported."
    #except EnvironmentError:
    #    pass

    #try:
    #    res = iris[2] > iris
    #    res.show()
    #    assert False, "expected error. objects of different dimensions not supported."
    #except EnvironmentError:
    #    pass

    #vec/vec
    res = iris[0] < iris[1]
    res_rows = res.nrow
    assert res_rows == rows, "dimension mismatch"
    new_rows = iris[res].nrow
    assert new_rows == 0, "wrong number of rows returned"

    # frame/frame
    res = iris < iris
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == cols, "dimension mismatch"

    res = iris[0:2] < iris[1:3]
    res_rows, res_cols = res.dim
    assert res_rows == rows and res_cols == 2, "dimension mismatch"

    #try:
    #    res = iris < iris[0:3]
    #    res.show()
    #    assert False, "expected error. frames are different dimensions."
    #except EnvironmentError:
    #    pass


binop_lt()
