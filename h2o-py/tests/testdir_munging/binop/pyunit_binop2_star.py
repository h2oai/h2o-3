import sys
sys.path.insert(1, "../../../")
import h2o

def binop_star(ip,port):
    
    

    iris = h2o.import_file(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
    rows, cols = iris.dim()
    iris.show()

    #frame/scaler
    res = iris * 99
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == cols, "dimension mismatch"
    for x, y in zip([res[c].sum() for c in range(cols-1)], [86773.5, 45351.9, 55816.2, 17800.2]):
        assert abs(x - y) < 1e-7,  "unexpected column sums."

    res = 5 * iris
    res_rows, res_cols = res.dim()
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
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == cols, "dimension mismatch"

    res = iris[0:2] * iris[1:3]
    res_rows, res_cols = res.dim()
    assert res_rows == rows and res_cols == 2, "dimension mismatch"

    #try:
    #    res = iris * iris[0:3]
    #    res.show()
    #    assert False, "expected error. frames are different dimensions."
    #except EnvironmentError:
    #    pass

if __name__ == "__main__":
    h2o.run_test(sys.argv, binop_star)
